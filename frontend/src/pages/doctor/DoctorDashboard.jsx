import { CalendarDays, CalendarRange, ClipboardCheck, Hourglass } from 'lucide-react';
import { Link } from 'react-router-dom';
import AppointmentRow from '../../components/appointments/AppointmentRow.jsx';
import StartConsultationButton from '../../components/clinical/StartConsultationButton.jsx';
import RecentNotifications from '../../components/RecentNotifications.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { getDoctorDashboard } from '../../services/scheduleService.js';
import { getDoctorQueue } from '../../services/receptionService.js';
import QueueStatusBadge, { TokenChip } from '../../components/reception/QueueStatusBadge.jsx';
import { formatDateLong, todayIsoLocal } from '../../utils/dates.js';

/** Doctor home: today's list with consultation actions and workload counters (live data). */
export default function DoctorDashboard() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useApi(getDoctorDashboard);
  const queue = useApi(getDoctorQueue);
  const waiting = (queue.data || []).filter((q) => q.status === 'WAITING' || q.status === 'IN_CONSULTATION');

  return (
    <>
      <PageHeader title={`Good day, ${user.fullName}`} description={`${user.departmentName || ''} · ${formatDateLong(todayIsoLocal())}`} />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard icon={CalendarDays} label="Today" value={data?.todayTotal} loading={loading} />
        <StatCard icon={Hourglass} label="Pending today" value={data?.todayPending} loading={loading} tone="amber" />
        <StatCard icon={ClipboardCheck} label="Completed today" value={data?.todayCompleted} loading={loading} />
        <StatCard icon={CalendarRange} label="This week" value={data?.weekTotal} loading={loading}
          hint={data ? `${data.upcomingScheduled} scheduled after today` : undefined} tone="slate" />
      </div>
      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card title="Today's appointments" actions={<Link to="/doctor/appointments" className="text-sm font-medium text-brand-700 hover:underline">Calendar</Link>}>
            {loading && !data && <Spinner />}
            {error && <ErrorState error={error} onRetry={reload} />}
            {data && data.today.length === 0 && <EmptyState icon={CalendarDays} title="No appointments today" />}
            {data && data.today.length > 0 && (
              <ul className="divide-y divide-slate-100">{data.today.map((a) => <AppointmentRow key={a.id} a={a} actions={<StartConsultationButton appointment={a} />} />)}</ul>
            )}
          </Card>
        </div>
        <div className="space-y-6">
          <Card title="Queue" subtitle={queue.data ? `${(queue.data || []).filter((q) => q.status === 'WAITING').length} waiting` : undefined}>
            {queue.loading && <Spinner />}
            {queue.data && waiting.length === 0 && <p className="text-sm text-slate-500">No one waiting.</p>}
            <ul className="space-y-2">
              {waiting.map((q) => (
                <li key={q.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2"><TokenChip token={q.tokenNumber} status={q.status} /> {q.patientName}</span>
                  <QueueStatusBadge status={q.status} />
                </li>
              ))}
            </ul>
          </Card>
          <RecentNotifications />
        </div>
      </div>
    </>
  );
}
