import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, Search, X } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import { dispense, lookupPrescriptionForDispensing } from '../../services/inventoryService.js';
import { formatDateShort } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

const EMPTY = { medicineId: '', batchId: '', quantity: '' };
const selectClass = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20';

/**
 * Dispense against a finalized prescription. Only medicines matching the prescribed name and strength are
 * offered, and only non-expired batches (earliest expiry first). The administrator chooses batch and quantity.
 */
export default function DispensePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [rxNo, setRxNo] = useState(params.get('rx') || '');
  const [rx, setRx] = useState(null);
  const [lookupError, setLookupError] = useState(null);
  const [looking, setLooking] = useState(false);
  const [alloc, setAlloc] = useState({});
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);

  async function lookup(e) {
    e?.preventDefault();
    if (!rxNo.trim()) return;
    setLooking(true);
    setLookupError(null);
    setRx(null);
    try {
      const v = await lookupPrescriptionForDispensing(rxNo.trim());
      setRx(v);
      setAlloc(Object.fromEntries(v.items.map((i) => [i.prescriptionItemId, i.fullyDispensed ? [] : [{ ...EMPTY }]])));
      setErrors({});
      setNotes('');
    } catch (err) {
      setLookupError(err);
    } finally {
      setLooking(false);
    }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (params.get('rx')) lookup(); }, []);

  function update(itemId, idx, patch) {
    setErrors((e) => ({ ...e, [itemId]: undefined, form: undefined }));
    setAlloc((a) => ({ ...a, [itemId]: a[itemId].map((x, i) => (i === idx ? { ...x, ...patch } : x)) }));
  }

  function batchesOf(line, medicineId) {
    return line.candidates.find((c) => String(c.medicineId) === String(medicineId))?.batches || [];
  }

  function validate() {
    const errs = {};
    const usedPerBatch = {};
    const available = {};
    const lines = [];
    rx.items.forEach((line) => {
      const rows = (alloc[line.prescriptionItemId] || []).filter((r) => r.medicineId || r.batchId || r.quantity);
      let sum = 0;
      for (const r of rows) {
        const q = Number(r.quantity);
        if (!r.batchId) { errs[line.prescriptionItemId] = 'Choose a medicine and batch'; return; }
        if (!Number.isInteger(q) || q < 1) { errs[line.prescriptionItemId] = 'Enter a whole quantity of at least 1'; return; }
        const b = batchesOf(line, r.medicineId).find((x) => String(x.batchId) === String(r.batchId));
        available[r.batchId] = b?.available ?? 0;
        usedPerBatch[r.batchId] = (usedPerBatch[r.batchId] || 0) + q;
        sum += q;
        lines.push({ prescriptionItemId: line.prescriptionItemId, batchId: Number(r.batchId), quantity: q });
      }
      const limit = line.remainingQuantity ?? 1000;
      if (sum > limit) errs[line.prescriptionItemId] = `At most ${limit} can still be dispensed`;
    });
    Object.entries(usedPerBatch).forEach(([batchId, used]) => {
      if (used > available[batchId]) errs.form = `Batch stock exceeded (only ${available[batchId]} available in one of the chosen batches)`;
    });
    if (lines.length === 0 && Object.keys(errs).length === 0) errs.form = 'Enter at least one medicine to dispense';
    setErrors(errs);
    return Object.keys(errs).length ? null : lines;
  }

  async function submit() {
    const lines = validate();
    if (!lines) return;
    setBusy(true);
    try {
      const rec = await dispense({ prescriptionId: rx.prescriptionId, notes: notes.trim() || null, lines });
      toast.success(`Dispensed: ${rec.dispensingNo}`);
      navigate(`/admin/dispensing/${rec.id}`);
    } catch (err) {
      setErrors({ form: err.message });
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Dispense medicines" description="Look up the prescription number printed on the patient's prescription." />
      <Card>
        <form onSubmit={lookup} className="flex flex-wrap items-end gap-3">
          <div className="w-full max-w-sm">
            <TextField label="Prescription number" placeholder="RX-20261111-000001" value={rxNo} onChange={(e) => setRxNo(e.target.value)} />
          </div>
          <Button type="submit" loading={looking}><Search className="h-4 w-4" aria-hidden="true" /> Look up</Button>
        </form>
        {lookupError && <div className="mt-4"><ErrorState error={lookupError} title="Prescription not found" /></div>}
      </Card>

      {rx && (
        <div className="mt-6 space-y-6">
          <Card title={`${rx.prescriptionNo}${rx.versionNumber > 1 ? ` (version ${rx.versionNumber})` : ''}`}
            subtitle={`${rx.patientName} · ${rx.patientCode} · ${rx.doctorName} · finalized ${formatDateTime(rx.finalizedAt)}`}>
            <div className="space-y-4">
              {rx.items.map((line) => (
                <fieldset key={line.prescriptionItemId} className="rounded-lg border border-slate-200 p-4">
                  <legend className="px-1 text-sm font-semibold text-slate-900">{line.medicineName}{line.strength ? ` ${line.strength}` : ''}</legend>
                  <p className="text-sm text-slate-600">
                    Prescribed: {line.prescribedQuantity ?? 'quantity not stated (dispense once)'} · dispensed so far: {line.dispensedQuantity}
                    {line.remainingQuantity != null && ` · remaining: ${line.remainingQuantity}`}
                  </p>
                  {line.fullyDispensed && <div className="mt-2"><StatusBadge tone="green">Fully dispensed</StatusBadge></div>}
                  {!line.fullyDispensed && line.candidates.length === 0 && (
                    <p className="mt-2 text-sm text-amber-800">No matching medicine in the catalog. Add it under Medicines first; a different medicine needs a revised prescription.</p>
                  )}
                  {!line.fullyDispensed && line.candidates.length > 0 && (
                    <div className="mt-3 space-y-2">
                      {(alloc[line.prescriptionItemId] || []).map((r, idx) => {
                        const batches = batchesOf(line, r.medicineId);
                        const key = `${line.prescriptionItemId}-${idx}`;
                        return (
                          <div key={key} className="grid gap-2 sm:grid-cols-[1fr_1fr_7rem_auto] sm:items-end">
                            <label className="text-sm">
                              <span className="mb-1 block font-medium text-slate-700">Medicine</span>
                              <select className={selectClass} value={r.medicineId} aria-label={`Medicine for ${line.medicineName}`}
                                onChange={(e) => update(line.prescriptionItemId, idx, { medicineId: e.target.value, batchId: '' })}>
                                <option value="">Select</option>
                                {line.candidates.map((c) => (
                                  <option key={c.medicineId} value={c.medicineId}>
                                    {c.name} {c.strength} ({c.batches.reduce((s, b) => s + b.available, 0)} {c.unit}s)
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="text-sm">
                              <span className="mb-1 block font-medium text-slate-700">Batch</span>
                              <select className={selectClass} value={r.batchId} disabled={!r.medicineId} aria-label={`Batch for ${line.medicineName}`}
                                onChange={(e) => update(line.prescriptionItemId, idx, { batchId: e.target.value })}>
                                <option value="">{r.medicineId && batches.length === 0 ? 'Out of stock' : 'Select'}</option>
                                {batches.map((b) => (
                                  <option key={b.batchId} value={b.batchId}>{b.batchNumber} · exp {formatDateShort(b.expiryDate)} · {b.available} left</option>
                                ))}
                              </select>
                            </label>
                            <label className="text-sm">
                              <span className="mb-1 block font-medium text-slate-700">Quantity</span>
                              <input type="number" min="1" className={selectClass} value={r.quantity} aria-label={`Quantity for ${line.medicineName}`}
                                onChange={(e) => update(line.prescriptionItemId, idx, { quantity: e.target.value })} />
                            </label>
                            {idx > 0 ? (
                              <Button size="sm" variant="ghost" aria-label="Remove batch row"
                                onClick={() => setAlloc((a) => ({ ...a, [line.prescriptionItemId]: a[line.prescriptionItemId].filter((_, i) => i !== idx) }))}>
                                <X className="h-4 w-4" aria-hidden="true" />
                              </Button>
                            ) : <span />}
                          </div>
                        );
                      })}
                      <Button size="sm" variant="ghost"
                        onClick={() => setAlloc((a) => ({ ...a, [line.prescriptionItemId]: [...a[line.prescriptionItemId], { ...EMPTY }] }))}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Another batch
                      </Button>
                      {errors[line.prescriptionItemId] && <p role="alert" className="text-sm text-red-600">{errors[line.prescriptionItemId]}</p>}
                    </div>
                  )}
                </fieldset>
              ))}
            </div>
            <div className="mt-4 max-w-xl">
              <TextAreaField label="Notes (optional)" rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            {errors.form && <p role="alert" className="mt-3 text-sm text-red-600">{errors.form}</p>}
            <div className="mt-4 flex gap-2">
              <Button onClick={submit} loading={busy} disabled={rx.items.every((i) => i.fullyDispensed)}>Record dispensing</Button>
              <Button variant="secondary" onClick={() => { setRx(null); setRxNo(''); }}>Clear</Button>
            </div>
          </Card>
          {rx.previousDispensings.length > 0 && (
            <Card title="Earlier dispensings for this prescription">
              <ul className="divide-y divide-slate-100 text-sm">
                {rx.previousDispensings.map((d) => (
                  <li key={d.id} className="flex flex-wrap justify-between gap-2 py-2">
                    <Link to={`/admin/dispensing/${d.id}`} className="font-medium text-brand-700 hover:underline">{d.dispensingNo}</Link>
                    <span className="text-slate-600">{d.totalQuantity} unit(s) · {d.dispensedBy} · {formatDateTime(d.dispensedAt)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}
    </>
  );
}
