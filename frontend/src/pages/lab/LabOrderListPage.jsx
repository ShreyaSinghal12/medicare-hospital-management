import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FlaskConical, Search } from 'lucide-react';
import LabStatusBadge, { LAB_STATUS } from '../../components/lab/LabStatusBadge.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { getDoctorLabOrders, getMyLabOrders } from '../../services/labService.js';
import { formatDateTime } from '../../utils/format.js';

/** Lab orders: a doctor's own orders, or a patient's tests and reports. */
export default function LabOrderListPage({ mode }) {
  const patient = mode === 'patient';
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const load = patient ? getMyLabOrders : getDoctorLabOrders;
  const { data, loading, error, reload } = useApi(() => load({ search, status, page }), [search, status, page]);
  const rows = data?.content || [];
  const base = patient ? '/patient/lab-reports' : '/doctor/lab-orders';

  return (
    <>
      <PageHeader title={patient ? 'Lab reports' : 'Lab orders'}
        description={patient
          ? 'Tests your doctors ordered. Results appear once your doctor releases the report.'
          : 'Tests you ordered. Open an order to record results, upload the report and release it.'} />
      <Card>
        <div className="grid gap-3 sm:grid-cols-[1fr_14rem]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
            <TextField label="Search" placeholder={patient ? 'Order number or test' : 'Order number, patient or test'} value={input}
              className="pl-9" onChange={(e) => { setInput(e.target.value); setPage(0); }} />
          </div>
          <SelectField label="Status" placeholder="All" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
            options={Object.entries(LAB_STATUS).map(([value, s]) => ({ value, label: s.label }))} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={FlaskConical} title={patient ? 'No lab tests yet' : 'No lab orders'} />}
          <ul className="divide-y divide-slate-100">
            {rows.map((o) => (
              <li key={o.id}>
                <Link to={`${base}/${o.id}`} className="flex flex-wrap items-center justify-between gap-3 rounded-lg px-2 py-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{o.tests.join(', ')}</p>
                    <p className="text-sm text-slate-500">
                      {o.orderNo} · {patient ? o.doctorName : `${o.patientName} (${o.patientCode})`} · {formatDateTime(o.orderedAt)}
                    </p>
                  </div>
                  <LabStatusBadge status={o.status} priority={o.priority} />
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
