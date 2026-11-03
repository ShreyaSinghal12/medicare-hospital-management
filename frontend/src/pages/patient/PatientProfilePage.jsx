import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Pencil } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { getMyProfile, updateMyProfile } from '../../services/patientService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import {
  BLOOD_GROUPS, GENDERS, NAME_PATTERN, PHONE_PATTERN, todayIso,
} from '../../utils/validation.js';

const GENDER_LABEL = Object.fromEntries(GENDERS.map((g) => [g.value, g.label]));

function toForm(p) {
  return {
    fullName: p.fullName, phone: p.phone || '', dateOfBirth: p.dateOfBirth || '', gender: p.gender || '',
    bloodGroup: p.bloodGroup || '', address: p.address || '', emergencyContactName: p.emergencyContactName || '',
    emergencyContactPhone: p.emergencyContactPhone || '',
  };
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-sm text-slate-900">{value || '—'}</dd>
    </div>
  );
}

export default function PatientProfilePage() {
  const { refresh } = useAuth();
  const { data: profile, loading, error, reload } = useApi(getMyProfile);
  const [editing, setEditing] = useState(false);
  const {
    register, handleSubmit, reset, setError,
    formState: { errors, isSubmitting },
  } = useForm();

  useEffect(() => {
    if (profile) reset(toForm(profile));
  }, [profile, reset]);

  async function onSubmit(values) {
    try {
      await updateMyProfile({
        ...values,
        gender: values.gender || null,
        bloodGroup: values.bloodGroup || null,
      });
      toast.success('Profile updated');
      setEditing(false);
      reload();
      refresh();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  if (loading && !profile) return <Spinner label="Loading profile…" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <PageHeader
        title="My profile"
        description="Your personal and contact information. Clinical records are maintained by your doctors."
        actions={!editing && (
          <Button onClick={() => setEditing(true)}>
            <Pencil className="h-4 w-4" aria-hidden="true" /> Edit profile
          </Button>
        )}
      />

      {!editing ? (
        <Card title={profile.fullName} subtitle={`Patient ID ${profile.patientCode}`}>
          <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <Detail label="E-mail" value={profile.email} />
            <Detail label="Phone" value={profile.phone} />
            <Detail label="Date of birth" value={profile.dateOfBirth && `${profile.dateOfBirth} (${profile.age} years)`} />
            <Detail label="Gender" value={GENDER_LABEL[profile.gender]} />
            <Detail label="Blood group" value={profile.bloodGroup} />
            <Detail label="Address" value={profile.address} />
            <Detail label="Emergency contact" value={profile.emergencyContactName} />
            <Detail label="Emergency phone" value={profile.emergencyContactPhone} />
          </dl>
        </Card>
      ) : (
        <Card title="Edit profile" subtitle="Your e-mail address can't be changed here.">
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
            <TextField label="Full name" required error={errors.fullName?.message}
              {...register('fullName', {
                required: 'Full name is required',
                pattern: { value: NAME_PATTERN, message: 'Use letters, spaces, dots, apostrophes or hyphens (2–120)' },
              })} />
            <TextField label="Phone" type="tel" required error={errors.phone?.message}
              {...register('phone', {
                required: 'Phone is required',
                pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' },
              })} />
            <TextField label="Date of birth" type="date" required max={todayIso()} error={errors.dateOfBirth?.message}
              {...register('dateOfBirth', {
                required: 'Date of birth is required',
                validate: (v) => v < todayIso() || 'Date of birth must be in the past',
              })} />
            <SelectField label="Gender" placeholder="Not specified" options={GENDERS} {...register('gender')} />
            <SelectField label="Blood group" placeholder="Not specified"
              options={BLOOD_GROUPS.map((b) => ({ value: b, label: b }))} {...register('bloodGroup')} />
            <div className="sm:col-span-2">
              <TextAreaField label="Address" rows={2} error={errors.address?.message}
                {...register('address', { maxLength: { value: 500, message: 'Maximum 500 characters' } })} />
            </div>
            <TextField label="Emergency contact name" error={errors.emergencyContactName?.message}
              {...register('emergencyContactName', { maxLength: { value: 120, message: 'Maximum 120 characters' } })} />
            <TextField label="Emergency contact phone" type="tel" error={errors.emergencyContactPhone?.message}
              {...register('emergencyContactPhone', {
                validate: (v) => !v || PHONE_PATTERN.test(v) || 'Enter a valid phone number',
              })} />
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" loading={isSubmitting}>Save profile</Button>
              <Button variant="secondary" onClick={() => { reset(toForm(profile)); setEditing(false); }}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      )}
    </>
  );
}
