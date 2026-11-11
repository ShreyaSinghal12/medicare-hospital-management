import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FlaskConical } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import { getConsultationLabOrders } from '../../services/labService.js';
import Button from '../ui/Button.jsx';
import Card from '../ui/Card.jsx';
import Spinner from '../ui/Spinner.jsx';
import LabStatusBadge from './LabStatusBadge.jsx';
import OrderLabTestsDialog from './OrderLabTestsDialog.jsx';

/** Lab orders of a consultation, with "Order tests" for the consulting doctor. */
export default function ConsultationLabCard({ consultationId, canOrder }) {
  const { data, loading } = useApi(() => getConsultationLabOrders(consultationId), [consultationId]);
  const [open, setOpen] = useState(false);

  return (
    <Card title="Lab tests" actions={<FlaskConical className="h-5 w-5 text-brand-600" aria-hidden="true" />}>
      {loading && <Spinner />}
      {data && data.length === 0 && <p className="mb-3 text-sm text-slate-500">No tests ordered.</p>}
      <ul className="mb-3 space-y-2">
        {(data || []).map((o) => (
          <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <Link to={`/doctor/lab-orders/${o.id}`} className="font-medium text-brand-700 hover:underline">
              {o.orderNo} · {o.tests.join(', ')}
            </Link>
            <LabStatusBadge status={o.status} priority={o.priority} />
          </li>
        ))}
      </ul>
      {canOrder && <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>Order tests</Button>}
      <OrderLabTestsDialog consultationId={consultationId} open={open} onClose={() => setOpen(false)} />
    </Card>
  );
}
