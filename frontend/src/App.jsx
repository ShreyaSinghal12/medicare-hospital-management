import { useEffect, useState } from 'react';
import Spinner from './components/ui/Spinner.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import AppRoutes from './routes/AppRoutes.jsx';
import { getHospitalInfo } from './services/systemService.js';
import { setHospitalTimeZone } from './utils/hospitalTime.js';

/** Loads the hospital time zone once before rendering, so every date shown is in hospital time. */
function HospitalTimeGate({ children }) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    getHospitalInfo()
      .then((info) => info?.timeZone && setHospitalTimeZone(info.timeZone))
      .catch(() => { /* backend unreachable: fall back to the device's zone; pages show their own errors */ })
      .finally(() => setReady(true));
  }, []);
  if (!ready) {
    return <div className="flex min-h-screen items-center justify-center"><Spinner label="Starting MediCare…" /></div>;
  }
  return children;
}

export default function App() {
  return (
    <HospitalTimeGate>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </HospitalTimeGate>
  );
}
