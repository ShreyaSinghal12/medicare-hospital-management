import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { BellRing } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import { getReminderLog, getReminderSettings, runRemindersNow, updateReminderSettings } from '../../services/reminderService.js';
import { formatDateTime } from '../../utils/format.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';

function SettingsForm({ settings, onSaved }) {
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting, isDirty } } = useForm({ defaultValues: settings });
  useEffect(() => reset(settings), [settings, reset]);

  async function onSubmit(v) {
    try {
      const s = await updateReminderSettings({ emailRemindersEnabled: v.emailRemindersEnabled, reminderHoursBefore: Number(v.reminderHoursBefore) });
      toast.success('Reminder settings saved');
      onSaved(s);
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      <div className="max-w-xs">
        <TextField label="Send appointment reminders this many hours before" type="number" min="1" max="168" required
          error={errors.reminderHoursBefore?.message}
          {...register('reminderHoursBefore', { required: 'Required', min: { value: 1, message: 'At least 1 hour' }, max: { value: 168, message: 'At most 168 hours (7 days)' } })} />
      </div>
      <label className="flex items-start gap-3 text-sm text-slate-800">
        <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-700" {...register('emailRemindersEnabled')} />
        <span>Also e-mail reminders<span className="block text-xs text-slate-500">Patients can still switch reminder e-mails off for themselves. In-app reminders are always sent.</span></span>
      </label>
      <Button type="submit" loading={isSubmitting} disabled={!isDirty}>Save settings</Button>
    </form>
  );
}

/** Reminder settings, a manual run and the log of sent reminders. */
export default function RemindersPage() {
  const settings = useApi(getReminderSettings);
  const [page, setPage] = useState(0);
  const log = useApi(() => getReminderLog({ page }), [page]);
  const [running, setRunning] = useState(false);
  const [lastRun, setLastRun] = useState(null);

  async function runNow() {
    setRunning(true);
    try {
      const r = await runRemindersNow();
      setLastRun(r);
      toast.success(r.quietHours ? 'Quiet hours (21:00–08:00): nothing sent' : `Sent ${r.upcomingSent} appointment and ${r.followUpsSent} follow-up reminder(s)`);
      log.reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setRunning(false);
    }
  }

  const rows = log.data?.content || [];
  return (
    <>
      <PageHeader title="Reminders" description="Reminders go out automatically every 15 minutes between 08:00 and 21:00 (hospital time). Each reminder is sent once." />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Settings">
          {settings.loading && <Spinner />}
          {settings.error && <ErrorState error={settings.error} onRetry={settings.reload} />}
          {settings.data && <SettingsForm settings={settings.data} onSaved={() => settings.reload()} />}
        </Card>
        <Card title="Send now" subtitle="Useful after changing settings. Reminders already sent are never repeated.">
          <Button onClick={runNow} loading={running}><BellRing className="h-4 w-4" aria-hidden="true" /> Send due reminders now</Button>
          {lastRun && (
            <p className="mt-3 text-sm text-slate-700" role="status">
              {lastRun.quietHours ? 'Quiet hours: nothing was sent.' : `Sent ${lastRun.upcomingSent} appointment reminder(s) and ${lastRun.followUpsSent} follow-up reminder(s).`}
            </p>
          )}
        </Card>
      </div>
      <div className="mt-6">
        <Card title="Recent reminders">
          {log.loading && !log.data && <Spinner />}
          {log.error && <ErrorState error={log.error} onRetry={log.reload} />}
          {log.data && rows.length === 0 && <EmptyState icon={BellRing} title="No reminders sent yet" />}
          {rows.length > 0 && (
            <ul className="divide-y divide-slate-100 text-sm">
              {rows.map((r) => (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="text-slate-800">
                    {r.type === 'UPCOMING' ? `Appointment #${r.appointmentId}` : `Follow-up for consultation #${r.consultationId}`}
                  </span>
                  <span className="flex items-center gap-2 text-slate-500">
                    {r.sentAt ? formatDateTime(r.sentAt) : '—'}
                    <StatusBadge tone={r.status === 'SENT' ? 'green' : r.status === 'FAILED' ? 'red' : 'slate'}>{r.status.toLowerCase()}</StatusBadge>
                  </span>
                </li>
              ))}
            </ul>
          )}
          <div className="mt-3"><Pagination data={log.data} onPageChange={setPage} /></div>
        </Card>
      </div>
    </>
  );
}
