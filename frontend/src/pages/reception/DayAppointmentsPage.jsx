import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, ChevronLeft, ChevronRight, Printer } from 'lucide-react';
import { toast } from 'sonner';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import CancelAppointmentDialog from '../../components/appointments/CancelAppointmentDialog.jsx';
import { TokenChip } from '../../components/reception/QueueStatusBadge.jsx';
import RescheduleDialog from '../../components/reception/RescheduleDialog.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { markNoShow } from '../../services/appointmentService.js';
import { searchDoctors } from '../../services/doctorService.js';
import { checkIn, getDayAppointments } from '../../services/receptionService.js';
import { addDaysIso, formatDateLong, hhmm, parseIsoDate, todayIsoLocal } from '../../utils/dates.js';

function started(a) {
  const d = parseIsoDate(a.date);
  const [h, m] = a.startTime.split(':').map(Number);
  d.setHours(h, m);
  return d < new Date();
}

/** Daily calendar for the front desk with check-in, reschedule, cancel, no-show and print. */
export default function DayAppointmentsPage() {
  const today = todayIsoLocal();
  const [date, setDate] = useState(today);
  const [doctorId, setDoctorId] = useState('');
  const [status, setStatus] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [cancelling, setCancelling] = useState(null);
  const [moving, setMoving] = useState(null);
  const [noShow, setNoShow] = useState(null);
  const doctors = useApi(() => searchDoctors({ size: 50 }));
  const { data, loading, error, reload } = useApi(() => getDayAppointments({ date, doctorId, status }), [date, doctorId, status]);
  const rows = data || [];

  async function doCheckIn(a) {
    setBusyId(a.id);
    try {
      const res = await checkIn(a.id);
      toast.success(`${a.patientName} checked in · token ${res.queueToken}`);
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusyId(null);
    }
  }

  async function confirmNoShow() {
    try {
      await markNoShow(noShow.id);
      toast.success('Recorded as no-show');
      setNoShow(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  }

  return (
    <>
      <PageHeader title="Appointments" description={formatDateLong(date)} actions={<Button to="/reception/book">Book appointment</Button>} />
      <Card>
        <div className="grid gap-3 sm:grid-cols-[auto_12rem_1fr_12rem] sm:items-end">
          <div className="flex gap-1">
            <Button variant="secondary" size="sm" aria-label="Previous day" onClick={() => setDate(addDaysIso(date, -1))}><ChevronLeft className="h-4 w-4" /></Button>
            <Button variant="secondary" size="sm" onClick={() => setDate(today)}>Today</Button>
            <Button variant="secondary" size="sm" aria-label="Next day" onClick={() => setDate(addDaysIso(date, 1))}><ChevronRight className="h-4 w-4" /></Button>
          </div>
          <TextField label="Date" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
          <SelectField label="Doctor" placeholder="All doctors" value={doctorId}
            options={(doctors.data?.content || []).map((d) => ({ value: String(d.id), label: d.fullName }))} onChange={(e) => setDoctorId(e.target.value)} />
          <SelectField label="Status" placeholder="All statuses" value={status}
            options={['SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'].map((s) => ({ value: s, label: s.replace('_', ' ').toLowerCase() }))}
            onChange={(e) => setStatus(e.target.value)} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={CalendarDays} title="No appointments" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="w-14 font-mono text-sm font-semibold text-slate-800">{hhmm(a.startTime)}</span>
                  {a.queueToken ? <TokenChip token={a.queueToken} status={a.queueStatus} /> : <span className="w-9" />}
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{a.patientName} <span className="text-sm font-normal text-slate-500">· {a.patientCode}</span></p>
                    <p className="text-sm text-slate-500">{a.doctorName} · {a.referenceNo}{a.type === 'WALK_IN' ? ' · walk-in' : ''}</p>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} />
                  {a.status === 'SCHEDULED' && a.date === today && (
                    <Button size="sm" onClick={() => doCheckIn(a)} loading={busyId === a.id}>Check in</Button>
                  )}
                  {a.canReschedule && <Button size="sm" variant="secondary" onClick={() => setMoving(a)}>Reschedule</Button>}
                  {a.canCancel && <Button size="sm" variant="ghost" onClick={() => setCancelling(a)}>Cancel</Button>}
                  {(a.status === 'SCHEDULED' || a.status === 'CHECKED_IN') && started(a) && (
                    <Button size="sm" variant="ghost" onClick={() => setNoShow(a)}>No-show</Button>
                  )}
                  <Link to={`/reception/appointments/${a.id}/print`} aria-label={`Print confirmation for ${a.patientName}`}
                    className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100"><Printer className="h-4 w-4" /></Link>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </Card>
      <CancelAppointmentDialog appointment={cancelling} onClose={() => setCancelling(null)} onCancelled={() => { setCancelling(null); reload(); }} />
      <RescheduleDialog appointment={moving} onClose={() => setMoving(null)} onDone={() => { setMoving(null); reload(); }} />
      <ConfirmDialog open={!!noShow} title="Record a no-show?" message={noShow ? `${noShow.patientName} did not attend at ${hhmm(noShow.startTime)}.` : ''}
        confirmLabel="Mark no-show" onCancel={() => setNoShow(null)} onConfirm={confirmNoShow} />
    </>
  );
}
