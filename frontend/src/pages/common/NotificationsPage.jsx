import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { toast } from 'sonner';
import { NOTIFICATIONS_CHANGED } from '../../components/NotificationBell.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import {
  listNotifications, markAllNotificationsRead, markNotificationRead,
} from '../../services/notificationService.js';
import { formatDateTime } from '../../utils/format.js';

function announceChange() {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
}

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [busy, setBusy] = useState(false);
  const { data, loading, error, reload } = useApi(
    () => listNotifications({ unreadOnly, page, size: 10 }),
    [unreadOnly, page],
  );
  const items = data?.content || [];

  async function markOne(id) {
    try {
      await markNotificationRead(id);
      announceChange();
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  }

  async function markAll() {
    setBusy(true);
    try {
      const res = await markAllNotificationsRead();
      toast.success(res.updated ? `Marked ${res.updated} notification(s) as read` : 'Everything was already read');
      announceChange();
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Updates about your account, appointments, reports and bills."
        actions={
          <Button variant="secondary" onClick={markAll} loading={busy}>
            <CheckCheck className="h-4 w-4" aria-hidden="true" /> Mark all as read
          </Button>
        }
      />
      <Card>
        <div className="mb-4 flex gap-2" role="group" aria-label="Filter notifications">
          {[
            { value: false, label: 'All' },
            { value: true, label: 'Unread' },
          ].map((f) => (
            <button
              key={f.label}
              type="button"
              aria-pressed={unreadOnly === f.value}
              onClick={() => {
                setUnreadOnly(f.value);
                setPage(0);
              }}
              className={`rounded-full px-3 py-1 text-sm font-medium ${
                unreadOnly === f.value ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {loading && !data && <Spinner />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {!loading && !error && items.length === 0 && (
          <EmptyState icon={Bell} title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} />
        )}
        {items.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {items.map((n) => (
              <li key={n.id} className="flex flex-wrap items-start justify-between gap-3 py-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-slate-900">{n.title}</p>
                    {!n.read && <StatusBadge tone="blue">New</StatusBadge>}
                  </div>
                  <p className="mt-0.5 text-sm text-slate-600">{n.message}</p>
                  <p className="mt-1 text-xs text-slate-400">{formatDateTime(n.createdAt)}</p>
                  {n.link && (
                    <Link to={n.link} className="mt-1 inline-block text-sm font-medium text-brand-700 hover:underline">
                      Open
                    </Link>
                  )}
                </div>
                {!n.read && (
                  <Button variant="ghost" size="sm" onClick={() => markOne(n.id)}>Mark as read</Button>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="mt-4">
          <Pagination data={data} onPageChange={setPage} />
        </div>
      </Card>
    </>
  );
}
