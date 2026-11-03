import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Copy, Pencil, Search, UserRound } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { adminCorrectPatient, adminFindDuplicatePatients, adminSearchPatients } from '../../services/adminService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { GENDERS, NAME_PATTERN, PHONE_PATTERN, todayIso } from '../../utils/validation.js';

function CorrectionForm({ patient, onSaved, onCancel }) {
  const {
    register, handleSubmit, setError,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: {
      fullName: patient.fullName, phone: patient.phone || '', dateOfBirth: patient.dateOfBirth, gender: patient.gender || '',
      address: patient.address || '', emergencyContactName: patient.emergencyContactName || '',
      emergencyContactPhone: patient.emergencyContactPhone || '', reason: '',
    },
  });

  async function onSubmit(values) {
    try {
      await adminCorrectPatient(patient.id, { ...values, gender: values.gender || null });
      toast.success('Patient record corrected');
      onSaved();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600 sm:col-span-2">
        Only demographic data can be corrected here. Clinical information is maintained by doctors and is not visible to administrators.
      </p>
      <TextField label="Full name" required error={errors.fullName?.message}
        {...register('fullName', { required: 'Required', pattern: { value: NAME_PATTERN, message: 'Letters, spaces, dots, apostrophes or hyphens' } })} />
      <TextField label="Phone" type="tel" required error={errors.phone?.message}
        {...register('phone', { required: 'Required', pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' } })} />
      <TextField label="Date of birth" type="date" required max={todayIso()} error={errors.dateOfBirth?.message}
        {...register('dateOfBirth', { required: 'Required', validate: (v) => v < todayIso() || 'Must be in the past' })} />
      <SelectField label="Gender" placeholder="Not specified" options={GENDERS} {...register('gender')} />
      <div className="sm:col-span-2">
        <TextField label="Address" error={errors.address?.message} {...register('address', { maxLength: { value: 500, message: 'Max 500' } })} />
      </div>
      <TextField label="Emergency contact name" {...register('emergencyContactName', { maxLength: { value: 120, message: 'Max 120' } })} />
      <TextField label="Emergency contact phone" type="tel" error={errors.emergencyContactPhone?.message}
        {...register('emergencyContactPhone', { validate: (v) => !v || PHONE_PATTERN.test(v) || 'Enter a valid phone number' })} />
      <div className="sm:col-span-2">
        <TextAreaField label="Reason for correction" required rows={2} hint="Stored in the audit log." error={errors.reason?.message}
          {...register('reason', { required: 'A reason is required', minLength: { value: 5, message: 'At least 5 characters' }, maxLength: { value: 300, message: 'Max 300' } })} />
      </div>
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Save correction</Button>
      </div>
    </form>
  );
}

function PatientRow({ p, onEdit }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <p className="font-medium text-slate-900">{p.fullName} <span className="text-sm font-normal text-slate-500">· {p.patientCode}</span></p>
        <p className="text-sm text-slate-500">{p.email} · {p.phone}</p>
        <p className="text-xs text-slate-400">Born {p.dateOfBirth} ({p.age} yrs)</p>
      </div>
      <div className="flex items-center gap-2">
        <StatusBadge tone={p.active ? 'green' : 'slate'}>{p.active ? 'Active' : 'Inactive'}</StatusBadge>
        <Button variant="secondary" size="sm" onClick={() => onEdit(p)} aria-label={`Correct ${p.fullName}`}>
          <Pencil className="h-4 w-4" aria-hidden="true" /> Correct
        </Button>
      </div>
    </li>
  );
}

/** Admin patient administration: search, non-clinical corrections and duplicate review. */
export default function PatientsAdminPage() {
  const [tab, setTab] = useState('directory');
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null);
  const directory = useApi(() => adminSearchPatients({ search, page }), [search, page]);
  const duplicates = useApi(adminFindDuplicatePatients);
  const rows = directory.data?.content || [];
  const groups = duplicates.data || [];

  function saved() {
    setEditing(null);
    directory.reload();
    duplicates.reload();
  }

  return (
    <>
      <PageHeader title="Patients" description="Registration troubleshooting and demographic corrections. Clinical records are not shown here." />
      <Card>
        <Tabs label="Patient views" value={tab} onChange={setTab} tabs={[
          { value: 'directory', label: 'Directory', count: directory.data?.totalElements },
          { value: 'duplicates', label: 'Possible duplicates', count: groups.length },
        ]} />

        {tab === 'directory' && (
          <>
            <div className="relative max-w-md">
              <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
              <TextField label="Search" placeholder="Name, e-mail, phone or patient ID" value={searchInput}
                onChange={(e) => { setSearchInput(e.target.value); setPage(0); }} className="pl-9" />
            </div>
            <div className="mt-4">
              {directory.loading && !directory.data && <Spinner />}
              {directory.error && <ErrorState error={directory.error} onRetry={directory.reload} />}
              {!directory.loading && !directory.error && rows.length === 0 && <EmptyState icon={UserRound} title="No patients found" />}
              {rows.length > 0 && <ul className="divide-y divide-slate-100">{rows.map((p) => <PatientRow key={p.id} p={p} onEdit={setEditing} />)}</ul>}
              <div className="mt-4"><Pagination data={directory.data} onPageChange={setPage} /></div>
            </div>
          </>
        )}

        {tab === 'duplicates' && (
          <div>
            {duplicates.loading && !duplicates.data && <Spinner />}
            {duplicates.error && <ErrorState error={duplicates.error} onRetry={duplicates.reload} />}
            {!duplicates.loading && !duplicates.error && groups.length === 0 && (
              <EmptyState icon={Copy} title="No possible duplicates" message="No patients share the same name and date of birth or the same phone number." />
            )}
            <div className="space-y-4">
              {groups.map((g) => (
                <section key={g.matchReason + g.matchValue} className="rounded-lg border border-amber-200 bg-amber-50/40 p-4">
                  <h3 className="text-sm font-semibold text-amber-900">
                    {g.matchReason === 'PHONE' ? `Same phone number (…${g.matchValue.slice(-4)})` : `Same name and date of birth (${g.matchValue})`}
                  </h3>
                  <ul className="mt-2 divide-y divide-amber-100">{g.patients.map((p) => <PatientRow key={p.id} p={p} onEdit={setEditing} />)}</ul>
                </section>
              ))}
            </div>
            {groups.length > 0 && (
              <p className="mt-4 text-xs text-slate-500">
                Records are never merged automatically. Correct the demographics, or deactivate the duplicate account in Users &amp; access.
              </p>
            )}
          </div>
        )}
      </Card>

      <Modal open={!!editing} title={editing ? `Correct ${editing.fullName} (${editing.patientCode})` : ''} size="lg" onClose={() => setEditing(null)}>
        {editing && <CorrectionForm patient={editing} onSaved={saved} onCancel={() => setEditing(null)} />}
      </Modal>
    </>
  );
}
