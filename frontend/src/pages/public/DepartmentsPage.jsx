import { Building2 } from 'lucide-react';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getHospitalInfo } from '../../services/systemService.js';

export default function DepartmentsPage() {
  const { data, loading, error, reload } = useApi(getHospitalInfo);
  const departments = data?.departments || [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold text-slate-900">Departments</h1>
      <p className="mt-2 text-slate-600">Specialist care available at {data?.hospitalName || 'our hospital'}.</p>

      <div className="mt-8">
        {loading && <Spinner label="Loading departments…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && departments.length === 0 && (
          <EmptyState icon={Building2} title="No departments yet" message="Departments will appear here once an administrator adds them." />
        )}
        {departments.length > 0 && (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {departments.map((d) => (
              <li key={d.id} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <Building2 className="h-6 w-6 text-brand-600" aria-hidden="true" />
                <h2 className="mt-3 font-semibold text-slate-900">{d.name}</h2>
                <p className="mt-1 text-sm text-slate-600">{d.description}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
