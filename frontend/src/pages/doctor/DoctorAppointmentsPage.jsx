import { useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, List } from 'lucide-react';
import { toast } from 'sonner';
import AppointmentRow from '../../components/appointments/AppointmentRow.jsx';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import useApi from '../../hooks/useApi.js';
import { markNoShow } from '../../services/appointmentService.js';
import { getDoctorAppointments } from '../../services/scheduleService.js';
import {
  addDaysIso, formatDateShort, hhmm, parseIsoDate, startOfWeekIso, todayIsoLocal,
} from '../../utils/dates.js';

const STATUSES = ['SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];

function canMarkNoShow(a) {
  const start = parseIsoDate(a.date);
  const [h, m] = a.startTime.split(':').map(Number);
  start.setHours(h, m);
  return (a.status === 'SCHEDULED' || a.status === 'CHECKED_IN') && start < new Date();
}

function WeekCalendar({ weekStart, appointments }) {
  const days = Array.from({ length: 7 }, (_, i) => addDaysIso(weekStart, i));
  const today = todayIsoLocal();
  return (
    <div className="-mx-5 overflow-x-auto px-5">
      <div className="grid min-w-[56rem] grid-cols-7 gap-2">
        {days.map((d) => {
          const items = appointments.filter((a) => a.date === d && a.status !== 'CANCELLED');
          return (
            <section key={d} aria-label={formatDateShort(d)}
              className={`min-h-40 rounded-lg border p-2 ${d === today ? 'border-brand-300 bg-brand-50/40' : 'border-slate-200 bg-white'}`}>
              <h3 className="mb-2 text-xs font-semibold text-slate-600">{formatDateShort(d)}</h3>
              {items.length === 0 && <p className="text-xs text-slate-300">—</p>}
              <ul className="space-y-1.5">
                {items.map((a) => (
                  <li key={a.id} className="rounded-md border border-slate-100 bg-white p-1.5 text-xs shadow-sm">
                    <p className="font-mono font-semibold text-slate-800">{hhmm(a.startTime)}</p>
                    <p className="truncate text-slate-700">{a.patientName}</p>
                    <AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} />
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    </div>
  );
}

/** Doctor's appointments: week calendar or filtered list, with no-show recording. */
export default function DoctorAppointmentsPage() {
  const today = todayIsoLocal();
  const [view, setView] = useState('week');
  const [weekStart, setWeekStart] = useState(startOfWeekIso(today));
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(addDaysIso(today, 13));
  const [status, setStatus] = useState('');
  const [noShow, setNoShow] = useState(null);
  const [busy, setBusy] = useState(false);

  const range = view === 'week' ? { from: weekStart, to: addDaysIso(weekStart, 6) } : { from, to, status };
  const { data, loading, error, reload } = useApi(() => getDoctorAppointments(range),
    [view, weekStart, from, to, status]);
  const rows = data?.content || [];

  async function confirmNoShow() {
    setBusy(true);
    try {
      await markNoShow(noShow.id);
      toast.success('Recorded as no-show');
      setNoShow(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="My appointments" description="Week calendar and filtered list of your bookings." />
      <Card>
        <Tabs label="View" value={view} onChange={setView} tabs={[
          { value: 'week', label: 'Week' },
          { value: 'list', label: 'List' },
        ]} />
        {view === 'week' ? (
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <Button variant="secondary" size="sm" onClick={() => setWeekStart(addDaysIso(weekStart, -7))} aria-label="Previous week">
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setWeekStart(startOfWeekIso(today))}>This week</Button>
            <Button variant="secondary" size="sm" onClick={() => setWeekStart(addDaysIso(weekStart, 7))} aria-label="Next week">
              <ChevronRight className="h-4 w-4" />
            </Button>
            <span className="text-sm font-medium text-slate-700">
              {formatDateShort(weekStart)} – {formatDateShort(addDaysIso(weekStart, 6))}
            </span>
          </div>
        ) : (
          <div className="mb-4 grid gap-3 sm:grid-cols-3">
            <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            <TextField label="To" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
            <SelectField label="Status" placeholder="All statuses" value={status}
              options={STATUSES.map((s) => ({ value: s, label: s.replace('_', ' ').toLowerCase() }))}
              onChange={(e) => setStatus(e.target.value)} />
          </div>
        )}

        {loading && !data && <Spinner />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && view === 'week' && <WeekCalendar weekStart={weekStart} appointments={rows} />}
        {data && view === 'list' && rows.length === 0 && (
          <EmptyState icon={view === 'list' ? List : CalendarDays} title="No appointments in this range" />
        )}
        {data && view === 'list' && rows.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {rows.map((a) => (
              <AppointmentRow key={a.id} a={a} showDate actions={canMarkNoShow(a) && (
                <Button size="sm" variant="ghost" onClick={() => setNoShow(a)}>Mark no-show</Button>
              )} />
            ))}
          </ul>
        )}
      </Card>
      <ConfirmDialog open={!!noShow} title="Record a no-show?"
        message={noShow ? `${noShow.patientName} did not attend the ${hhmm(noShow.startTime)} appointment on ${formatDateShort(noShow.date)}.` : ''}
        confirmLabel="Mark no-show" loading={busy} onCancel={() => setNoShow(null)} onConfirm={confirmNoShow} />
    </>
  );
}
