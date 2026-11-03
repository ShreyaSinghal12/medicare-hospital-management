import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Stethoscope, X } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { getPublicDepartments, getSpecializations, searchDoctors } from '../../services/doctorService.js';
import Button from '../ui/Button.jsx';
import EmptyState from '../ui/EmptyState.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import { SelectField, TextField } from '../ui/FormField.jsx';
import Pagination from '../ui/Pagination.jsx';
import Spinner from '../ui/Spinner.jsx';
import DoctorCard from './DoctorCard.jsx';

/**
 * Searchable, filterable doctor list. Filters live in the URL (?search=&departmentId=&specialization=&page=)
 * so results can be bookmarked and the browser back button works.
 *
 * @param profileBase path prefix for profile links, e.g. "/doctors" or "/patient/doctors"
 */
export default function DoctorDirectory({ profileBase }) {
  const [params, setParams] = useSearchParams();
  const [searchInput, setSearchInput] = useState(params.get('search') || '');
  const debounced = useDebouncedValue(searchInput);
  const departmentId = params.get('departmentId') || '';
  const specialization = params.get('specialization') || '';
  const page = Number(params.get('page') || 0);
  const search = params.get('search') || '';

  function update(changes) {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null || v === undefined ? next.delete(k) : next.set(k, v)));
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  }

  useEffect(() => {
    if (debounced !== search) update({ search: debounced.trim() });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);

  const { data: departments } = useApi(getPublicDepartments);
  const { data: specializations } = useApi(getSpecializations);
  const { data, loading, error, reload } = useApi(
    () => searchDoctors({ search, departmentId, specialization, page }),
    [search, departmentId, specialization, page],
  );
  const doctors = data?.content || [];
  const filtered = search || departmentId || specialization;

  return (
    <div>
      <div className="grid gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-[1fr_14rem_14rem]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
          <TextField label="Search doctors" placeholder="Name or specialty" value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)} className="pl-9" />
        </div>
        <SelectField label="Department" placeholder="All departments" value={departmentId}
          options={(departments || []).map((d) => ({ value: String(d.id), label: d.name }))}
          onChange={(e) => update({ departmentId: e.target.value })} />
        <SelectField label="Specialization" placeholder="All specializations" value={specialization}
          options={(specializations || []).map((s) => ({ value: s, label: s }))}
          onChange={(e) => update({ specialization: e.target.value })} />
      </div>

      {filtered && (
        <div className="mt-3">
          <Button variant="ghost" size="sm" onClick={() => { setSearchInput(''); setParams({}, { replace: true }); }}>
            <X className="h-4 w-4" aria-hidden="true" /> Clear filters
          </Button>
        </div>
      )}

      <div className="mt-6" aria-live="polite">
        {loading && !data && <Spinner label="Loading doctors…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && doctors.length === 0 && (
          <EmptyState icon={Stethoscope} title="No doctors found"
            message={filtered ? 'Try a different name, department or specialization.' : 'No doctors are available yet.'} />
        )}
        {doctors.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {doctors.map((d) => <DoctorCard key={d.id} doctor={d} to={`${profileBase}/${d.id}`} />)}
          </div>
        )}
        <div className="mt-6">
          <Pagination data={data} onPageChange={(p) => update({ page: String(p) })} />
        </div>
      </div>
    </div>
  );
}
