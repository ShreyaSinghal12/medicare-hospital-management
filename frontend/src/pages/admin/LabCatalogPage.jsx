import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { FlaskConical, Pencil, Plus, Search } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import {
  adminCreateLabTest, adminListLabTests, adminSetLabTestActive, adminUpdateLabTest,
} from '../../services/labService.js';
import { formatCurrency } from '../../utils/format.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';

export function LabTestForm({ test, onSaved, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: {
      code: test?.code || '', name: test?.name || '', description: test?.description || '', sampleType: test?.sampleType || '',
      unit: test?.unit || '', referenceRange: test?.referenceRange || '', price: test?.price ?? '', turnaroundHours: test?.turnaroundHours ?? 24,
    },
  });

  async function onSubmit(v) {
    const payload = { ...v, code: v.code.trim().toUpperCase(), price: Number(v.price), turnaroundHours: Number(v.turnaroundHours) };
    try {
      const saved = test ? await adminUpdateLabTest(test.id, payload) : await adminCreateLabTest(payload);
      toast.success(`${saved.name} ${test ? 'updated' : 'added'}`);
      onSaved();
    } catch (err) {
      if (err.code === 'LAB_TEST_CODE_EXISTS') setError('code', { type: 'server', message: err.message });
      else if (err.code === 'LAB_TEST_NAME_EXISTS') setError('name', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Code" required error={errors.code?.message} {...register('code', {
        required: 'Required', pattern: { value: /^[A-Za-z0-9-]{2,20}$/, message: '2–20 letters, digits or hyphens' },
      })} />
      <TextField label="Name" required error={errors.name?.message} {...register('name', {
        required: 'Required', minLength: { value: 2, message: 'At least 2 characters' }, maxLength: { value: 150, message: 'Max 150' },
      })} />
      <div className="sm:col-span-2">
        <TextAreaField label="Description" rows={2} error={errors.description?.message}
          {...register('description', { maxLength: { value: 500, message: 'Max 500' } })} />
      </div>
      <TextField label="Sample type" placeholder="Blood, Urine …" error={errors.sampleType?.message}
        {...register('sampleType', { maxLength: { value: 50, message: 'Max 50' } })} />
      <TextField label="Unit" placeholder="mg/dL" error={errors.unit?.message}
        {...register('unit', { maxLength: { value: 30, message: 'Max 30' } })} />
      <TextField label="Reference range" placeholder="70–100" hint="Shown next to results for information only."
        error={errors.referenceRange?.message} {...register('referenceRange', { maxLength: { value: 100, message: 'Max 100' } })} />
      <TextField label="Price (₹)" type="number" step="0.01" min="0" required error={errors.price?.message} {...register('price', {
        required: 'Required', min: { value: 0, message: 'Cannot be negative' }, max: { value: 100000, message: 'Max 100000' },
      })} />
      <TextField label="Turnaround (hours)" type="number" min="1" required error={errors.turnaroundHours?.message} {...register('turnaroundHours', {
        required: 'Required', min: { value: 1, message: 'At least 1' }, max: { value: 720, message: 'Max 720' },
      })} />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>{test ? 'Save' : 'Add test'}</Button>
      </div>
    </form>
  );
}

/** Laboratory test catalog. Administrators manage tests; they never see orders or results. */
export default function LabCatalogPage() {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const { data, loading, error, reload } = useApi(() => adminListLabTests(search), [search]);
  const [editing, setEditing] = useState(null);
  const [toggling, setToggling] = useState(null);
  const [busy, setBusy] = useState(false);
  const rows = data || [];

  async function confirmToggle() {
    setBusy(true);
    try {
      await adminSetLabTestActive(toggling.id, !toggling.active);
      toast.success(`${toggling.name} ${toggling.active ? 'deactivated' : 'activated'}`);
      setToggling(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Lab test catalog" description="Tests doctors can order. Price changes apply to new orders only."
        actions={<Button onClick={() => setEditing({})}><Plus className="h-4 w-4" aria-hidden="true" /> Add test</Button>} />
      <Card>
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
          <TextField label="Search" placeholder="Name or code" value={input} className="pl-9" onChange={(e) => setInput(e.target.value)} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={FlaskConical} title="No tests found" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((t) => (
              <li key={t.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-slate-900">{t.name}</p>
                    <span className="font-mono text-xs text-slate-500">{t.code}</span>
                    <StatusBadge tone={t.active ? 'green' : 'slate'}>{t.active ? 'Active' : 'Inactive'}</StatusBadge>
                  </div>
                  <p className="text-sm text-slate-500">
                    {formatCurrency(t.price)} · {t.sampleType || 'No sample'} · {t.turnaroundHours} h
                    {t.referenceRange ? ` · ref. ${t.referenceRange}${t.unit ? ` ${t.unit}` : ''}` : ''}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" onClick={() => setEditing(t)} aria-label={`Edit ${t.name}`}>
                    <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
                  </Button>
                  <Button variant={t.active ? 'secondary' : 'primary'} size="sm" onClick={() => setToggling(t)}>
                    {t.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </Card>
      <Modal open={editing !== null} title={editing?.id ? `Edit ${editing.name}` : 'Add lab test'} size="lg" onClose={() => setEditing(null)}>
        <LabTestForm test={editing?.id ? editing : null} onSaved={() => { setEditing(null); reload(); }} onCancel={() => setEditing(null)} />
      </Modal>
      <ConfirmDialog open={!!toggling} tone={toggling?.active ? 'danger' : 'primary'}
        title={toggling ? `${toggling.active ? 'Deactivate' : 'Activate'} ${toggling.name}?` : ''}
        message={toggling?.active ? 'Doctors can no longer order this test. Existing orders are not affected.' : 'Doctors can order this test again.'}
        confirmLabel={toggling?.active ? 'Deactivate' : 'Activate'} loading={busy}
        onCancel={() => setToggling(null)} onConfirm={confirmToggle} />
    </>
  );
}
