import { CalendarCheck, CalendarX, ClipboardCheck, Hourglass, Stethoscope, UserPlus, UserX } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import useApi from '../../hooks/useApi.js';
import { getReceptionDashboard } from '../../services/receptionService.js';
import { formatDateLong } from '../../utils/dates.js';

/** Front-desk overview for today (live counts). */
export default function ReceptionDashboard() {
  const { data, loading, error, reload } = useApi(getReceptionDashboard);
  return (
    <>
      <PageHeader title="Reception desk" description={data ? formatDateLong(data.date) : 'Today'}
        actions={(
          <>
            <Button to="/reception/patients/new" variant="secondary"><UserPlus className="h-4 w-4" aria-hidden="true" /> Register walk-in</Button>
            <Button to="/reception/book">Book appointment</Button>
          </>
        )} />
      {error && <ErrorState error={error} onRetry={reload} />}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard icon={CalendarCheck} label="Appointments today" value={data?.totalAppointments} loading={loading} />
        <StatCard icon={Hourglass} label="Waiting (checked in)" value={data?.waiting} loading={loading} tone="amber" />
        <StatCard icon={Stethoscope} label="In consultation" value={data?.inConsultation} loading={loading} />
        <StatCard icon={ClipboardCheck} label="Completed" value={data?.completed} loading={loading} />
        <StatCard icon={CalendarCheck} label="Not yet arrived" value={data?.scheduled} loading={loading} tone="slate" />
        <StatCard icon={CalendarX} label="Cancelled" value={data?.cancelled} loading={loading} tone="slate" />
        <StatCard icon={UserX} label="No-shows" value={data?.noShow} loading={loading} tone="red" />
        <StatCard icon={UserPlus} label="Walk-ins / new patients" value={data ? `${data.walkIns} / ${data.patientsRegisteredToday}` : undefined} loading={loading} />
      </div>
      <div className="mt-6">
        <Card title="Doctors today" actions={<Button to="/reception/queue" size="sm" variant="secondary">Open queue board</Button>}>
          {loading && !data && <Spinner />}
          {data && data.doctors.length === 0 && <EmptyState title="No appointments today" />}
          {data && data.doctors.length > 0 && (
            <div className="-mx-5 overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-2">Doctor</th>
                    <th scope="col" className="px-3 py-2">Appointments</th>
                    <th scope="col" className="px-3 py-2">Waiting</th>
                    <th scope="col" className="px-3 py-2">In consultation</th>
                    <th scope="col" className="px-3 py-2 pr-5">Completed</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.doctors.map((d) => (
                    <tr key={d.doctorId}>
                      <td className="px-5 py-2 font-medium text-slate-900">{d.doctorName}</td>
                      <td className="px-3 py-2">{d.appointments}</td>
                      <td className="px-3 py-2">{d.waiting}</td>
                      <td className="px-3 py-2">{d.inConsultation}</td>
                      <td className="px-3 py-2 pr-5">{d.completed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
