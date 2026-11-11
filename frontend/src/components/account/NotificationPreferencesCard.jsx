import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../../context/AuthContext.jsx';
import { getNotificationPreferences, updateNotificationPreferences } from '../../services/accountService.js';
import Button from '../ui/Button.jsx';
import Card from '../ui/Card.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import Spinner from '../ui/Spinner.jsx';

export const CATEGORY_LABELS = {
  APPOINTMENTS: ['Appointments', 'Confirmations, changes and cancellations'],
  REMINDERS: ['Reminders', 'Upcoming appointments and follow-up visits'],
  RESULTS: ['Prescriptions and lab reports', 'A notice that something is ready — never the content'],
  BILLING: ['Billing', 'Invoices, payments and refunds'],
};

/** E-mail preferences: master switch plus categories. In-app notifications and security e-mails are always on. */
export default function NotificationPreferencesCard() {
  const { user, setUser } = useAuth();
  const [prefs, setPrefs] = useState(null);
  const [saved, setSaved] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setError(null);
    try {
      const p = await getNotificationPreferences();
      setPrefs(p);
      setSaved(JSON.stringify(p));
    } catch (err) {
      setError(err);
    }
  }
  useEffect(() => { load(); }, []);

  async function save() {
    setBusy(true);
    try {
      const p = await updateNotificationPreferences(prefs);
      setPrefs(p);
      setSaved(JSON.stringify(p));
      setUser({ ...user, emailNotificationsEnabled: p.emailEnabled });
      toast.success('Notification preferences saved');
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title="E-mail notifications" subtitle="In-app notifications are always on. E-mails never include diagnoses, prescriptions or results.">
      {!prefs && !error && <Spinner />}
      {error && <ErrorState error={error} onRetry={load} />}
      {prefs && (
        <>
          <label className="flex items-center gap-3 text-sm font-medium text-slate-900">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-brand-600" checked={prefs.emailEnabled}
              onChange={(e) => setPrefs({ ...prefs, emailEnabled: e.target.checked })} />
            Send me e-mails
          </label>
          <fieldset className="mt-3 space-y-2 border-l-2 border-slate-100 pl-4" disabled={!prefs.emailEnabled}>
            <legend className="sr-only">E-mail categories</legend>
            {Object.entries(CATEGORY_LABELS).map(([key, [label, hint]]) => (
              <label key={key} className={`flex items-start gap-3 text-sm ${prefs.emailEnabled ? 'text-slate-800' : 'text-slate-400'}`}>
                <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600"
                  checked={prefs.emailEnabled && prefs.categories[key] !== false}
                  onChange={(e) => setPrefs({ ...prefs, categories: { ...prefs.categories, [key]: e.target.checked } })} />
                <span>{label}<span className="block text-xs text-slate-500">{hint}</span></span>
              </label>
            ))}
          </fieldset>
          <p className="mt-3 flex items-start gap-2 text-xs text-slate-500">
            <ShieldCheck className="h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
            Security e-mails (password reset, password changes, account activation) are always sent.
          </p>
          <Button className="mt-4" onClick={save} loading={busy} disabled={JSON.stringify(prefs) === saved}>Save preferences</Button>
        </>
      )}
    </Card>
  );
}
