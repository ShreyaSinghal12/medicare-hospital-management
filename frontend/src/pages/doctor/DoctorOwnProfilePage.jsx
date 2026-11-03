import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getOwnDoctorProfile, updateOwnDoctorProfile } from '../../services/doctorService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { formatCurrency } from '../../utils/format.js';
import { PHONE_PATTERN } from '../../utils/validation.js';

export default function DoctorOwnProfilePage() {
  const { data: doctor, loading, error, reload } = useApi(getOwnDoctorProfile);
  const {
    register, handleSubmit, reset, setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm();

  useEffect(() => {
    if (doctor) reset({ phone: doctor.phone || '', qualifications: doctor.qualifications, bio: doctor.bio || '' });
  }, [doctor, reset]);

  async function onSubmit(values) {
    try {
      await updateOwnDoctorProfile(values);
      toast.success('Profile updated');
      reload();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  if (loading && !doctor) return <Spinner label="Loading profile…" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;

  return (
    <>
      <PageHeader title="My profile" description="Your public profile as patients see it in the doctor directory." />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Managed by administration" subtitle="Contact an administrator to change these.">
          <dl className="space-y-3 text-sm">
            {[
              ['Department', doctor.departmentName],
              ['Specialization', doctor.specialization],
              ['Licence number', doctor.licenseNumber],
              ['Experience', `${doctor.experienceYears} years`],
              ['Consultation fee', formatCurrency(doctor.consultationFee)],
              ['Consultation length', `${doctor.slotDurationMinutes} minutes`],
            ].map(([k, v]) => (
              <div key={k} className="flex justify-between gap-3"><dt className="text-slate-500">{k}</dt><dd className="text-right font-medium text-slate-900">{v}</dd></div>
            ))}
          </dl>
        </Card>
        <div className="lg:col-span-2">
          <Card title="Editable details">
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
              <TextField label="Phone" type="tel" required error={errors.phone?.message}
                {...register('phone', { required: 'Phone is required', pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' } })} />
              <TextField label="Qualifications" required error={errors.qualifications?.message}
                {...register('qualifications', { required: 'Qualifications are required', maxLength: { value: 255, message: 'Max 255 characters' } })} />
              <TextAreaField label="Biography" rows={5} hint="Shown on your public profile." error={errors.bio?.message}
                {...register('bio', { maxLength: { value: 2000, message: 'Max 2000 characters' } })} />
              <Button type="submit" loading={isSubmitting} disabled={!isDirty}>Save changes</Button>
            </form>
          </Card>
        </div>
      </div>
    </>
  );
}
