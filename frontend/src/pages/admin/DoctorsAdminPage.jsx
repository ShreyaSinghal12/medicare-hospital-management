import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Pencil, Plus, Search, Stethoscope } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { adminListDepartments, adminSearchDoctors } from '../../services/adminService.js';
import { setUserActive } from '../../services/adminUserService.js';
import { formatCurrency } from '../../utils/format.js';

export default function DoctorsAdminPage() {
  const [searchInput, setSearchInput] = useState('');
  const search = useDebouncedValue(searchInput);
  const [departmentId, setDepartmentId] = useState('');
  const [active, setActive] = useState('');
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState(null);
  const [saving, setSaving] = useState(false);

  const { data: departments } = useApi(adminListDepartments);
  const { data, loading, error, reload } = useApi(
    () => adminSearchDoctors({ search, departmentId, active, page }),
    [search, departmentId, active, page],
  );
  const rows = data?.content || [];

  async function toggle() {
    setSaving(true);
    try {
      await setUserActive(pending.userId, !pending.active, 'Changed from doctor management');
      toast.success(`${pending.fullName} ${pending.active ? 'deactivated' : 'activated'}`);
      setPending(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Doctors"
        description="Add doctors, assign departments and update consultation fees."
        actions={<Button to="/admin/doctors/new"><Plus className="h-4 w-4" aria-hidden="true" /> Add doctor</Button>}
      />
      <Card>
        <div className="grid gap-3 sm:grid-cols-[1fr_13rem_10rem]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
            <TextField label="Search" placeholder="Name, specialty, e-mail or licence" value={searchInput}
              onChange={(e) => { setSearchInput(e.target.value); setPage(0); }} className="pl-9" />
          </div>
          <SelectField label="Department" placeholder="All departments" value={departmentId}
            options={(departments || []).map((d) => ({ value: String(d.id), label: d.name + (d.active ? '' : ' (inactive)') }))}
            onChange={(e) => { setDepartmentId(e.target.value); setPage(0); }} />
          <SelectField label="Status" placeholder="Any status" value={active}
            options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]}
            onChange={(e) => { setActive(e.target.value); setPage(0); }} />
        </div>

        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {!loading && !error && rows.length === 0 && (
            <EmptyState icon={Stethoscope} title="No doctors found"
              action={<Button to="/admin/doctors/new" size="sm">Add the first doctor</Button>} />
          )}
          {rows.length > 0 && (
            <div className="-mx-5 overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Doctor</th>
                    <th scope="col" className="hidden px-5 py-3 font-semibold md:table-cell">Department</th>
                    <th scope="col" className="hidden px-5 py-3 font-semibold sm:table-cell">Fee</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Status</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((d) => (
                    <tr key={d.id}>
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">{d.fullName}</p>
                        <p className="text-slate-500">{d.specialization} · {d.licenseNumber}</p>
                      </td>
                      <td className="hidden px-5 py-3 text-slate-700 md:table-cell">{d.departmentName}</td>
                      <td className="hidden whitespace-nowrap px-5 py-3 text-slate-700 sm:table-cell">{formatCurrency(d.consultationFee)}</td>
                      <td className="px-5 py-3">
                        <StatusBadge tone={d.active ? 'green' : 'slate'}>{d.active ? 'Active' : 'Inactive'}</StatusBadge>
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Link to={`/admin/doctors/${d.id}/edit`} aria-label={`Edit ${d.fullName}`}
                            className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                            <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
                          </Link>
                          <Button variant={d.active ? 'secondary' : 'primary'} size="sm" onClick={() => setPending(d)}>
                            {d.active ? 'Deactivate' : 'Activate'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>

      <ConfirmDialog
        open={!!pending}
        tone={pending?.active ? 'danger' : 'primary'}
        title={pending ? `${pending.active ? 'Deactivate' : 'Activate'} ${pending.fullName}?` : ''}
        message={pending?.active
          ? 'The doctor will be signed out, hidden from patients and unable to log in. Historic appointments and prescriptions are kept.'
          : 'The doctor will be able to log in and will appear in the doctor directory again.'}
        confirmLabel={pending?.active ? 'Deactivate' : 'Activate'}
        loading={saving}
        onCancel={() => setPending(null)}
        onConfirm={toggle}
      />
    </>
  );
}
