import { Route, Routes } from 'react-router-dom';
import PublicLayout from '../layouts/PublicLayout.jsx';
import AboutPage from '../pages/public/AboutPage.jsx';
import DepartmentsPage from '../pages/public/DepartmentsPage.jsx';
import LandingPage from '../pages/public/LandingPage.jsx';
import NotFoundPage from '../pages/public/NotFoundPage.jsx';
import SystemStatusPage from '../pages/public/SystemStatusPage.jsx';
import UnauthorizedPage from '../pages/public/UnauthorizedPage.jsx';

/**
 * Application routes. Role-specific route groups (patient, doctor, receptionist,
 * admin, laboratory, pharmacy) are added behind a ProtectedRoute in Phase 2+.
 */
export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<LandingPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="departments" element={<DepartmentsPage />} />
        <Route path="status" element={<SystemStatusPage />} />
        <Route path="unauthorized" element={<UnauthorizedPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
