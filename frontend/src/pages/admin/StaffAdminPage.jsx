import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Plus, Search, Users } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { adminCreateStaff, adminSearchStaff } from '../../services/adminService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { formatDateTime } from '../../utils/format.js';
import { ROLE_LABEL } from '../../utils/roles.js';
import { EMAIL_PATTERN, NAME_PATTERN, PHONE_PATTERN } from '../../utils/validation.js';

const STAFF_ROLES = ['RECEPTIONIST', 'LAB_TECHNICIAN', 'PHARMACIST', 'ADMIN'];

function StaffForm({ defaultRole, onSaved, onCancel }) {
  const {
    register, handleSubmit, watch, setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { role: defaultRole, fullName: '', email: '', phone: '', qualification: '', licenseNumber: '' } });
  const role = watch('role');

  async function onSubmit(values) {
    try {
      const created = await adminCreateStaff({ ...values, email: values.email.trim() });
      toast.success(`${created.fullName} created${created.employeeCode ? ` (${created.employeeCode})` : ''}. A set-password e-mail was sent.`);
      onSaved(created.role);
    } catch (err) {
      if (err.code === 'EMAIL_ALREADY_REGISTERED') setError('email', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <SelectField label="Role" required options={STAFF_ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r] }))} {...register('role')} />
      <TextField label="Full name" required error={errors.fullName?.message}
        {...register('fullName', { required: 'Full name is required', pattern: { value: NAME_PATTERN, message: 'Letters, spaces, dots, apostrophes or hyphens' } })} />
      <TextField label="E-mail" type="email" required error={errors.email?.message}
        {...register('email', { required: 'E-mail is required', pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' } })} />
      <TextField label="Phone" type="tel" required error={errors.phone?.message}
        {...register('phone', { required: 'Phone is required', pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' } })} />
      {role === 'LAB_TECHNICIAN' && (
        <TextField label="Qualification" placeholder="e.g. B.Sc. MLT" error={errors.qualification?.message}
          {...register('qualification', { maxLength: { value: 150, message: 'Max 150 characters' } })} />
      )}
      {role === 'PHARMACIST' && (
        <TextField label="Pharmacist licence number" error={errors.licenseNumber?.message}
          {...register('licenseNumber', { maxLength: { value: 50, message: 'Max 50 characters' } })} />
      )}
      {role === 'ADMIN' && (
        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800 sm:col-span-2">
          Administrators can manage all accounts and settings. Grant this role only when necessary.
        </p>
      )}
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Create account</Button>
      </div>
    </form>
  );
}

/** Admin: receptionists, lab technicians, pharmacists and administrators. */
export default function StaffAdminPage() {
  const [role, setRole] = useState('RECEPTIONIST');
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const { data, loading, error, reload } = useApi(() => adminSearchStaff({ role, search, page }), [role, search, page]);
  const rows = data?.content || [];

  return (
    <>
      <PageHeader title="Staff accounts" description="Create front-desk, laboratory, pharmacy and administrator accounts. Use Users & access to deactivate or unlock accounts."
        actions={<Button onClick={() => setCreating(true)}><Plus className="h-4 w-4" aria-hidden="true" /> Add staff member</Button>} />
      <Card>
        <Tabs label="Staff role" value={role} onChange={(r) => { setRole(r); setPage(0); }}
          tabs={STAFF_ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r] }))} />
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
          <TextField label="Search" placeholder="Name, e-mail or phone" value={searchInput}
            onChange={(e) => { setSearchInput(e.target.value); setPage(0); }} className="pl-9" />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {!loading && !error && rows.length === 0 && <EmptyState icon={Users} title={`No ${ROLE_LABEL[role].toLowerCase()} accounts`} />}
          {rows.length > 0 && (
            <ul className="divide-y divide-slate-100">
              {rows.map((s) => (
                <li key={s.userId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{s.fullName}</p>
                    <p className="text-sm text-slate-500">{s.email} · {s.phone}</p>
                    <p className="text-xs text-slate-400">
                      {[s.employeeCode, s.qualification, s.licenseNumber && `Licence ${s.licenseNumber}`].filter(Boolean).join(' · ') || ROLE_LABEL[s.role]}
                      {' · '}Last login {formatDateTime(s.lastLoginAt)}
                    </p>
                  </div>
                  <StatusBadge tone={s.active ? 'green' : 'slate'}>{s.active ? 'Active' : 'Inactive'}</StatusBadge>
                </li>
              ))}
            </ul>
          )}
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>

      <Modal open={creating} title="Add staff member" description="The new user receives an e-mail link to set their password." size="lg" onClose={() => setCreating(false)}>
        <StaffForm defaultRole={role} onCancel={() => setCreating(false)}
          onSaved={(createdRole) => { setCreating(false); setRole(createdRole); reload(); }} />
      </Modal>
    </>
  );
}
