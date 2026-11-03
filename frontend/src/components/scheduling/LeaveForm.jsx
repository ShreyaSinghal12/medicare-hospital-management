import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { todayIsoLocal } from '../../utils/dates.js';
import Button from '../ui/Button.jsx';
import { SelectField, TextField } from '../ui/FormField.jsx';

/** Leave / unavailability form. `submit(payload)` calls the doctor or admin endpoint. */
export default function LeaveForm({ submit, submitLabel = 'Submit request', onDone }) {
  const today = todayIsoLocal();
  const {
    register, handleSubmit, watch, reset, setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { leaveType: 'LEAVE', startDate: today, endDate: today, startTime: '', endTime: '', reason: '' } });
  const type = watch('leaveType');
  const start = watch('startDate');

  async function onSubmit(values) {
    const payload = {
      ...values,
      startTime: values.leaveType === 'UNAVAILABLE' ? values.startTime : null,
      endTime: values.leaveType === 'UNAVAILABLE' ? values.endTime : null,
    };
    try {
      const res = await submit(payload);
      const flagged = res.affectedAppointments;
      toast.success(res.status === 'PENDING' ? 'Request submitted for approval'
        : `Recorded${flagged ? `; ${flagged} appointment(s) flagged for rescheduling` : ''}`);
      reset({ ...values, reason: '' });
      onDone?.(res);
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <SelectField label="Type" options={[{ value: 'LEAVE', label: 'Leave (whole days)' }, { value: 'UNAVAILABLE', label: 'Unavailable (specific hours)' }]}
        {...register('leaveType')} />
      <TextField label="Reason (optional)" {...register('reason', { maxLength: { value: 500, message: 'Max 500 characters' } })} error={errors.reason?.message} />
      <TextField label="From date" type="date" min={today} required error={errors.startDate?.message}
        {...register('startDate', { required: 'Required', validate: (v) => v >= today || 'Cannot be in the past' })} />
      <TextField label="To date" type="date" min={start} required error={errors.endDate?.message}
        {...register('endDate', { required: 'Required', validate: (v) => v >= start || 'Must be on or after the start date' })} />
      {type === 'UNAVAILABLE' && (
        <>
          <TextField label="From time" type="time" required error={errors.startTime?.message}
            {...register('startTime', { required: 'Required' })} />
          <TextField label="To time" type="time" required error={errors.endTime?.message}
            {...register('endTime', { required: 'Required', validate: (v, all) => v > all.startTime || 'Must be after the start time' })} />
        </>
      )}
      <div className="sm:col-span-2">
        <Button type="submit" loading={isSubmitting}>{submitLabel}</Button>
      </div>
    </form>
  );
}
