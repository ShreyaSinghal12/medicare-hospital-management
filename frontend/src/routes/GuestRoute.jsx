import { Navigate, Outlet, useSearchParams } from 'react-router-dom';
import Spinner from '../components/ui/Spinner.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { homeFor, safeRedirect } from '../utils/roles.js';

/**
 * Login/register pages: an already logged-in user is sent to the requested
 * ?redirect= page (if it is a safe relative path) or to their role dashboard.
 */
export default function GuestRoute() {
  const { user, initializing } = useAuth();
  const [params] = useSearchParams();
  if (initializing) {
    return (
      <div className="flex justify-center py-24">
        <Spinner />
      </div>
    );
  }
  if (user) {
    return <Navigate to={safeRedirect(params.get('redirect')) || homeFor(user)} replace />;
  }
  return <Outlet />;
}
