import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, PackagePlus, Pencil } from 'lucide-react';
import { toast } from 'sonner';
import LedgerTable from '../../components/inventory/LedgerTable.jsx';
import MedicineForm from '../../components/inventory/MedicineForm.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import {
  adjustBatch, getMedicine, listTransactions, receiveStock, setMedicineActive, titleCase, writeOffBatch,
} from '../../services/inventoryService.js';
import { addDaysIso, formatDateShort, todayIsoLocal } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';

function ReceiveStockForm({ medicine, onDone, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { batchNumber: '', expiryDate: '', quantity: '', purchasePrice: '', supplier: '', reference: '' },
  });
  const minExpiry = addDaysIso(todayIsoLocal(), 1);

  async function onSubmit(v) {
    try {
      const d = await receiveStock(medicine.id, {
        ...v, quantity: Number(v.quantity), purchasePrice: v.purchasePrice === '' ? null : Number(v.purchasePrice),
      });
      toast.success(`Received ${v.quantity} ${medicine.unit}(s) in batch ${v.batchNumber}`);
      onDone(d);
    } catch (err) {
      if (err.code === 'BATCH_EXISTS') setError('batchNumber', { type: 'server', message: err.message });
      else if (err.code === 'BATCH_EXPIRED') setError('expiryDate', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Batch number" required error={errors.batchNumber?.message} {...register('batchNumber', {
        required: 'Required', pattern: { value: /^[A-Za-z0-9][A-Za-z0-9/._-]{0,49}$/, message: 'Letters, digits and / . _ - only' },
      })} />
      <TextField label="Expiry date" type="date" required min={minExpiry} error={errors.expiryDate?.message}
        {...register('expiryDate', { required: 'Required', validate: (x) => x >= minExpiry || 'Must be after today' })} />
      <TextField label={`Quantity (${medicine.unit}s)`} type="number" min="1" required error={errors.quantity?.message}
        {...register('quantity', { required: 'Required', min: { value: 1, message: 'At least 1' }, max: { value: 1000000, message: 'Too large' } })} />
      <TextField label="Purchase price per unit (₹)" type="number" step="0.01" min="0" error={errors.purchasePrice?.message}
        {...register('purchasePrice', { min: { value: 0, message: 'Cannot be negative' } })} />
      <TextField label="Supplier" error={errors.supplier?.message} {...register('supplier', { maxLength: { value: 150, message: 'Max 150' } })} />
      <TextField label="Invoice / reference" error={errors.reference?.message} {...register('reference', { maxLength: { value: 100, message: 'Max 100' } })} />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Receive stock</Button>
      </div>
    </form>
  );
}

function AdjustForm({ batch, unit, onDone, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { type: batch.expired ? 'ADJUSTMENT_OUT' : '', quantity: '', reason: '' },
  });

  async function onSubmit(v) {
    try {
      const d = await adjustBatch(batch.id, { ...v, quantity: Number(v.quantity) });
      toast.success('Stock adjusted');
      onDone(d);
    } catch (err) {
      if (err.code === 'INSUFFICIENT_STOCK') setError('quantity', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  const options = [
    ...(batch.expired ? [] : [{ value: 'ADJUSTMENT_IN', label: 'Add (count correction, found stock)' }]),
    { value: 'ADJUSTMENT_OUT', label: 'Remove (damaged, lost, count correction)' },
    { value: 'STOCK_OUT', label: 'Return to supplier' },
  ];
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      <p className="text-sm text-slate-600">Batch {batch.batchNumber}: {batch.quantityAvailable} {unit}(s) available.</p>
      <SelectField label="Adjustment" required placeholder="Select" options={options} error={errors.type?.message}
        {...register('type', { required: 'Required' })} />
      <TextField label="Quantity" type="number" min="1" required error={errors.quantity?.message}
        {...register('quantity', { required: 'Required', min: { value: 1, message: 'At least 1' } })} />
      <TextAreaField label="Reason" required rows={2} error={errors.reason?.message}
        {...register('reason', { required: 'Required', minLength: { value: 3, message: 'At least 3 characters' }, maxLength: { value: 500, message: 'Max 500' } })} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Save adjustment</Button>
      </div>
    </form>
  );
}

/** One medicine: stock summary, batches with actions, and its ledger. */
export default function MedicineDetailPage() {
  const { id } = useParams();
  const { data, loading, error, reload } = useApi(() => getMedicine(id), [id]);
  const [ledgerPage, setLedgerPage] = useState(0);
  const ledger = useApi(() => listTransactions({ medicineId: id, page: ledgerPage, size: 10 }), [id, ledgerPage]);
  const [modal, setModal] = useState(null); // 'edit' | 'receive' | {adjust: batch}
  const [writeOff, setWriteOff] = useState(null);
  const [toggle, setToggle] = useState(false);
  const [busy, setBusy] = useState(false);

  function refreshed() {
    setModal(null);
    reload();
    ledger.reload();
  }

  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (loading && !data) return <Spinner />;
  const m = data.medicine;

  async function doWriteOff() {
    setBusy(true);
    try {
      await writeOffBatch(writeOff.id);
      toast.success(`Batch ${writeOff.batchNumber} written off`);
      setWriteOff(null);
      refreshed();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function doToggle() {
    setBusy(true);
    try {
      await setMedicineActive(m.id, !m.active);
      toast.success(m.active ? 'Medicine deactivated' : 'Medicine activated');
      setToggle(false);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Link to="/admin/inventory" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Medicines
      </Link>
      <PageHeader title={`${m.name} ${m.strength}`}
        description={`${m.genericName ? `${m.genericName} · ` : ''}${titleCase(m.category)} · ${m.manufacturer} · ${formatCurrency(m.unitPrice)} per ${m.unit}`}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setModal('edit')}><Pencil className="h-4 w-4" aria-hidden="true" /> Edit</Button>
            <Button variant="secondary" onClick={() => setToggle(true)}>{m.active ? 'Deactivate' : 'Activate'}</Button>
            {m.active && <Button onClick={() => setModal('receive')}><PackagePlus className="h-4 w-4" aria-hidden="true" /> Receive stock</Button>}
          </div>
        )} />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {[
          ['Usable stock', `${m.availableQuantity} ${m.unit}(s)`, m.lowStock ? 'text-amber-700' : 'text-slate-900'],
          ['Reorder level', m.reorderLevel, 'text-slate-900'],
          ['Expired, not written off', m.expiredQuantity, m.expiredQuantity ? 'text-red-700' : 'text-slate-900'],
          ['Next expiry', m.nearestExpiry ? formatDateShort(m.nearestExpiry) : '—', 'text-slate-900'],
        ].map(([label, value, tone]) => (
          <div key={label} className="rounded-xl border border-slate-200 bg-white p-4">
            <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
            <p className={`mt-1 text-xl font-semibold ${tone}`}>{value}</p>
          </div>
        ))}
      </div>

      <Card title="Batches" subtitle={`Earliest expiry first. "Expiring" means within ${data.expiryWarningDays} days.`}>
        {data.batches.length === 0 ? <p className="text-sm text-slate-500">No stock received yet.</p> : (
          <div className="relative overflow-x-auto">
            <table className="w-full min-w-[40rem] text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="py-2 pr-3 font-semibold">Batch</th>
                  <th scope="col" className="py-2 pr-3 font-semibold">Expiry</th>
                  <th scope="col" className="py-2 pr-3 text-right font-semibold">Available</th>
                  <th scope="col" className="py-2 pr-3 text-right font-semibold">Received</th>
                  <th scope="col" className="py-2 pr-3 font-semibold">Supplier</th>
                  <th scope="col" className="py-2 font-semibold"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.batches.map((b) => (
                  <tr key={b.id}>
                    <td className="py-2 pr-3 font-mono text-xs text-slate-800">{b.batchNumber}</td>
                    <td className="py-2 pr-3">
                      <span className="mr-2">{formatDateShort(b.expiryDate)}</span>
                      {b.expired && <StatusBadge tone="red">Expired</StatusBadge>}
                      {b.expiringSoon && <StatusBadge tone="amber">Expiring</StatusBadge>}
                    </td>
                    <td className="py-2 pr-3 text-right font-semibold">{b.quantityAvailable}</td>
                    <td className="py-2 pr-3 text-right text-slate-500">{b.quantityReceived}</td>
                    <td className="py-2 pr-3 text-slate-600">{b.supplier || '—'}</td>
                    <td className="py-2 text-right">
                      <div className="flex justify-end gap-1">
                        <Button size="sm" variant="ghost" onClick={() => setModal({ adjust: b })} aria-label={`Adjust batch ${b.batchNumber}`}>Adjust</Button>
                        {b.expired && b.quantityAvailable > 0 && (
                          <Button size="sm" variant="ghost" onClick={() => setWriteOff(b)}>Write off</Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-6">
        <Card title="Stock history">
          {ledger.loading && !ledger.data && <Spinner />}
          {ledger.data && ledger.data.content.length === 0 && <p className="text-sm text-slate-500">No movements yet.</p>}
          {ledger.data && ledger.data.content.length > 0 && <LedgerTable rows={ledger.data.content} />}
          <div className="mt-3"><Pagination data={ledger.data} onPageChange={setLedgerPage} /></div>
        </Card>
      </div>

      <Modal open={modal === 'edit'} title={`Edit ${m.name}`} size="lg" onClose={() => setModal(null)}>
        <MedicineForm medicine={m} onCancel={() => setModal(null)} onSaved={refreshed} />
      </Modal>
      <Modal open={modal === 'receive'} title={`Receive stock: ${m.name} ${m.strength}`} size="lg" onClose={() => setModal(null)}>
        <ReceiveStockForm medicine={m} onCancel={() => setModal(null)} onDone={refreshed} />
      </Modal>
      <Modal open={!!modal?.adjust} title="Adjust stock" onClose={() => setModal(null)}>
        {modal?.adjust && <AdjustForm batch={modal.adjust} unit={m.unit} onCancel={() => setModal(null)} onDone={refreshed} />}
      </Modal>
      <ConfirmDialog open={!!writeOff} tone="danger" title={writeOff ? `Write off batch ${writeOff.batchNumber}?` : ''}
        message={writeOff ? `${writeOff.quantityAvailable} expired ${m.unit}(s) will be removed from stock and recorded in the ledger.` : ''}
        confirmLabel="Write off" loading={busy} onCancel={() => setWriteOff(null)} onConfirm={doWriteOff} />
      <ConfirmDialog open={toggle} tone={m.active ? 'danger' : 'primary'} title={m.active ? `Deactivate ${m.name}?` : `Activate ${m.name}?`}
        message={m.active ? 'It cannot receive stock or be dispensed. History is kept.' : 'It can receive stock and be dispensed again.'}
        confirmLabel={m.active ? 'Deactivate' : 'Activate'} loading={busy} onCancel={() => setToggle(false)} onConfirm={doToggle} />
    </>
  );
}
