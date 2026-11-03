import { useEffect } from 'react';
import AppRoutes from './routes/AppRoutes.jsx';
import { initCsrf } from './services/systemService.js';

export default function App() {
  // Prime the XSRF-TOKEN cookie so later POST/PUT/PATCH/DELETE requests pass CSRF checks.
  useEffect(() => {
    initCsrf().catch(() => {
      /* Backend unreachable — the status card on the home page reports this to the user. */
    });
  }, []);

  return <AppRoutes />;
}
