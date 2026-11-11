import { useState } from 'react';
import { Link } from 'react-router-dom';
import useApi from '../../hooks/useApi.js';
import { getPatientLabOrders } from '../../services/labService.js';
import { formatDateTime } from '../../utils/format.js';
import Card from '../ui/Card.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import Pagination from '../ui/Pagination.jsx';
import Spinner from '../ui/Spinner.jsx';
import LabStatusBadge from './LabStatusBadge.jsx';

/** All lab orders of a patient, for a treating doctor's record view (the server audits this access). */
export default function PatientLabOrdersCard({ patientId }) {
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => getPatientLabOrders(patientId, { page }), [patientId, page]);
  const rows = data?.content || [];

  return (
    <Card title="Lab tests">
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && rows.length === 0 && <p className="text-sm text-slate-500">No lab tests ordered.</p>}
      <ul className="divide-y divide-slate-100">
        {rows.map((o) => (
          <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <div className="min-w-0">
              <Link to={`/doctor/lab-orders/${o.id}`} className="font-medium text-brand-700 hover:underline">{o.tests.join(', ')}</Link>
              <p className="text-slate-500">{o.orderNo} · {o.doctorName} · {formatDateTime(o.orderedAt)}</p>
            </div>
            <LabStatusBadge status={o.status} priority={o.priority} />
          </li>
        ))}
      </ul>
      {data && data.totalPages > 1 && <div className="mt-3"><Pagination data={data} onPageChange={setPage} /></div>}
    </Card>
  );
}
