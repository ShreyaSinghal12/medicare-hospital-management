import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { getUnreadCount } from '../services/notificationService.js';

export const NOTIFICATIONS_CHANGED = 'medicare:notifications-changed';

/** Navbar bell with the unread count. Refreshes on navigation, every 60 s and on change events. */
export default function NotificationBell() {
  const [count, setCount] = useState(0);
  const location = useLocation();

  const load = useCallback(() => {
    getUnreadCount().then(setCount).catch(() => {});
  }, []);

  useEffect(() => {
    load();
  }, [load, location.pathname]);

  useEffect(() => {
    const id = setInterval(load, 60000);
    window.addEventListener(NOTIFICATIONS_CHANGED, load);
    return () => {
      clearInterval(id);
      window.removeEventListener(NOTIFICATIONS_CHANGED, load);
    };
  }, [load]);

  const label = count > 0 ? `Notifications, ${count} unread` : 'Notifications';
  return (
    <Link to="/notifications" className="relative rounded-full p-2 text-slate-600 hover:bg-slate-100" aria-label={label}>
      <Bell className="h-5 w-5" aria-hidden="true" />
      {count > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-[11px] font-bold text-white">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}
