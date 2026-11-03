import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import { SelectField, TextAreaField, TextField } from '../../components/ui/FormField.jsx';
import PasswordChecklist from '../../components/ui/PasswordChecklist.jsx';
import { registerPatient } from '../../services/authService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import {
  BLOOD_GROUPS, EMAIL_PATTERN, GENDERS, NAME_PATTERN, PHONE_PATTERN, isStrongPassword, todayIso,
} from '../../utils/validation.js';
import AuthCard from './AuthCard.jsx';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState(null);
  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    defaultValues: {
      fullName: '', email: '', phone: '', dateOfBirth: '', gender: '', bloodGroup: '', address: '',
      emergencyContactName: '', emergencyContactPhone: '', password: '', confirmPassword: '', consent: false,
    },
  });
  const password = watch('password');

  async function onSubmit(values) {
    setFormError(null);
    const { consent, ...payload } = values;
    void consent;
    try {
      const profile = await registerPatient({
        ...payload,
        email: payload.email.trim(),
        gender: payload.gender || null,
        bloodGroup: payload.bloodGroup || null,
      });
      toast.success(`Account created. Your patient ID is ${profile.patientCode}. Please log in.`);
      navigate(`/login?email=${encodeURIComponent(profile.email)}`, { replace: true });
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) {
        if (err.code === 'EMAIL_ALREADY_REGISTERED') setError('email', { type: 'server', message: err.message });
        else setFormError(err.message);
      }
    }
  }

  return (
    <AuthCard
      wide
      title="Create your patient account"
      subtitle="Register to book appointments and access your records online."
      footer={
        <>
          Already registered? <Link to="/login" className="font-medium text-brand-700 hover:underline">Log in</Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
        {formError && (
          <div role="alert" className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {formError}
          </div>
        )}

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-900">Personal details</legend>
          <div className="sm:col-span-2">
            <TextField
              label="Full name" required autoComplete="name" error={errors.fullName?.message}
              {...register('fullName', {
                required: 'Full name is required',
                pattern: { value: NAME_PATTERN, message: 'Use letters, spaces, dots, apostrophes or hyphens (2–120)' },
              })}
            />
          </div>
          <TextField
            label="Date of birth" type="date" required max={todayIso()} error={errors.dateOfBirth?.message}
            {...register('dateOfBirth', {
              required: 'Date of birth is required',
              validate: (v) => v < todayIso() || 'Date of birth must be in the past',
            })}
          />
          <SelectField label="Gender" placeholder="Select (optional)" options={GENDERS} {...register('gender')} />
          <SelectField
            label="Blood group" placeholder="Select (optional)"
            options={BLOOD_GROUPS.map((b) => ({ value: b, label: b }))} {...register('bloodGroup')}
          />
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-900">Contact</legend>
          <TextField
            label="E-mail address" type="email" required autoComplete="email" error={errors.email?.message}
            {...register('email', {
              required: 'E-mail is required',
              pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' },
            })}
          />
          <TextField
            label="Phone number" type="tel" required autoComplete="tel" placeholder="+91 98765 43210"
            error={errors.phone?.message}
            {...register('phone', {
              required: 'Phone number is required',
              pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' },
            })}
          />
          <div className="sm:col-span-2">
            <TextAreaField label="Address" rows={2} autoComplete="street-address" error={errors.address?.message}
              {...register('address', { maxLength: { value: 500, message: 'Maximum 500 characters' } })} />
          </div>
          <TextField label="Emergency contact name" error={errors.emergencyContactName?.message}
            {...register('emergencyContactName', { maxLength: { value: 120, message: 'Maximum 120 characters' } })} />
          <TextField
            label="Emergency contact phone" type="tel" error={errors.emergencyContactPhone?.message}
            {...register('emergencyContactPhone', {
              validate: (v) => !v || PHONE_PATTERN.test(v) || 'Enter a valid phone number',
            })}
          />
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-semibold text-slate-900">Password</legend>
          <div>
            <TextField
              label="Password" type="password" required autoComplete="new-password" error={errors.password?.message}
              {...register('password', { required: 'Password is required', validate: isStrongPassword })}
            />
            <PasswordChecklist value={password} />
          </div>
          <TextField
            label="Confirm password" type="password" required autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword', {
              required: 'Please confirm your password',
              validate: (v) => v === password || 'Passwords do not match',
            })}
          />
        </fieldset>

        <label className="flex items-start gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            aria-invalid={!!errors.consent}
            {...register('consent', { required: 'Please confirm to continue' })}
          />
          <span>
            I understand this is an academic prototype and I will enter only fictional test data.
            {errors.consent && <span role="alert" className="block text-xs font-medium text-red-600">{errors.consent.message}</span>}
          </span>
        </label>

        <Button type="submit" className="w-full" loading={isSubmitting}>
          {isSubmitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthCard>
  );
}
