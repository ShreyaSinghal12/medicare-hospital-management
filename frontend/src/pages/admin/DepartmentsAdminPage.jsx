import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Building2, Pencil, Plus } from 'lucide-react';
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
import {
  adminCreateDepartment, adminListDepartments, adminSetDepartmentActive, adminUpdateDepartment,
} from '../../services/adminService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';

function DepartmentForm({ department, onSaved, onCancel }) {
  const {
    register, handleSubmit, setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { name: department?.name || '', description: department?.description || '' } });

  async function onSubmit(values) {
    try {
      const saved = department ? await adminUpdateDepartment(department.id, values) : await adminCreateDepartment(values);
      toast.success(`Department "${saved.name}" ${department ? 'updated' : 'created'}`);
      onSaved();
    } catch (err) {
      if (err.code === 'DEPARTMENT_EXISTS') setError('name', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      <TextField label="Name" required autoFocus error={errors.name?.message}
        {...register('name', {
          required: 'Name is required',
          minLength: { value: 2, message: 'At least 2 characters' },
          maxLength: { value: 100, message: 'Max 100 characters' },
        })} />
      <TextAreaField label="Description" rows={3} error={errors.description?.message}
        {...register('description', { maxLength: { value: 500, message: 'Max 500 characters' } })} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>{department ? 'Save' : 'Create'}</Button>
      </div>
    </form>
  );
}

export default function DepartmentsAdminPage() {
  const { data, loading, error, reload } = useApi(adminListDepartments);
  const [editing, setEditing] = useState(null); // null = closed, {} = new, object = edit
  const [toggling, setToggling] = useState(null);
  const [busy, setBusy] = useState(false);
  const rows = data || [];

  async function confirmToggle() {
    setBusy(true);
    try {
      await adminSetDepartmentActive(toggling.id, !toggling.active);
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
      <PageHeader title="Departments" description="Departments group doctors and appear on the public website."
        actions={<Button onClick={() => setEditing({})}><Plus className="h-4 w-4" aria-hidden="true" /> Add department</Button>} />
      <Card>
        {loading && !data && <Spinner />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && rows.length === 0 && <EmptyState icon={Building2} title="No departments yet" />}
        {rows.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {rows.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center justify-between gap-3 py-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-slate-900">{d.name}</p>
                    <StatusBadge tone={d.active ? 'green' : 'slate'}>{d.active ? 'Active' : 'Inactive'}</StatusBadge>
                  </div>
                  <p className="text-sm text-slate-500">{d.description || 'No description'}</p>
                  <p className="mt-0.5 text-xs text-slate-400">{d.activeDoctorCount} active doctor(s)</p>
                </div>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" onClick={() => setEditing(d)} aria-label={`Edit ${d.name}`}>
                    <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
                  </Button>
                  <Button variant={d.active ? 'secondary' : 'primary'} size="sm" onClick={() => setToggling(d)}>
                    {d.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Modal open={editing !== null} title={editing?.id ? `Edit ${editing.name}` : 'Add department'} onClose={() => setEditing(null)}>
        <DepartmentForm department={editing?.id ? editing : null}
          onSaved={() => { setEditing(null); reload(); }} onCancel={() => setEditing(null)} />
      </Modal>

      <ConfirmDialog
        open={!!toggling}
        tone={toggling?.active ? 'danger' : 'primary'}
        title={toggling ? `${toggling.active ? 'Deactivate' : 'Activate'} ${toggling.name}?` : ''}
        message={toggling?.active
          ? (toggling.activeDoctorCount > 0
            ? `This department has ${toggling.activeDoctorCount} active doctor(s); the server will refuse until they are reassigned or deactivated.`
            : 'The department will be hidden from patients. Historic records are kept.')
          : 'The department will be visible to patients again.'}
        confirmLabel={toggling?.active ? 'Deactivate' : 'Activate'}
        loading={busy}
        onCancel={() => setToggling(null)}
        onConfirm={confirmToggle}
      />
    </>
  );
}
