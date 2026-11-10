import { CalendarCheck, CalendarX, CalendarDays, ClipboardCheck, Droplet, IdCard, UserRound } from 'lucide-react';
import { Link } from 'react-router-dom';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import RecentNotifications from '../../components/RecentNotifications.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { getMyAppointmentSummary } from '../../services/appointmentService.js';
import { getMyMedicalHistory } from '../../services/clinicalService.js';
import { getMyPrescriptions } from '../../services/prescriptionService.js';
import { getMyProfile } from '../../services/patientService.js';
import { formatDateLong, hhmm } from '../../utils/dates.js';

/** Patient home: appointment summary, next visit, profile and notifications (all live data). */
export default function PatientDashboard() {
  const { user } = useAuth();
  const { data: profile, loading, error, reload } = useApi(getMyProfile);
  const summary = useApi(getMyAppointmentSummary);
  const history = useApi(getMyMedicalHistory);
  const recent = (history.data?.timeline || []).slice(0, 3);
  const latestRx = useApi(() => getMyPrescriptions({ size: 1 }));
  const rx = latestRx.data?.content?.[0];
  const s = summary.data;
  const firstName = user.fullName.split(' ')[0];

  return (
    <>
      <PageHeader title={`Welcome, ${firstName}`} description="Your health information in one place."
        actions={<Button to="/patient/doctors">Book an appointment</Button>} />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard icon={CalendarDays} label="Total appointments" value={s?.total} loading={summary.loading} />
        <StatCard icon={CalendarCheck} label="Upcoming" value={s?.upcoming} loading={summary.loading} />
        <StatCard icon={ClipboardCheck} label="Completed" value={s?.completed} loading={summary.loading} />
        <StatCard icon={CalendarX} label="Cancelled" value={s?.cancelled} loading={summary.loading} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Next appointment" actions={<Link to="/patient/appointments" className="text-sm font-medium text-brand-700 hover:underline">All appointments</Link>}>
            {summary.loading && <Spinner />}
            {summary.error && <ErrorState error={summary.error} onRetry={summary.reload} />}
            {s && !s.next && (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-slate-600">You have no upcoming appointments.</p>
                <Button to="/patient/doctors" size="sm">Find a doctor</Button>
              </div>
            )}
            {s?.next && (
              <Link to={`/patient/appointments/${s.next.id}`} className="block rounded-lg border border-brand-100 bg-brand-50/50 p-4 hover:bg-brand-50">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-slate-900">{s.next.doctorName}</p>
                    <p className="text-sm text-slate-600">{s.next.specialization} · {s.next.departmentName}</p>
                  </div>
                  <AppointmentStatusBadge status={s.next.status} needsReschedule={s.next.needsReschedule} />
                </div>
                <p className="mt-2 text-sm font-medium text-brand-800">
                  {formatDateLong(s.next.date)} at {hhmm(s.next.startTime)} · {s.next.referenceNo}
                </p>
              </Link>
            )}
          </Card>
          <Card title="Latest prescription" actions={<Link to="/patient/prescriptions" className="text-sm font-medium text-brand-700 hover:underline">All prescriptions</Link>}>
            {latestRx.loading && <Spinner />}
            {latestRx.data && !rx && <p className="text-sm text-slate-500">No prescriptions yet.</p>}
            {rx && (
              <Link to={`/patient/prescriptions/${rx.id}`} className="block rounded-lg border border-slate-200 p-3 text-sm hover:bg-slate-50">
                <p className="font-medium text-slate-900">{rx.prescriptionNo} · {rx.doctorName}</p>
                <p className="text-slate-600">{rx.medicines.join(', ')}</p>
              </Link>
            )}
          </Card>
          <Card title="Recent consultations" actions={<Link to="/patient/history" className="text-sm font-medium text-brand-700 hover:underline">Full history</Link>}>
            {history.loading && <Spinner />}
            {history.data && recent.length === 0 && <p className="text-sm text-slate-500">No consultation records yet.</p>}
            <ul className="divide-y divide-slate-100">
              {recent.map((e) => (
                <li key={e.consultationId} className="py-2 text-sm">
                  <p className="font-medium text-slate-900">{formatDateLong(e.date)} · {e.doctorName}</p>
                  <p className="text-slate-600">{e.diagnosis}</p>
                </li>
              ))}
            </ul>
          </Card>
          <RecentNotifications />
        </div>
        <Card title="Profile summary" actions={<Button to="/patient/profile" variant="secondary" size="sm">View profile</Button>}>
          {loading && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {profile && (
            <ul className="space-y-3 text-sm">
              {[
                [IdCard, 'Patient ID', profile.patientCode],
                [UserRound, 'Age', profile.age ?? '—'],
                [Droplet, 'Blood group', profile.bloodGroup || '—'],
              ].map(([Icon, label, value]) => (
                <li key={label} className="flex items-center gap-3">
                  <Icon className="h-4 w-4 text-brand-600" aria-hidden="true" />
                  <span className="text-slate-500">{label}</span>
                  <span className="ml-auto font-semibold text-slate-900">{value}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
