import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Stethoscope } from 'lucide-react';
import { toast } from 'sonner';
import { getConsultationByAppointment, startConsultation } from '../../services/clinicalService.js';
import { todayIsoLocal } from '../../utils/dates.js';
import Button from '../ui/Button.jsx';

/** "Start / Continue consultation" for today's open appointments, "View notes" for completed ones. */
export default function StartConsultationButton({ appointment: a }) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const openToday = a.date === todayIsoLocal() && ['SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS'].includes(a.status);
  if (!openToday && a.status !== 'COMPLETED') return null;

  async function go() {
    setBusy(true);
    try {
      const c = a.status === 'COMPLETED' ? await getConsultationByAppointment(a.id) : await startConsultation(a.id);
      navigate(`/doctor/consultations/${c.id}`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Button size="sm" variant={a.status === 'COMPLETED' ? 'secondary' : 'primary'} onClick={go} loading={busy}>
      {a.status !== 'COMPLETED' && <Stethoscope className="h-4 w-4" aria-hidden="true" />}
      {a.status === 'COMPLETED' ? 'View notes' : a.status === 'IN_PROGRESS' ? 'Continue consultation' : 'Start consultation'}
    </Button>
  );
}
