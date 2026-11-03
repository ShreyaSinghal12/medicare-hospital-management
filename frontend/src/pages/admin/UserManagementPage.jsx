import { useEffect, useState } from 'react';
import { Search, Unlock, Users } from 'lucide-react';
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
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { searchUsers, setUserActive, unlockUser } from '../../services/adminUserService.js';
import { formatDateTime } from '../../utils/format.js';
import { ALL_ROLES, ROLE_LABEL } from '../../utils/roles.js';

/** Admin: search accounts, activate/deactivate and unlock. Shows no clinical data. */
export default function UserManagementPage() {
  const { user: me } = useAuth();
  const [searchInput, setSearchInput] = useState('');
  const [filters, setFilters] = useState({ search: '', role: '', active: '' });
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState(null); // { user, activate }
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  // Debounce the free-text search.
  useEffect(() => {
    const id = setTimeout(() => {
      setFilters((f) => (f.search === searchInput ? f : { ...f, search: searchInput }));
      setPage(0);
    }, 350);
    return () => clearTimeout(id);
  }, [searchInput]);

  const { data, loading, error, reload } = useApi(
    () => searchUsers({ ...filters, page, size: 10 }),
    [filters.search, filters.role, filters.active, page],
  );
  const rows = data?.content || [];

  async function confirmStatusChange() {
    setSaving(true);
    try {
      await setUserActive(pending.user.id, pending.activate, reason || undefined);
      toast.success(`${pending.user.fullName} has been ${pending.activate ? 'activated' : 'deactivated'}`);
      setPending(null);
      setReason('');
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleUnlock(u) {
    try {
      await unlockUser(u.id);
      toast.success(`${u.fullName} has been unlocked`);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  }

  return (
    <>
      <PageHeader title="Users & access" description="Search accounts, activate or deactivate them and clear login locks." />
      <Card>
        <div className="grid gap-3 sm:grid-cols-[1fr_12rem_10rem]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
            <TextField
              label="Search"
              placeholder="Name, e-mail or phone"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="pl-9"
            />
          </div>
          <SelectField
            label="Role" placeholder="All roles" value={filters.role}
            options={ALL_ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r] }))}
            onChange={(e) => { setFilters((f) => ({ ...f, role: e.target.value })); setPage(0); }}
          />
          <SelectField
            label="Status" placeholder="Any status" value={filters.active}
            options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]}
            onChange={(e) => { setFilters((f) => ({ ...f, active: e.target.value })); setPage(0); }}
          />
        </div>

        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {!loading && !error && rows.length === 0 && (
            <EmptyState icon={Users} title="No users found" message="Try a different search or filter." />
          )}
          {rows.length > 0 && (
            <div className="-mx-5 overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Name</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Role</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Status</th>
                    <th scope="col" className="hidden px-5 py-3 font-semibold md:table-cell">Last login</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((u) => (
                    <tr key={u.id} className="hover:bg-slate-50/60">
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">{u.fullName}</p>
                        <p className="text-slate-500">{u.email}</p>
                      </td>
                      <td className="px-5 py-3 text-slate-700">{ROLE_LABEL[u.role]}</td>
                      <td className="px-5 py-3">
                        <div className="flex flex-wrap gap-1">
                          <StatusBadge tone={u.active ? 'green' : 'slate'}>{u.active ? 'Active' : 'Inactive'}</StatusBadge>
                          {u.locked && <StatusBadge tone="amber">Locked</StatusBadge>}
                        </div>
                      </td>
                      <td className="hidden whitespace-nowrap px-5 py-3 text-slate-600 md:table-cell">{formatDateTime(u.lastLoginAt)}</td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          {u.locked && (
                            <Button variant="secondary" size="sm" onClick={() => handleUnlock(u)}>
                              <Unlock className="h-4 w-4" aria-hidden="true" /> Unlock
                            </Button>
                          )}
                          {u.id === me.id ? (
                            <span className="self-center text-xs text-slate-400">This is you</span>
                          ) : (
                            <Button
                              variant={u.active ? 'secondary' : 'primary'}
                              size="sm"
                              onClick={() => setPending({ user: u, activate: !u.active })}
                            >
                              {u.active ? 'Deactivate' : 'Activate'}
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="mt-4">
            <Pagination data={data} onPageChange={setPage} />
          </div>
        </div>
      </Card>

      <ConfirmDialog
        open={!!pending}
        tone={pending?.activate ? 'primary' : 'danger'}
        title={pending ? `${pending.activate ? 'Activate' : 'Deactivate'} ${pending.user.fullName}?` : ''}
        message={
          pending?.activate
            ? 'The user will be able to log in again.'
            : 'The user will be signed out immediately and cannot log in until reactivated. Their records are kept.'
        }
        confirmLabel={pending?.activate ? 'Activate' : 'Deactivate'}
        loading={saving}
        onCancel={() => { setPending(null); setReason(''); }}
        onConfirm={confirmStatusChange}
      >
        <TextField label="Reason (optional, kept in the audit log)" value={reason} maxLength={300}
          onChange={(e) => setReason(e.target.value)} />
      </ConfirmDialog>
    </>
  );
}
