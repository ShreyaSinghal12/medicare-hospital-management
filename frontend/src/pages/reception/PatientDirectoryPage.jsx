import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, UserPlus, Users } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { searchReceptionPatients } from '../../services/receptionService.js';

export default function PatientDirectoryPage() {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => searchReceptionPatients({ search, page, size: 15 }), [search, page]);
  const rows = data?.content || [];
  return (
    <>
      <PageHeader title="Patient directory" description="Contact and registration details. Medical records are not available at reception."
        actions={<Button to="/reception/patients/new"><UserPlus className="h-4 w-4" aria-hidden="true" /> Register walk-in</Button>} />
      <Card>
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-500" aria-hidden="true" />
          <TextField label="Search" placeholder="Name, phone, e-mail or patient ID" value={input} className="pl-9"
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
                  <Link to={`/reception/patients/${p.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">{p.fullName}</Link>
                  <p className="text-sm text-slate-500">{p.patientCode} · {p.phone} · born {p.dateOfBirth}</p>
                </div>
                <Button size="sm" variant="secondary" to={`/reception/book?patientId=${p.id}`}>Book</Button>
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
