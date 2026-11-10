import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import PatientPicker from '../../components/reception/PatientPicker.jsx';
import SlotPicker from '../../components/scheduling/SlotPicker.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import { SelectField, TextAreaField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import useApi from '../../hooks/useApi.js';
import { bookAppointment } from '../../services/appointmentService.js';
import { searchDoctors } from '../../services/doctorService.js';
import { getReceptionPatient } from '../../services/receptionService.js';
import { todayIsoLocal } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

/** Front-desk booking for a selected patient (uses the same concurrency-safe booking API). */
export default function ReceptionBookPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const preselected = useApi(() => (params.get('patientId') ? getReceptionPatient(params.get('patientId')) : Promise.resolve(null)), []);
  const [patient, setPatient] = useState(null);
  const current = patient || preselected.data;
  const doctors = useApi(() => searchDoctors({ size: 50 }));
  const [doctorId, setDoctorId] = useState('');
  const [slot, setSlot] = useState(null);
  const [walkIn, setWalkIn] = useState(true);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const doctor = (doctors.data?.content || []).find((d) => String(d.id) === doctorId);

  async function submit() {
    if (reason.trim().length < 3) {
      setReasonError('Enter the reason the patient gives (at least 3 characters)');
      return;
    }
    setBusy(true);
    try {
      const a = await bookAppointment({
        doctorId: Number(doctorId), patientId: current.id, date: slot.date, startTime: slot.startTime,
        reason: reason.trim(), walkIn: walkIn && slot.date === todayIsoLocal(),
      });
      toast.success(`Booked ${a.referenceNo}`);
      navigate(`/reception/appointments/${a.id}/print`);
    } catch (err) {
      toast.error(err.message);
      if (err.code === 'SLOT_ALREADY_BOOKED') {
        setSlot(null);
        setRefresh((r) => r + 1);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Book appointment" description="Book on behalf of a patient at the desk or on the phone." />
      <div className="space-y-6">
        <Card title="1. Patient">
          {current ? (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm"><strong>{current.fullName}</strong> · {current.patientCode} · {current.phone}</p>
              <Button variant="ghost" size="sm" onClick={() => { setPatient(null); preselected.reload(); navigate('/reception/book', { replace: true }); }}>
                <X className="h-4 w-4" aria-hidden="true" /> Change
              </Button>
            </div>
          ) : <PatientPicker onPick={setPatient} />}
        </Card>
        <Card title="2. Doctor and time">
          <div className="max-w-md">
            <SelectField label="Doctor" placeholder="Select doctor" value={doctorId}
              options={(doctors.data?.content || []).map((d) => ({ value: String(d.id), label: `${d.fullName} · ${d.departmentName}` }))}
              onChange={(e) => { setDoctorId(e.target.value); setSlot(null); }} />
          </div>
          {doctor && <p className="mt-2 text-sm text-slate-500">{doctor.specialization} · fee {formatCurrency(doctor.consultationFee)} · {doctor.slotDurationMinutes} min</p>}
          {doctorId && <div className="mt-4"><SlotPicker key={doctorId} doctorId={doctorId} value={slot} onChange={setSlot} refreshKey={refresh} /></div>}
        </Card>
        <Card title="3. Confirm">
          <TextAreaField label="Reason for visit (as told by the patient)" required rows={2} value={reason} error={reasonError} maxLength={500}
            hint="Visible to the doctor only; reception will not see it again after booking."
            onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
          <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={walkIn} onChange={(e) => setWalkIn(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-brand-600" />
            Walk-in (patient is at the hospital now; applies to today's bookings)
          </label>
          <Button className="mt-4" onClick={submit} loading={busy} disabled={!current || !slot}>
            {!current ? 'Select a patient' : !slot ? 'Choose a time' : `Book ${slot.date} ${slot.startTime}`}
          </Button>
        </Card>
      </div>
    </>
  );
}
