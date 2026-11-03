import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, Users } from 'lucide-react';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { getMyPatients } from '../../services/clinicalService.js';

/** Patients the doctor has a treatment relationship with. */
export default function MyPatientsPage() {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => getMyPatients({ search, page }), [search, page]);
  const rows = data?.content || [];
  return (
    <>
      <PageHeader title="My patients" description="Patients you have (or had) appointments with. Opening a record is logged." />
      <Card>
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
          <TextField label="Search" placeholder="Name or patient ID" value={input} className="pl-9"
            onChange={(e) => { setInput(e.target.value); setPage(0); }} />
        </div>
        <div className="mt-4">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {!loading && !error && rows.length === 0 && <EmptyState icon={Users} title="No patients found" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <Link to={`/doctor/patients/${p.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">{p.fullName}</Link>
                  <p className="text-sm text-slate-500">{p.patientCode} · {p.age} yrs · {p.gender ? p.gender.toLowerCase().replaceAll('_', ' ') : 'gender not given'}{p.bloodGroup ? ` · ${p.bloodGroup}` : ''}</p>
                </div>
                <Link to={`/doctor/patients/${p.id}`} className="text-sm font-medium text-brand-700 hover:underline">Open record →</Link>
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
