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
import { getHospitalSettings, updateHospitalSettings } from '../../services/adminInsightsService.js';
import { formatDateTime } from '../../utils/format.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';

const num = (min, max) => ({
  required: 'Required',
  min: { value: min, message: `At least ${min}` },
  max: { value: max, message: `At most ${max}` },
});

function SettingsForm({ settings, onSaved }) {
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting, isDirty } } = useForm({ defaultValues: settings });
  useEffect(() => reset(settings), [settings, reset]);

  async function onSubmit(v) {
    const payload = {
      ...v,
      defaultSlotMinutes: Number(v.defaultSlotMinutes), bookingWindowDays: Number(v.bookingWindowDays),
      cancellationWindowHours: Number(v.cancellationWindowHours), taxRatePercent: Number(v.taxRatePercent),
      reminderHoursBefore: Number(v.reminderHoursBefore), expiryWarningDays: Number(v.expiryWarningDays),
    };
    try {
      onSaved(await updateHospitalSettings(payload));
      toast.success('Settings saved');
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
      <Card title="Hospital" subtitle="Shown on the website, e-mails, prescriptions and invoices.">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <TextField label="Hospital name" required error={errors.hospitalName?.message}
              {...register('hospitalName', { required: 'Required', minLength: { value: 2, message: 'At least 2 characters' }, maxLength: { value: 150, message: 'Max 150' } })} />
          </div>
          <div className="sm:col-span-2">
            <TextAreaField label="Address" rows={2} error={errors.address?.message} {...register('address', { maxLength: { value: 500, message: 'Max 500' } })} />
          </div>
          <TextField label="Phone" error={errors.phone?.message}
            {...register('phone', { pattern: { value: /^$|^[+0-9 ()-]{6,30}$/, message: 'Enter a valid phone number' } })} />
          <TextField label="E-mail" type="email" error={errors.email?.message}
            {...register('email', { pattern: { value: /^$|^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: 'Enter a valid e-mail address' } })} />
          <TextField label="Time zone" value={settings.timeZone} disabled readOnly
            hint="Fixed at installation: changing it would shift every stored appointment time." />
        </div>
      </Card>
      <Card title="Appointments">
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Default slot length (minutes)" type="number" required error={errors.defaultSlotMinutes?.message}
            hint="Used for new doctors." {...register('defaultSlotMinutes', num(5, 120))} />
          <TextField label="Booking window (days ahead)" type="number" required error={errors.bookingWindowDays?.message}
            {...register('bookingWindowDays', num(1, 365))} />
          <TextField label="Online cancellation until (hours before)" type="number" required error={errors.cancellationWindowHours?.message}
            {...register('cancellationWindowHours', num(0, 168))} />
          <div className="sm:col-span-3">
            <TextAreaField label="Cancellation policy (shown to patients)" rows={2} error={errors.cancellationPolicy?.message}
              {...register('cancellationPolicy', { maxLength: { value: 1000, message: 'Max 1000' } })} />
          </div>
        </div>
      </Card>
      <Card title="Billing, reminders and inventory">
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Invoice number prefix" required error={errors.invoicePrefix?.message} hint="2–6 capital letters, used for new invoices."
            {...register('invoicePrefix', { required: 'Required', pattern: { value: /^[A-Z]{2,6}$/, message: '2–6 capital letters' } })} />
          <TextField label="Tax rate (%)" type="number" step="0.01" required error={errors.taxRatePercent?.message} hint="Applies to new invoices only."
            {...register('taxRatePercent', num(0, 100))} />
          <TextField label="Expiry warning (days)" type="number" required error={errors.expiryWarningDays?.message}
            {...register('expiryWarningDays', num(1, 365))} />
          <TextField label="Reminder lead time (hours)" type="number" required error={errors.reminderHoursBefore?.message}
            {...register('reminderHoursBefore', num(1, 168))} />
          <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-800 sm:col-span-2">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-brand-700" {...register('emailRemindersEnabled')} />
            Also e-mail reminders
          </label>
        </div>
      </Card>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>Save settings</Button>
        {settings.updatedAt && <span className="text-sm text-slate-500">Last changed {formatDateTime(settings.updatedAt)}</span>}
      </div>
    </form>
  );
}

/** Hospital-wide settings. Every change is recorded in the audit log with the fields that changed. */
export default function HospitalSettingsPage() {
  const { data, loading, error, reload } = useApi(getHospitalSettings);
  return (
    <>
      <PageHeader title="Hospital settings" description="Changes apply immediately and are recorded in the audit log." />
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && <SettingsForm settings={data} onSaved={() => reload()} />}
    </>
  );
}
