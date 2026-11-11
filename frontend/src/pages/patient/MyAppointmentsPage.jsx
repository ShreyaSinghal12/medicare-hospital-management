import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, Plus } from 'lucide-react';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import CancelAppointmentDialog from '../../components/appointments/CancelAppointmentDialog.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import useApi from '../../hooks/useApi.js';
import { getMyAppointments } from '../../services/appointmentService.js';
import { formatDateShort, hhmm } from '../../utils/dates.js';

const TABS = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'PAST', label: 'History' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export default function MyAppointmentsPage() {
  const [scope, setScope] = useState('UPCOMING');
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState(null);
  const { data, loading, error, reload } = useApi(() => getMyAppointments({ scope, page }), [scope, page]);
  const rows = data?.content || [];

  return (
    <>
      <PageHeader title="My appointments" description="Book, reschedule or cancel your visits."
        actions={<Button to="/patient/doctors"><Plus className="h-4 w-4" aria-hidden="true" /> Book appointment</Button>} />
      <Card>
        <Tabs label="Appointment lists" value={scope} onChange={(v) => { setScope(v); setPage(0); }} tabs={TABS} />
        {loading && !data && <Spinner />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && rows.length === 0 && (
          <EmptyState icon={CalendarDays}
            title={scope === 'UPCOMING' ? 'No upcoming appointments' : scope === 'PAST' ? 'No past appointments' : 'No cancelled appointments'}
            action={scope === 'UPCOMING' && <Button to="/patient/doctors" size="sm">Find a doctor</Button>} />
        )}
        {rows.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {rows.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 py-4">
                <div className="flex min-w-0 items-start gap-4">
                  <div className="w-16 shrink-0 rounded-lg bg-brand-50 py-2 text-center text-brand-800">
                    <p className="text-xs font-medium">{formatDateShort(a.date).split(' ')[0]}</p>
                    <p className="text-lg font-bold leading-tight">{a.date.slice(8)}</p>
                    <p className="text-xs">{hhmm(a.startTime)}</p>
                  </div>
                  <div className="min-w-0">
                    <Link to={`/patient/appointments/${a.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">
                      {a.doctorName}
                    </Link>
                    <p className="text-sm text-slate-500">{a.specialization} · {formatDateShort(a.date)} {hhmm(a.startTime)}–{hhmm(a.endTime)}</p>
                    <p className="text-xs text-slate-500">{a.referenceNo}</p>
                    <div className="mt-1"><AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} /></div>
                  </div>
                </div>
                <div className="flex gap-2">
                  {a.canReschedule && <Button size="sm" variant="secondary" to={`/patient/appointments/${a.id}/reschedule`}>Reschedule</Button>}
                  {a.canCancel && <Button size="sm" variant="ghost" onClick={() => setCancelling(a)}>Cancel</Button>}
                  {!a.canCancel && !a.canReschedule && <Button size="sm" variant="ghost" to={`/patient/appointments/${a.id}`}>Details</Button>}
                </div>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
      </Card>
      <CancelAppointmentDialog appointment={cancelling} onClose={() => setCancelling(null)}
        onCancelled={() => { setCancelling(null); reload(); }} />
    </>
  );
}
