import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Pencil } from 'lucide-react';
import { toast } from 'sonner';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getPatientAppointments, getReceptionPatient, updatePatientContact } from '../../services/receptionService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { formatDateShort, hhmm } from '../../utils/dates.js';
import { PHONE_PATTERN } from '../../utils/validation.js';

function ContactForm({ patient, onSaved, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { phone: patient.phone || '', address: patient.address || '', emergencyContactName: patient.emergencyContactName || '', emergencyContactPhone: patient.emergencyContactPhone || '' },
  });
  async function onSubmit(v) {
    try {
      await updatePatientContact(patient.id, v);
      toast.success('Contact details updated');
      onSaved();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-3 sm:grid-cols-2">
      <TextField label="Phone" type="tel" required error={errors.phone?.message}
        {...register('phone', { required: 'Required', pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' } })} />
      <TextField label="Address" {...register('address', { maxLength: { value: 500, message: 'Max 500' } })} />
      <TextField label="Emergency contact name" {...register('emergencyContactName', { maxLength: { value: 120, message: 'Max 120' } })} />
      <TextField label="Emergency contact phone" type="tel" error={errors.emergencyContactPhone?.message}
        {...register('emergencyContactPhone', { validate: (v) => !v || PHONE_PATTERN.test(v) || 'Enter a valid phone number' })} />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>Save</Button>
      </div>
    </form>
  );
}

export default function ReceptionPatientPage() {
  const { id } = useParams();
  const { data: p, loading, error, reload } = useApi(() => getReceptionPatient(id), [id]);
  const appts = useApi(() => getPatientAppointments(id), [id]);
  const [editing, setEditing] = useState(false);
  if (loading && !p) return <Spinner />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <>
      <Link to="/reception/patients" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Patient directory
      </Link>
      <PageHeader title={p.fullName} description={`${p.patientCode} · ${p.age} years`}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button to={`/reception/billing/new?patientId=${p.id}`} variant="secondary">Create invoice</Button>
            <Button to={`/reception/book?patientId=${p.id}`}>Book appointment</Button>
          </div>
        )} />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Contact" actions={<Button size="sm" variant="secondary" onClick={() => setEditing(true)}><Pencil className="h-4 w-4" aria-hidden="true" /> Edit</Button>}>
          <dl className="space-y-2 text-sm">
            {[['E-mail', p.email], ['Phone', p.phone], ['Date of birth', p.dateOfBirth], ['Address', p.address],
              ['Emergency contact', [p.emergencyContactName, p.emergencyContactPhone].filter(Boolean).join(' · ')]].map(([k, v]) => (
              <div key={k}><dt className="text-slate-500">{k}</dt><dd className="font-medium text-slate-900">{v || '—'}</dd></div>
            ))}
          </dl>
          <p className="mt-3 text-xs text-slate-500">Name or date-of-birth corrections are made by an administrator.</p>
        </Card>
        <div className="lg:col-span-2">
          <Card title="Appointments">
            {appts.loading && <Spinner />}
            {appts.data && appts.data.length === 0 && <p className="text-sm text-slate-500">No appointments yet.</p>}
            <ul className="divide-y divide-slate-100">
              {(appts.data || []).map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <span>{formatDateShort(a.date)} {hhmm(a.startTime)} · {a.doctorName} · {a.referenceNo}</span>
                  <span className="flex items-center gap-2">
                    <AppointmentStatusBadge status={a.status} needsReschedule={a.needsReschedule} />
                    <Link to={`/reception/appointments/${a.id}/print`} className="text-xs font-medium text-brand-700 hover:underline">Print</Link>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
      <Modal open={editing} title="Edit contact details" onClose={() => setEditing(false)}>
        {editing && <ContactForm patient={p} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); reload(); }} />}
      </Modal>
    </>
  );
}
