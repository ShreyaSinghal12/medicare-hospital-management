import { Route, Routes } from 'react-router-dom';
import DashboardLayout from '../layouts/DashboardLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import UserManagementPage from '../pages/admin/UserManagementPage.jsx';
import ForgotPasswordPage from '../pages/auth/ForgotPasswordPage.jsx';
import LoginPage from '../pages/auth/LoginPage.jsx';
import RegisterPage from '../pages/auth/RegisterPage.jsx';
import ResetPasswordPage from '../pages/auth/ResetPasswordPage.jsx';
import AccountSettingsPage from '../pages/common/AccountSettingsPage.jsx';
import NotificationsPage from '../pages/common/NotificationsPage.jsx';
import StaffHomePage from '../pages/common/StaffHomePage.jsx';
import PatientDashboard from '../pages/patient/PatientDashboard.jsx';
import PatientProfilePage from '../pages/patient/PatientProfilePage.jsx';
import AboutPage from '../pages/public/AboutPage.jsx';
import DepartmentsPage from '../pages/public/DepartmentsPage.jsx';
import LandingPage from '../pages/public/LandingPage.jsx';
import NotFoundPage from '../pages/public/NotFoundPage.jsx';
import SystemStatusPage from '../pages/public/SystemStatusPage.jsx';
import UnauthorizedPage from '../pages/public/UnauthorizedPage.jsx';
import GuestRoute from './GuestRoute.jsx';
import ProtectedRoute from './ProtectedRoute.jsx';

/**
 * Application routes.
 * Public pages → PublicLayout; authenticated pages → ProtectedRoute + DashboardLayout,
 * with a role restriction per area. The backend enforces the same rules independently.
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
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route element={<GuestRoute />}>
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* Any logged-in user */}
      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="account" element={<AccountSettingsPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['PATIENT']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="patient" element={<PatientDashboard />} />
          <Route path="patient/profile" element={<PatientProfilePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['DOCTOR']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="doctor" element={<StaffHomePage title="Doctor dashboard" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['RECEPTIONIST']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="reception" element={<StaffHomePage title="Reception desk" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="admin" element={<StaffHomePage title="Administration" />} />
          <Route path="admin/users" element={<UserManagementPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['LAB_TECHNICIAN']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="lab" element={<StaffHomePage title="Laboratory" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['PHARMACIST']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="pharmacy" element={<StaffHomePage title="Pharmacy" />} />
        </Route>
      </Route>
    </Routes>
  );
}
