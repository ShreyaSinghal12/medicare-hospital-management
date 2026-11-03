import { Route, Routes } from 'react-router-dom';
import DashboardLayout from '../layouts/DashboardLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import DepartmentsAdminPage from '../pages/admin/DepartmentsAdminPage.jsx';
import DoctorFormPage from '../pages/admin/DoctorFormPage.jsx';
import DoctorSchedulePage from '../pages/admin/DoctorSchedulePage.jsx';
import LeaveApprovalsPage from '../pages/admin/LeaveApprovalsPage.jsx';
import DoctorsAdminPage from '../pages/admin/DoctorsAdminPage.jsx';
import PatientsAdminPage from '../pages/admin/PatientsAdminPage.jsx';
import StaffAdminPage from '../pages/admin/StaffAdminPage.jsx';
import UserManagementPage from '../pages/admin/UserManagementPage.jsx';
import AvailabilityPage from '../pages/doctor/AvailabilityPage.jsx';
import ConsultationPage from '../pages/doctor/ConsultationPage.jsx';
import DoctorAppointmentsPage from '../pages/doctor/DoctorAppointmentsPage.jsx';
import MyPatientsPage from '../pages/doctor/MyPatientsPage.jsx';
import PatientRecordPage from '../pages/doctor/PatientRecordPage.jsx';
import MedicalHistoryPage from '../pages/patient/MedicalHistoryPage.jsx';
import DoctorDashboard from '../pages/doctor/DoctorDashboard.jsx';
import DoctorOwnProfilePage from '../pages/doctor/DoctorOwnProfilePage.jsx';
import AppointmentDetailPage from '../pages/patient/AppointmentDetailPage.jsx';
import BookAppointmentPage from '../pages/patient/BookAppointmentPage.jsx';
import MyAppointmentsPage from '../pages/patient/MyAppointmentsPage.jsx';
import ReschedulePage from '../pages/patient/ReschedulePage.jsx';
import { FindDoctorPage, PatientDoctorProfilePage } from '../pages/patient/FindDoctorPage.jsx';
import DoctorProfilePage from '../pages/public/DoctorProfilePage.jsx';
import DoctorsPage from '../pages/public/DoctorsPage.jsx';
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
        <Route path="doctors" element={<DoctorsPage />} />
        <Route path="doctors/:id" element={<DoctorProfilePage />} />
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
          <Route path="patient/doctors" element={<FindDoctorPage />} />
          <Route path="patient/doctors/:id" element={<PatientDoctorProfilePage />} />
          <Route path="patient/book/:doctorId" element={<BookAppointmentPage />} />
          <Route path="patient/appointments" element={<MyAppointmentsPage />} />
          <Route path="patient/history" element={<MedicalHistoryPage />} />
          <Route path="patient/appointments/:id" element={<AppointmentDetailPage />} />
          <Route path="patient/appointments/:id/reschedule" element={<ReschedulePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['DOCTOR']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="doctor" element={<DoctorDashboard />} />
          <Route path="doctor/appointments" element={<DoctorAppointmentsPage />} />
          <Route path="doctor/availability" element={<AvailabilityPage />} />
          <Route path="doctor/patients" element={<MyPatientsPage />} />
          <Route path="doctor/patients/:patientId" element={<PatientRecordPage />} />
          <Route path="doctor/consultations/:id" element={<ConsultationPage />} />
          <Route path="doctor/profile" element={<DoctorOwnProfilePage />} />
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
          <Route path="admin/doctors" element={<DoctorsAdminPage />} />
          <Route path="admin/doctors/new" element={<DoctorFormPage />} />
          <Route path="admin/doctors/:id/edit" element={<DoctorFormPage />} />
          <Route path="admin/doctors/:id/schedule" element={<DoctorSchedulePage />} />
          <Route path="admin/leave" element={<LeaveApprovalsPage />} />
          <Route path="admin/departments" element={<DepartmentsAdminPage />} />
          <Route path="admin/staff" element={<StaffAdminPage />} />
          <Route path="admin/patients" element={<PatientsAdminPage />} />
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
