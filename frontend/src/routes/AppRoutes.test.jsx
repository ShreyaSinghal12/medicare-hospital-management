import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchCurrentUser } from '../services/authService.js';
import { renderApp } from '../test/renderWithProviders.jsx';

vi.mock('../services/systemService.js', () => ({
  initCsrf: vi.fn().mockResolvedValue(undefined),
  getHealth: vi.fn().mockResolvedValue(null),
  getHospitalInfo: vi.fn().mockResolvedValue({
    hospitalName: 'Test Hospital',
    departments: [{ id: 1, name: 'Cardiology', description: 'Heart care' }],
  }),
}));
vi.mock('../services/doctorService.js', () => ({
  getPublicDepartments: vi.fn().mockResolvedValue([
    { id: 1, name: 'Cardiology', description: 'Heart care', active: true, activeDoctorCount: 2 },
  ]),
}));
vi.mock('../services/authService.js', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));
vi.mock('../services/notificationService.js', () => ({
  getUnreadCount: vi.fn().mockResolvedValue(3),
  listNotifications: vi.fn().mockResolvedValue({ content: [], page: 0, totalPages: 0, totalElements: 0 }),
}));
vi.mock('../services/appointmentService.js', () => ({
  getMyAppointmentSummary: vi.fn().mockResolvedValue({ total: 4, upcoming: 1, completed: 2, cancelled: 1, noShow: 0, next: null }),
}));
vi.mock('../services/clinicalService.js', () => ({
  getMyMedicalHistory: vi.fn().mockResolvedValue({ allergies: [], conditions: [], timeline: [] }),
}));
vi.mock('../services/patientService.js', () => ({
  getMyProfile: vi.fn().mockResolvedValue({ patientCode: 'PAT-000001', age: 30, bloodGroup: 'O+' }),
}));

const patient = { id: 1, email: 'p@test.example', fullName: 'Pat Example', role: 'PATIENT' };

describe('AppRoutes', () => {
  beforeEach(() => vi.mocked(fetchCurrentUser).mockResolvedValue(null));

  it('renders the 404 page for unknown routes', async () => {
    renderApp('/does-not-exist');
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('renders the 403 page', async () => {
    renderApp('/unauthorized');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('lists departments from the API', async () => {
    renderApp('/departments');
    expect(await screen.findByRole('heading', { name: 'Cardiology' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View doctors in Cardiology' })).toHaveAttribute('href', '/doctors?departmentId=1');
  });

  it('redirects anonymous users from a protected page to the login page', async () => {
    renderApp('/patient/profile');
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
  });

  it('sends a logged-in user with the wrong role to the 403 page', async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(patient);
    renderApp('/admin/users');
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('shows the patient dashboard with role navigation and unread badge', async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(patient);
    renderApp('/patient');
    expect(await screen.findByRole('heading', { name: 'Welcome, Pat' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'My profile' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users & access' })).not.toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Notifications, 3 unread' })).toBeInTheDocument();
    expect(await screen.findByText('PAT-000001')).toBeInTheDocument();
  });

  it('redirects a logged-in user away from the login page', async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue(patient);
    renderApp('/login');
    expect(await screen.findByRole('heading', { name: 'Welcome, Pat' })).toBeInTheDocument();
  });
});
