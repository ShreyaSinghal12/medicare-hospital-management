import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import Spinner from '../components/ui/Spinner.jsx';
import DashboardLayout from '../layouts/DashboardLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { FindDoctorPage, PatientDoctorProfilePage } from '../pages/patient/FindDoctorPage.jsx';
import LoginPage from '../pages/auth/LoginPage.jsx';
import LandingPage from '../pages/public/LandingPage.jsx';
import NotFoundPage from '../pages/public/NotFoundPage.jsx';
import UnauthorizedPage from '../pages/public/UnauthorizedPage.jsx';
import GuestRoute from './GuestRoute.jsx';
import ProtectedRoute from './ProtectedRoute.jsx';

// Pages load on first visit (code splitting); the landing, login and error pages stay in the main bundle.
const DepartmentsAdminPage = lazy(() => import('../pages/admin/DepartmentsAdminPage.jsx'));
const DoctorFormPage = lazy(() => import('../pages/admin/DoctorFormPage.jsx'));
const DoctorSchedulePage = lazy(() => import('../pages/admin/DoctorSchedulePage.jsx'));
const LeaveApprovalsPage = lazy(() => import('../pages/admin/LeaveApprovalsPage.jsx'));
const DoctorsAdminPage = lazy(() => import('../pages/admin/DoctorsAdminPage.jsx'));
const PatientsAdminPage = lazy(() => import('../pages/admin/PatientsAdminPage.jsx'));
const StaffAdminPage = lazy(() => import('../pages/admin/StaffAdminPage.jsx'));
const UserManagementPage = lazy(() => import('../pages/admin/UserManagementPage.jsx'));
const AvailabilityPage = lazy(() => import('../pages/doctor/AvailabilityPage.jsx'));
const ConsultationPage = lazy(() => import('../pages/doctor/ConsultationPage.jsx'));
const DayAppointmentsPage = lazy(() => import('../pages/reception/DayAppointmentsPage.jsx'));
const PatientDirectoryPage = lazy(() => import('../pages/reception/PatientDirectoryPage.jsx'));
const PrintConfirmationPage = lazy(() => import('../pages/reception/PrintConfirmationPage.jsx'));
const QueuePage = lazy(() => import('../pages/reception/QueuePage.jsx'));
const ReceptionBookPage = lazy(() => import('../pages/reception/ReceptionBookPage.jsx'));
const ReceptionDashboard = lazy(() => import('../pages/reception/ReceptionDashboard.jsx'));
const ReceptionPatientPage = lazy(() => import('../pages/reception/ReceptionPatientPage.jsx'));
const RegisterWalkInPage = lazy(() => import('../pages/reception/RegisterWalkInPage.jsx'));
const NewPrescriptionPage = lazy(() => import('../pages/doctor/NewPrescriptionPage.jsx'));
const PrescriptionWorkspacePage = lazy(() => import('../pages/doctor/PrescriptionWorkspacePage.jsx'));
const PatientPrescriptionPage = lazy(() => import('../pages/prescriptions/PatientPrescriptionPage.jsx'));
const PrescriptionListPage = lazy(() => import('../pages/prescriptions/PrescriptionListPage.jsx'));
const DoctorAppointmentsPage = lazy(() => import('../pages/doctor/DoctorAppointmentsPage.jsx'));
const MyPatientsPage = lazy(() => import('../pages/doctor/MyPatientsPage.jsx'));
const PatientRecordPage = lazy(() => import('../pages/doctor/PatientRecordPage.jsx'));
const MedicalHistoryPage = lazy(() => import('../pages/patient/MedicalHistoryPage.jsx'));
const DoctorDashboard = lazy(() => import('../pages/doctor/DoctorDashboard.jsx'));
const DoctorOwnProfilePage = lazy(() => import('../pages/doctor/DoctorOwnProfilePage.jsx'));
const AppointmentDetailPage = lazy(() => import('../pages/patient/AppointmentDetailPage.jsx'));
const BookAppointmentPage = lazy(() => import('../pages/patient/BookAppointmentPage.jsx'));
const MyAppointmentsPage = lazy(() => import('../pages/patient/MyAppointmentsPage.jsx'));
const ReschedulePage = lazy(() => import('../pages/patient/ReschedulePage.jsx'));
const DoctorProfilePage = lazy(() => import('../pages/public/DoctorProfilePage.jsx'));
const DoctorsPage = lazy(() => import('../pages/public/DoctorsPage.jsx'));
const ForgotPasswordPage = lazy(() => import('../pages/auth/ForgotPasswordPage.jsx'));
const RegisterPage = lazy(() => import('../pages/auth/RegisterPage.jsx'));
const ResetPasswordPage = lazy(() => import('../pages/auth/ResetPasswordPage.jsx'));
const AccountSettingsPage = lazy(() => import('../pages/common/AccountSettingsPage.jsx'));
const NotificationsPage = lazy(() => import('../pages/common/NotificationsPage.jsx'));
const LabCatalogPage = lazy(() => import('../pages/admin/LabCatalogPage.jsx'));
const RemindersPage = lazy(() => import('../pages/admin/RemindersPage.jsx'));
const AdminDashboardPage = lazy(() => import('../pages/admin/AdminDashboardPage.jsx'));
const AuditLogPage = lazy(() => import('../pages/admin/AuditLogPage.jsx'));
const HospitalSettingsPage = lazy(() => import('../pages/admin/HospitalSettingsPage.jsx'));
const ReportsPage = lazy(() => import('../pages/admin/ReportsPage.jsx'));
const BillingSummaryPage = lazy(() => import('../pages/billing/BillingSummaryPage.jsx'));
const InvoiceListPage = lazy(() => import('../pages/billing/InvoiceListPage.jsx'));
const InvoicePage = lazy(() => import('../pages/billing/InvoicePage.jsx'));
const NewInvoicePage = lazy(() => import('../pages/billing/NewInvoicePage.jsx'));
const DispensePage = lazy(() => import('../pages/inventory/DispensePage.jsx'));
const DispensingListPage = lazy(() => import('../pages/inventory/DispensingListPage.jsx'));
const DispensingRecordPage = lazy(() => import('../pages/inventory/DispensingRecordPage.jsx'));
const InventoryAlertsPage = lazy(() => import('../pages/inventory/InventoryAlertsPage.jsx'));
const MedicineDetailPage = lazy(() => import('../pages/inventory/MedicineDetailPage.jsx'));
const MedicinesPage = lazy(() => import('../pages/inventory/MedicinesPage.jsx'));
const StockLedgerPage = lazy(() => import('../pages/inventory/StockLedgerPage.jsx'));
const DoctorLabOrderPage = lazy(() => import('../pages/lab/DoctorLabOrderPage.jsx'));
const LabOrderListPage = lazy(() => import('../pages/lab/LabOrderListPage.jsx'));
const PatientLabReportPage = lazy(() => import('../pages/lab/PatientLabReportPage.jsx'));
const PatientDashboard = lazy(() => import('../pages/patient/PatientDashboard.jsx'));
const PatientProfilePage = lazy(() => import('../pages/patient/PatientProfilePage.jsx'));
const AboutPage = lazy(() => import('../pages/public/AboutPage.jsx'));
const DepartmentsPage = lazy(() => import('../pages/public/DepartmentsPage.jsx'));
const SystemStatusPage = lazy(() => import('../pages/public/SystemStatusPage.jsx'));

/**
 * Application routes.
 * Public pages → PublicLayout; authenticated pages → ProtectedRoute + DashboardLayout,
 * with a role restriction per area. The backend enforces the same rules independently.
 */
export default function AppRoutes() {
  return (
    <Suspense fallback={<Spinner label="Loading page…" />}>
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
          <Route path="patient/prescriptions" element={<PrescriptionListPage mode="patient" />} />
          <Route path="patient/prescriptions/:id" element={<PatientPrescriptionPage />} />
          <Route path="patient/invoices" element={<InvoiceListPage mode="patient" />} />
          <Route path="patient/invoices/:id" element={<InvoicePage mode="patient" />} />
          <Route path="patient/lab-reports" element={<LabOrderListPage mode="patient" />} />
          <Route path="patient/lab-reports/:id" element={<PatientLabReportPage />} />
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
          <Route path="doctor/consultations/:consultationId/prescriptions/new" element={<NewPrescriptionPage />} />
          <Route path="doctor/prescriptions" element={<PrescriptionListPage mode="doctor" />} />
          <Route path="doctor/prescriptions/:id" element={<PrescriptionWorkspacePage />} />
          <Route path="doctor/lab-orders" element={<LabOrderListPage mode="doctor" />} />
          <Route path="doctor/lab-orders/:id" element={<DoctorLabOrderPage />} />
          <Route path="doctor/profile" element={<DoctorOwnProfilePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['RECEPTIONIST']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="reception" element={<ReceptionDashboard />} />
          <Route path="reception/appointments" element={<DayAppointmentsPage />} />
          <Route path="reception/appointments/:id/print" element={<PrintConfirmationPage />} />
          <Route path="reception/queue" element={<QueuePage />} />
          <Route path="reception/book" element={<ReceptionBookPage />} />
          <Route path="reception/patients" element={<PatientDirectoryPage />} />
          <Route path="reception/patients/new" element={<RegisterWalkInPage />} />
          <Route path="reception/patients/:id" element={<ReceptionPatientPage />} />
          <Route path="reception/billing" element={<InvoiceListPage mode="reception" />} />
          <Route path="reception/billing/new" element={<NewInvoicePage />} />
          <Route path="reception/billing/:id" element={<InvoicePage mode="reception" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route element={<DashboardLayout />}>
          <Route path="admin" element={<AdminDashboardPage />} />
          <Route path="admin/settings" element={<HospitalSettingsPage />} />
          <Route path="admin/reports" element={<ReportsPage />} />
          <Route path="admin/audit" element={<AuditLogPage />} />
          <Route path="admin/users" element={<UserManagementPage />} />
          <Route path="admin/doctors" element={<DoctorsAdminPage />} />
          <Route path="admin/doctors/new" element={<DoctorFormPage />} />
          <Route path="admin/doctors/:id/edit" element={<DoctorFormPage />} />
          <Route path="admin/doctors/:id/schedule" element={<DoctorSchedulePage />} />
          <Route path="admin/leave" element={<LeaveApprovalsPage />} />
          <Route path="admin/departments" element={<DepartmentsAdminPage />} />
          <Route path="admin/staff" element={<StaffAdminPage />} />
          <Route path="admin/patients" element={<PatientsAdminPage />} />
          <Route path="admin/lab-tests" element={<LabCatalogPage />} />
          <Route path="admin/billing" element={<BillingSummaryPage />} />
          <Route path="admin/reminders" element={<RemindersPage />} />
          <Route path="admin/invoices" element={<InvoiceListPage mode="admin" />} />
          <Route path="admin/invoices/:id" element={<InvoicePage mode="admin" />} />
          <Route path="admin/inventory" element={<MedicinesPage />} />
          <Route path="admin/inventory/medicines/:id" element={<MedicineDetailPage />} />
          <Route path="admin/inventory/alerts" element={<InventoryAlertsPage />} />
          <Route path="admin/inventory/ledger" element={<StockLedgerPage />} />
          <Route path="admin/dispensing" element={<DispensingListPage />} />
          <Route path="admin/dispensing/new" element={<DispensePage />} />
          <Route path="admin/dispensing/:id" element={<DispensingRecordPage />} />
        </Route>
      </Route>


    </Routes>
    </Suspense>
  );
}
