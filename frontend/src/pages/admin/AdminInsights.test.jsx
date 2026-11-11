import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getAdminDashboard, getHospitalSettings, previewReport, searchAuditLog, updateHospitalSettings,
} from '../../services/adminInsightsService.js';
import AdminDashboardPage, { moneyTick } from './AdminDashboardPage.jsx';
import AuditLogPage from './AuditLogPage.jsx';
import HospitalSettingsPage from './HospitalSettingsPage.jsx';
import ReportsPage from './ReportsPage.jsx';

vi.mock('../../services/adminInsightsService.js', async (orig) => ({
  ...(await orig()),
  getAdminDashboard: vi.fn(),
  getHospitalSettings: vi.fn(),
  updateHospitalSettings: vi.fn(),
  previewReport: vi.fn(),
  searchAuditLog: vi.fn(),
}));

const dashboard = {
  from: '2030-10-01', to: '2030-10-03', totalAppointments: 5, completedAppointments: 3, cancelledAppointments: 1, noShowAppointments: 1,
  newPatients: 2, activePatients: 40, activeDoctors: 5, collected: 1500, refunded: 50, outstanding: 220, outstandingInvoices: 2,
  lowStockMedicines: 1, expiringBatches: 2, expiredBatches: 0,
  appointmentsByStatus: { COMPLETED: 3, CANCELLED: 1, NO_SHOW: 1 },
  appointmentsPerDay: [{ date: '2030-10-01', count: 2 }, { date: '2030-10-02', count: 0 }, { date: '2030-10-03', count: 2 }],
  appointmentsByDepartment: [{ name: 'Cardiology', count: 4 }],
  doctorWorkload: [{ doctorId: 1, doctorName: 'Dr. Test Doctor', department: 'Cardiology', total: 4, completed: 3, noShow: 1 }],
  revenuePerDay: [{ date: '2030-10-01', amount: 1000 }, { date: '2030-10-02', amount: 0 }, { date: '2030-10-03', amount: 450 }],
  labOrdersByStatus: { COMPLETED: 2 },
};

const settings = {
  hospitalName: 'MediCare Community Hospital', address: '12 Example Avenue', phone: '+91-00000-00000', email: 'contact@medicare.example',
  timeZone: 'Asia/Kolkata', defaultSlotMinutes: 15, bookingWindowDays: 30, cancellationWindowHours: 2, cancellationPolicy: 'Policy',
  invoicePrefix: 'INV', taxRatePercent: 0, emailRemindersEnabled: true, reminderHoursBefore: 24, expiryWarningDays: 60, updatedAt: null,
};

beforeEach(() => vi.clearAllMocks());

describe('AdminDashboardPage', () => {
  it('shows headline numbers, workload and the data behind each chart', async () => {
    vi.mocked(getAdminDashboard).mockResolvedValue(dashboard);
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>);
    expect(await screen.findByText('1 / 1')).toBeInTheDocument();
    expect(screen.getByText('Dr. Test Doctor')).toBeInTheDocument();
    expect(screen.getByText('1 low · 2 expiring · 0 expired')).toBeInTheDocument();
    const perDay = screen.getByText('Appointments per day').closest('section');
    await userEvent.click(within(perDay).getByText('Show data'));
    expect(within(perDay).getByRole('cell', { name: '2030-10-02' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));
    await waitFor(() => expect(getAdminDashboard).toHaveBeenCalledTimes(2));
  });
});

describe('moneyTick', () => {
  it('keeps neighbouring axis labels distinct', () => {
    expect([550, 1100, 1650, 2200].map(moneyTick)).toEqual(['₹550', '₹1.1k', '₹1.6k', '₹2.2k']);
    expect(moneyTick(12000)).toBe('₹12k');
    expect(moneyTick(150000)).toBe('₹1.5L');
    expect(moneyTick(-50)).toBe('−₹50');
  });
});

describe('HospitalSettingsPage', () => {
  it('validates fields and keeps the time zone read-only', async () => {
    vi.mocked(getHospitalSettings).mockResolvedValue(settings);
    vi.mocked(updateHospitalSettings).mockResolvedValue({ ...settings, taxRatePercent: 5 });
    render(<HospitalSettingsPage />);
    expect(await screen.findByLabelText('Time zone')).toBeDisabled();
    const prefix = screen.getByLabelText(/Invoice number prefix/);
    await userEvent.clear(prefix);
    await userEvent.type(prefix, 'inv');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    expect(await screen.findByText('2–6 capital letters')).toBeInTheDocument();
    expect(updateHospitalSettings).not.toHaveBeenCalled();
    await userEvent.clear(prefix);
    await userEvent.type(prefix, 'BILL');
    const tax = screen.getByLabelText(/Tax rate/);
    await userEvent.clear(tax);
    await userEvent.type(tax, '5');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    await waitFor(() => expect(updateHospitalSettings).toHaveBeenCalledWith(expect.objectContaining({ invoicePrefix: 'BILL', taxRatePercent: 5, bookingWindowDays: 30 })));
    expect(updateHospitalSettings.mock.calls[0][0]).not.toHaveProperty('timeZone', 'UTC');
  });
});

describe('ReportsPage', () => {
  it('previews a report and offers CSV and PDF downloads for the same range', async () => {
    vi.mocked(previewReport).mockResolvedValue({ type: 'PAYMENTS', title: 'Payments and refunds', from: '2030-10-01', to: '2030-10-30',
      columns: ['Date and time', 'Number', 'Amount (INR)'], rows: [['2030-10-01 10:00', 'PAY-1', '100.00']], totalRows: 1, truncated: false });
    render(<ReportsPage />);
    await userEvent.selectOptions(screen.getByLabelText('Report'), 'PAYMENTS');
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }));
    expect(await screen.findByRole('cell', { name: 'PAY-1' })).toBeInTheDocument();
    const csv = screen.getByRole('link', { name: /Download CSV/ }).getAttribute('href');
    expect(csv).toMatch(/^\/api\/admin\/reports\/PAYMENTS\/export\?format=CSV&from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/);
    expect(screen.getByRole('link', { name: /Download PDF/ }).getAttribute('href')).toContain('format=PDF');
  });
});

describe('AuditLogPage', () => {
  it('filters by outcome and shows entries', async () => {
    vi.mocked(searchAuditLog).mockResolvedValue({ content: [
      { id: 1, createdAt: '2030-10-01T05:00:00Z', actorUserId: 2, actorEmail: 'reception@medicare.example', actorRole: 'RECEPTIONIST',
        action: 'LAB_ACCESS_DENIED', entityType: 'LabOrder', entityId: '7', outcome: 'DENIED', details: 'Role RECEPTIONIST', ipAddress: '127.0.0.1' },
    ], page: 0, totalPages: 1, totalElements: 1 });
    render(<AuditLogPage />);
    expect(await screen.findByText('LAB_ACCESS_DENIED')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Outcome'), 'DENIED');
    await waitFor(() => expect(searchAuditLog).toHaveBeenLastCalledWith(expect.objectContaining({ outcome: 'DENIED', page: 0 })));
  });
});
