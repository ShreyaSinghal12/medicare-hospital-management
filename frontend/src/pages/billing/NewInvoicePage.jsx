import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, X } from 'lucide-react';
import { toast } from 'sonner';
import PatientPicker from '../../components/reception/PatientPicker.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import { createInvoice, getBillableCharges, ITEM_TYPES, sumMoney } from '../../services/billingService.js';
import { formatDateShort } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

const key = (c) => `${c.type}:${c.referenceId}`;

/** Build an invoice from the patient's unbilled services. Prices come from the server; the total shown is a preview. */
export default function NewInvoicePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [patientId, setPatientId] = useState(params.get('patientId'));
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(new Set());
  const [others, setOthers] = useState([]);
  const [discount, setDiscount] = useState('');
  const [notes, setNotes] = useState('');
  const [formError, setFormError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function load(id) {
    setData(null);
    setError(null);
    try {
      const d = await getBillableCharges(id);
      setData(d);
      setSelected(new Set(d.charges.map(key)));
    } catch (err) {
      setError(err);
    }
  }
  useEffect(() => { if (patientId) load(patientId); }, [patientId]);

  const charges = data?.charges || [];
  const chosen = charges.filter((c) => selected.has(key(c)));
  const validOthers = others.filter((o) => o.description.trim() && Number(o.quantity) > 0 && Number(o.unitPrice) > 0);
  const subtotal = sumMoney([...chosen.map((c) => c.lineTotal), ...validOthers.map((o) => sumMoney([o.unitPrice]) * Number(o.quantity))]);
  const disc = Number(discount || 0);
  const taxRate = Number(data?.taxRatePercent || 0);
  const tax = Math.round((subtotal - disc) * taxRate) / 100;
  const total = sumMoney([subtotal, -disc, tax]);

  function toggle(c) {
    setFormError(null);
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(key(c))) n.delete(key(c)); else n.add(key(c));
      return n;
    });
  }

  async function submit() {
    setFormError(null);
    if (chosen.length === 0 && validOthers.length === 0) return setFormError('Select at least one charge or add an item');
    if (others.length !== validOthers.length) return setFormError('Complete or remove the extra items (description, quantity and price above 0)');
    if (disc < 0 || disc > subtotal) return setFormError('The discount must be between 0 and the subtotal');
    if (disc > 0 && notes.trim().length === 0) return setFormError('Give the reason for the discount in the notes');
    setBusy(true);
    try {
      const inv = await createInvoice({
        patientId: Number(patientId),
        charges: chosen.map((c) => ({ type: c.type, referenceId: c.referenceId })),
        otherItems: validOthers.map((o) => ({ description: o.description.trim(), quantity: Number(o.quantity), unitPrice: Number(o.unitPrice) })),
        discountAmount: disc || null,
        notes: notes.trim() || null,
      });
      toast.success(`Invoice ${inv.invoiceNo} created`);
      navigate(`/reception/billing/${inv.id}`);
    } catch (err) {
      setFormError(err.message);
      if (err.code === 'ALREADY_BILLED') load(patientId);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="New invoice" description="Bill the services a patient has received. Prices are taken from bookings, lab orders and dispensing records." />
      <div className="space-y-6">
        <Card title="1. Patient">
          {data ? (
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <p><strong>{data.patientName}</strong> · {data.patientCode}
                {Number(data.outstandingBalance) > 0 && <span className="ml-2 text-amber-800">· {formatCurrency(data.outstandingBalance)} outstanding on earlier invoices</span>}
              </p>
              <Button size="sm" variant="ghost" onClick={() => { setPatientId(null); setData(null); }}><X className="h-4 w-4" aria-hidden="true" /> Change</Button>
            </div>
          ) : patientId && !error ? <Spinner /> : <PatientPicker onPick={(p) => setPatientId(String(p.id))} />}
          {error && <ErrorState error={error} onRetry={() => load(patientId)} />}
        </Card>

        {data && (
          <>
            <Card title="2. Charges" subtitle="Services not yet billed. Untick anything the patient will pay for separately.">
              {charges.length === 0 ? <p className="text-sm text-slate-500">Nothing left to bill for this patient.</p> : (
                <ul className="divide-y divide-slate-100">
                  {charges.map((c) => (
                    <li key={key(c)}>
                      <label className="flex cursor-pointer items-start gap-3 py-2 text-sm">
                        <input type="checkbox" checked={selected.has(key(c))} onChange={() => toggle(c)} className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600" />
                        <span className="flex-1">
                          <span className="text-slate-900">{c.description}{c.quantity > 1 ? ` × ${c.quantity}` : ''}</span>
                          <span className="block text-xs text-slate-500">{ITEM_TYPES[c.type]} · {c.source} · {formatDateShort(c.serviceDate)}</span>
                        </span>
                        <span className="font-medium">{formatCurrency(c.lineTotal)}</span>
                      </label>
                    </li>
                  ))}
                </ul>
              )}
              <div className="mt-4 space-y-2">
                {others.map((o, i) => (
                  <div key={i} className="grid gap-2 sm:grid-cols-[1fr_6rem_8rem_auto] sm:items-end">
                    <TextField label="Extra item" placeholder="e.g. Dressing" value={o.description} maxLength={200}
                      onChange={(e) => setOthers((xs) => xs.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))} />
                    <TextField label="Qty" type="number" min="1" value={o.quantity}
                      onChange={(e) => setOthers((xs) => xs.map((x, j) => (j === i ? { ...x, quantity: e.target.value } : x)))} />
                    <TextField label="Price (₹)" type="number" min="0.01" step="0.01" value={o.unitPrice}
                      onChange={(e) => setOthers((xs) => xs.map((x, j) => (j === i ? { ...x, unitPrice: e.target.value } : x)))} />
                    <Button size="sm" variant="ghost" aria-label="Remove extra item" onClick={() => setOthers((xs) => xs.filter((_, j) => j !== i))}>
                      <X className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  </div>
                ))}
                <Button size="sm" variant="ghost" onClick={() => setOthers((xs) => [...xs, { description: '', quantity: '1', unitPrice: '' }])}>
                  <Plus className="h-4 w-4" aria-hidden="true" /> Add extra item
                </Button>
              </div>
            </Card>

            <Card title="3. Review">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-3">
                  <TextField label="Discount (₹)" type="number" min="0" step="0.01" value={discount} onChange={(e) => setDiscount(e.target.value)} />
                  <TextAreaField label="Notes" rows={2} maxLength={500} hint="Required when a discount is given." value={notes} onChange={(e) => setNotes(e.target.value)} />
                </div>
                <dl className="space-y-1 self-end text-sm">
                  <div className="flex justify-between"><dt className="text-slate-500">Subtotal</dt><dd>{formatCurrency(subtotal)}</dd></div>
                  {disc > 0 && <div className="flex justify-between"><dt className="text-slate-500">Discount</dt><dd>− {formatCurrency(disc)}</dd></div>}
                  <div className="flex justify-between"><dt className="text-slate-500">Tax ({taxRate}%)</dt><dd>{formatCurrency(tax)}</dd></div>
                  <div className="flex justify-between border-t border-slate-200 pt-1 text-base font-semibold"><dt>Total</dt><dd>{formatCurrency(total)}</dd></div>
                </dl>
              </div>
              {formError && <p role="alert" className="mt-3 text-sm text-red-600">{formError}</p>}
              <Button className="mt-4" onClick={submit} loading={busy}>Create invoice</Button>
            </Card>
          </>
        )}
      </div>
    </>
  );
}
