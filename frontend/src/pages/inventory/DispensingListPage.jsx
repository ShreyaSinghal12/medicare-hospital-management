import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList, Search } from 'lucide-react';
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
import { listDispensings } from '../../services/inventoryService.js';
import { formatDateTime } from '../../utils/format.js';

export default function DispensingListPage() {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => listDispensings({ search, page }), [search, page]);
  const rows = data?.content || [];
  return (
    <>
      <PageHeader title="Dispensing records" actions={<Button to="/admin/dispensing/new">Dispense medicines</Button>} />
      <Card>
        <div className="relative max-w-md">
          <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-500" aria-hidden="true" />
          <TextField label="Search" placeholder="DSP or RX number, patient name or ID" value={input} className="pl-9"
            onChange={(e) => { setInput(e.target.value); setPage(0); }} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={ClipboardList} title="No dispensing records" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((d) => (
              <li key={d.id}>
                <Link to={`/admin/dispensing/${d.id}`} className="flex flex-wrap items-center justify-between gap-2 rounded-lg px-2 py-3 hover:bg-slate-50">
                  <div>
                    <p className="font-medium text-slate-900">{d.dispensingNo} · {d.prescriptionNo}</p>
                    <p className="text-sm text-slate-500">{d.patientName} ({d.patientCode}) · by {d.dispensedBy}</p>
                  </div>
                  <span className="text-sm text-slate-600">{d.totalQuantity} unit(s) · {formatDateTime(d.dispensedAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
