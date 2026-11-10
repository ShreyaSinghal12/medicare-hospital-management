import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { CheckCircle2 } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import { registerWalkIn } from '../../services/receptionService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import {
  BLOOD_GROUPS, EMAIL_PATTERN, GENDERS, NAME_PATTERN, PHONE_PATTERN, todayIso,
} from '../../utils/validation.js';

/** Register a walk-in patient. No password is set here; the patient gets an activation e-mail. */
export default function RegisterWalkInPage() {
  const [created, setCreated] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { fullName: '', email: '', phone: '', dateOfBirth: '', gender: '', bloodGroup: '', address: '', emergencyContactName: '', emergencyContactPhone: '' },
  });

  async function onSubmit(v) {
    try {
      const p = await registerWalkIn({ ...v, email: v.email.trim(), gender: v.gender || null, bloodGroup: v.bloodGroup || null });
      toast.success(`Registered ${p.fullName} (${p.patientCode})`);
      setCreated(p);
      reset();
    } catch (err) {
      if (err.code === 'EMAIL_ALREADY_REGISTERED') setError('email', { type: 'server', message: 'A patient with this e-mail is already registered. Search the directory instead.' });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  if (created) {
    return (
      <>
        <PageHeader title="Patient registered" />
        <Card>
          <div className="flex items-start gap-3">
            <CheckCircle2 className="h-6 w-6 text-emerald-600" aria-hidden="true" />
            <div>
              <p className="font-semibold text-slate-900">{created.fullName} · {created.patientCode}</p>
              <p className="mt-1 text-sm text-slate-600">An e-mail with a link to set a password was sent to {created.email}.</p>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button to={`/reception/book?patientId=${created.id}`}>Book appointment</Button>
                <Button to={`/reception/patients/${created.id}`} variant="secondary">Open patient</Button>
                <Button variant="ghost" onClick={() => setCreated(null)}>Register another</Button>
              </div>
            </div>
          </div>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Register walk-in patient" description="The patient receives an e-mail link to choose their own password." />
      <Card>
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <TextField label="Full name" required error={errors.fullName?.message}
              {...register('fullName', { required: 'Required', pattern: { value: NAME_PATTERN, message: 'Letters, spaces, dots, apostrophes or hyphens' } })} />
          </div>
          <TextField label="E-mail" type="email" required error={errors.email?.message}
            {...register('email', { required: 'Required', pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' } })} />
          <TextField label="Phone" type="tel" required error={errors.phone?.message}
            {...register('phone', { required: 'Required', pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' } })} />
          <TextField label="Date of birth" type="date" required max={todayIso()} error={errors.dateOfBirth?.message}
            {...register('dateOfBirth', { required: 'Required', validate: (v) => v < todayIso() || 'Must be in the past' })} />
          <SelectField label="Gender" placeholder="Not specified" options={GENDERS} {...register('gender')} />
          <SelectField label="Blood group" placeholder="Not known" options={BLOOD_GROUPS.map((b) => ({ value: b, label: b }))} {...register('bloodGroup')} />
          <div className="sm:col-span-2">
            <TextAreaField label="Address" rows={2} {...register('address', { maxLength: { value: 500, message: 'Max 500' } })} />
          </div>
          <TextField label="Emergency contact name" {...register('emergencyContactName', { maxLength: { value: 120, message: 'Max 120' } })} />
          <TextField label="Emergency contact phone" type="tel" error={errors.emergencyContactPhone?.message}
            {...register('emergencyContactPhone', { validate: (v) => !v || PHONE_PATTERN.test(v) || 'Enter a valid phone number' })} />
          <div className="sm:col-span-2">
            <Button type="submit" loading={isSubmitting}>Register patient</Button>
          </div>
        </form>
      </Card>
    </>
  );
}
