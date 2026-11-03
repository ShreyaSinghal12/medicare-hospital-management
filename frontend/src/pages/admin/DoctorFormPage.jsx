import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import {
  adminCreateDoctor, adminGetDoctor, adminListDepartments, adminUpdateDoctor,
} from '../../services/adminService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { EMAIL_PATTERN, NAME_PATTERN, PHONE_PATTERN } from '../../utils/validation.js';

const EMPTY = {
  fullName: '', email: '', phone: '', departmentId: '', specialization: '', qualifications: '',
  licenseNumber: '', experienceYears: 0, consultationFee: '', slotDurationMinutes: 15, bio: '',
};

const SLOT_OPTIONS = [10, 15, 20, 30, 45, 60].map((m) => ({ value: String(m), label: `${m} minutes` }));

/** Create (/admin/doctors/new) or edit (/admin/doctors/:id/edit) a doctor. */
export default function DoctorFormPage() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const { data: departments } = useApi(adminListDepartments);
  const doctorQuery = useApi(() => (editing ? adminGetDoctor(id) : Promise.resolve(null)), [id]);

  const {
    register, handleSubmit, reset, setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: EMPTY });

  useEffect(() => {
    const d = doctorQuery.data;
    if (d) {
      reset({
        fullName: d.fullName, email: d.email, phone: d.phone || '', departmentId: String(d.departmentId),
        specialization: d.specialization, qualifications: d.qualifications, licenseNumber: d.licenseNumber,
        experienceYears: d.experienceYears, consultationFee: d.consultationFee,
        slotDurationMinutes: String(d.slotDurationMinutes), bio: d.bio || '',
      });
    }
  }, [doctorQuery.data, reset]);

  async function onSubmit(values) {
    const payload = {
      ...values,
      email: editing ? undefined : values.email.trim(),
      departmentId: Number(values.departmentId),
      experienceYears: Number(values.experienceYears),
      consultationFee: Number(values.consultationFee),
      slotDurationMinutes: Number(values.slotDurationMinutes),
    };
    try {
      if (editing) {
        await adminUpdateDoctor(id, payload);
        toast.success('Doctor updated');
      } else {
        const created = await adminCreateDoctor(payload);
        toast.success(`${created.fullName} added. A "set your password" e-mail has been sent to ${created.email}.`);
      }
      navigate('/admin/doctors');
    } catch (err) {
      if (err.code === 'EMAIL_ALREADY_REGISTERED') setError('email', { type: 'server', message: err.message });
      else if (err.code === 'LICENSE_ALREADY_REGISTERED') setError('licenseNumber', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  if (editing && doctorQuery.loading) return <Spinner label="Loading doctor…" />;
  if (editing && doctorQuery.error) return <ErrorState error={doctorQuery.error} onRetry={doctorQuery.reload} />;

  const activeDepartments = (departments || []).filter(
    (d) => d.active || String(d.id) === String(doctorQuery.data?.departmentId),
  );

  return (
    <>
      <Link to="/admin/doctors" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back to doctors
      </Link>
      <PageHeader
        title={editing ? `Edit ${doctorQuery.data?.fullName || 'doctor'}` : 'Add doctor'}
        description={editing
          ? 'Changes to the consultation length apply to newly generated appointment slots only.'
          : 'The doctor receives an e-mail with a single-use link to set their own password.'}
      />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
        <Card title="Account">
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Full name" required placeholder="Dr. Firstname Lastname" error={errors.fullName?.message}
              {...register('fullName', {
                required: 'Full name is required',
                pattern: { value: NAME_PATTERN, message: 'Use letters, spaces, dots, apostrophes or hyphens' },
              })} />
            <TextField label="E-mail" type="email" required={!editing} disabled={editing}
              hint={editing ? 'E-mail addresses cannot be changed.' : undefined} error={errors.email?.message}
              {...register('email', editing ? {} : {
                required: 'E-mail is required',
                pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' },
              })} />
            <TextField label="Phone" type="tel" required error={errors.phone?.message}
              {...register('phone', {
                required: 'Phone is required',
                pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' },
              })} />
          </div>
        </Card>

        <Card title="Professional details">
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField label="Department" required placeholder="Select department" error={errors.departmentId?.message}
              options={activeDepartments.map((d) => ({ value: String(d.id), label: d.name }))}
              {...register('departmentId', { required: 'Department is required' })} />
            <TextField label="Specialization" required placeholder="e.g. Cardiologist" error={errors.specialization?.message}
              {...register('specialization', { required: 'Specialization is required', maxLength: { value: 100, message: 'Max 100 characters' } })} />
            <TextField label="Qualifications" required placeholder="e.g. MBBS, MD" error={errors.qualifications?.message}
              {...register('qualifications', { required: 'Qualifications are required', maxLength: { value: 255, message: 'Max 255 characters' } })} />
            <TextField label="Medical licence number" required error={errors.licenseNumber?.message}
              {...register('licenseNumber', {
                required: 'Licence number is required',
                pattern: { value: /^[A-Za-z0-9/-]{1,50}$/, message: "Letters, digits, '/' and '-' only" },
              })} />
            <TextField label="Experience (years)" type="number" min={0} max={70} required error={errors.experienceYears?.message}
              {...register('experienceYears', {
                required: 'Experience is required',
                min: { value: 0, message: 'Cannot be negative' },
                max: { value: 70, message: 'Maximum 70 years' },
              })} />
            <TextField label="Consultation fee (₹)" type="number" step="0.01" min={0} required error={errors.consultationFee?.message}
              {...register('consultationFee', {
                required: 'Fee is required',
                min: { value: 0, message: 'Cannot be negative' },
                max: { value: 99999.99, message: 'Too large' },
                validate: (v) => /^\d+(\.\d{1,2})?$/.test(String(v)) || 'Use at most 2 decimal places',
              })} />
            <SelectField label="Consultation length" required options={SLOT_OPTIONS} error={errors.slotDurationMinutes?.message}
              {...register('slotDurationMinutes', { required: true })} />
            <div className="sm:col-span-2">
              <TextAreaField label="Biography" rows={4} hint="Shown on the public doctor profile." error={errors.bio?.message}
                {...register('bio', { maxLength: { value: 2000, message: 'Max 2000 characters' } })} />
            </div>
          </div>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" loading={isSubmitting}>{editing ? 'Save changes' : 'Create doctor'}</Button>
          <Button variant="secondary" to="/admin/doctors">Cancel</Button>
        </div>
      </form>
    </>
  );
}
