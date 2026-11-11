import { Link } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getPublicDepartments } from '../../services/doctorService.js';
import PageHeading from '../../components/ui/PageHeading.jsx';

export default function DepartmentsPage() {
  const { data, loading, error, reload } = useApi(getPublicDepartments);
  const departments = data || [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <PageHeading title={'Departments'} className="text-3xl font-bold text-slate-900">Departments</PageHeading>
      <p className="mt-2 text-slate-600">Specialist care available at our hospital.</p>

      <div className="mt-8">
        {loading && <Spinner label="Loading departments…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && departments.length === 0 && (
          <EmptyState icon={Building2} title="No departments yet" message="Departments will appear here once an administrator adds them." />
        )}
        {departments.length > 0 && (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {departments.map((d) => (
              <li key={d.id} className="flex flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <Building2 className="h-6 w-6 text-brand-700" aria-hidden="true" />
                <h2 className="mt-3 font-semibold text-slate-900">{d.name}</h2>
                <p className="mt-1 flex-1 text-sm text-slate-600">{d.description}</p>
                <div className="mt-4 flex items-center justify-between text-sm">
                  <span className="text-slate-500">{d.activeDoctorCount} doctor{d.activeDoctorCount === 1 ? '' : 's'}</span>
                  {d.activeDoctorCount > 0 && (
                    <Link to={`/doctors?departmentId=${d.id}`} className="font-medium text-brand-700 hover:underline"
                      aria-label={`View doctors in ${d.name}`}>
                      View doctors →
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
