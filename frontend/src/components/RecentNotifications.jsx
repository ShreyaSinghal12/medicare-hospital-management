import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import useApi from '../hooks/useApi.js';
import { listNotifications } from '../services/notificationService.js';
import { formatDateTime } from '../utils/format.js';
import Card from './ui/Card.jsx';
import EmptyState from './ui/EmptyState.jsx';
import ErrorState from './ui/ErrorState.jsx';
import Spinner from './ui/Spinner.jsx';

/** The user's five most recent notifications (real data from /api/notifications). */
export default function RecentNotifications() {
  const { data, loading, error, reload } = useApi(() => listNotifications({ size: 5 }));
  const items = data?.content || [];

  return (
    <Card
      title="Recent notifications"
      actions={<Link to="/notifications" className="text-sm font-medium text-brand-700 hover:underline">View all</Link>}
    >
      {loading && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState icon={Bell} title="No notifications yet" />
      )}
      {items.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {items.map((n) => (
            <li key={n.id} className="flex items-start gap-3 py-3">
              <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.read ? 'bg-slate-200' : 'bg-brand-500'}`} aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-slate-900">
                  {n.title} {!n.read && <span className="sr-only">(unread)</span>}
                </p>
                <p className="text-sm text-slate-600">{n.message}</p>
                <p className="mt-0.5 text-xs text-slate-500">{formatDateTime(n.createdAt)}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
