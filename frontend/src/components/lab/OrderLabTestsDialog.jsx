import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { toast } from 'sonner';
import useApi from '../../hooks/useApi.js';
import { createLabOrder, getLabCatalog } from '../../services/labService.js';
import { formatCurrency } from '../../utils/format.js';
import Button from '../ui/Button.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import { SelectField, TextAreaField, TextField } from '../ui/FormField.jsx';
import Modal from '../ui/Modal.jsx';
import Spinner from '../ui/Spinner.jsx';

/** The doctor picks catalog tests for a consultation. Nothing is pre-selected or suggested. */
export default function OrderLabTestsDialog({ consultationId, open, onClose }) {
  const navigate = useNavigate();
  const catalog = useApi(() => (open ? getLabCatalog() : Promise.resolve(null)), [open]);
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState([]);
  const [priority, setPriority] = useState('ROUTINE');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const tests = (catalog.data || []).filter((t) => `${t.name} ${t.code}`.toLowerCase().includes(filter.trim().toLowerCase()));
  const total = (catalog.data || []).filter((t) => selected.includes(t.id)).reduce((sum, t) => sum + Number(t.price), 0);

  function close() {
    setSelected([]); setFilter(''); setNotes(''); setPriority('ROUTINE'); setError(null);
    onClose();
  }

  function toggle(id) {
    setError(null);
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }

  async function submit() {
    if (selected.length === 0) {
      setError('Select at least one test');
      return;
    }
    setBusy(true);
    try {
      const order = await createLabOrder({ consultationId, testIds: selected, priority, clinicalNotes: notes.trim() || null });
      toast.success(`Ordered ${order.orderNo}`);
      close();
      navigate(`/doctor/lab-orders/${order.id}`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} title="Order lab tests" description="Choose the tests to order for this visit." onClose={close} size="lg" busy={busy}>
      {catalog.loading && <Spinner />}
      {catalog.error && <ErrorState error={catalog.error} onRetry={catalog.reload} />}
      {catalog.data && (
        <div className="space-y-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-500" aria-hidden="true" />
            <TextField label="Filter tests" placeholder="Name or code" value={filter} className="pl-9" onChange={(e) => setFilter(e.target.value)} />
          </div>
          <fieldset>
            <legend className="sr-only">Tests</legend>
            <ul className="max-h-64 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
              {tests.length === 0 && <li className="p-3 text-sm text-slate-500">No tests match.</li>}
              {tests.map((t) => (
                <li key={t.id}>
                  <label className="flex cursor-pointer items-start gap-3 p-3 text-sm hover:bg-slate-50">
                    <input type="checkbox" checked={selected.includes(t.id)} onChange={() => toggle(t.id)}
                      className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-700" />
                    <span className="flex-1">
                      <span className="font-medium text-slate-900">{t.name}</span> <span className="text-slate-500">· {t.code}</span>
                      <span className="block text-xs text-slate-500">{t.sampleType || 'No sample'} · about {t.turnaroundHours} h</span>
                    </span>
                    <span className="text-slate-700">{formatCurrency(t.price)}</span>
                  </label>
                </li>
              ))}
            </ul>
          </fieldset>
          {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
          <div className="grid gap-4 sm:grid-cols-[12rem_1fr]">
            <SelectField label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}
              options={[{ value: 'ROUTINE', label: 'Routine' }, { value: 'URGENT', label: 'Urgent' }]} />
            <TextAreaField label="Clinical notes (optional)" rows={2} maxLength={1000} value={notes}
              hint="Not shown to the patient." onChange={(e) => setNotes(e.target.value)} />
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <p className="text-sm text-slate-600">{selected.length} test(s) · {formatCurrency(total)}</p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={close} disabled={busy}>Cancel</Button>
              <Button onClick={submit} loading={busy}>Place order</Button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
