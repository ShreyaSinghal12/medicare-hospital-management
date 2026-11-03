import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchCurrentUser, forgotPassword, login, registerPatient, resetPassword,
} from '../../services/authService.js';
import { renderApp } from '../../test/renderWithProviders.jsx';

vi.mock('../../services/systemService.js', () => ({
  initCsrf: vi.fn().mockResolvedValue(undefined),
  getHealth: vi.fn().mockResolvedValue(null),
  getHospitalInfo: vi.fn().mockResolvedValue({ hospitalName: 'Test Hospital', departments: [] }),
}));
vi.mock('../../services/authService.js', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  registerPatient: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
}));
vi.mock('../../services/notificationService.js', () => ({
  getUnreadCount: vi.fn().mockResolvedValue(0),
  listNotifications: vi.fn().mockResolvedValue({ content: [], page: 0, totalPages: 0, totalElements: 0 }),
}));
vi.mock('../../services/patientService.js', () => ({
  getMyProfile: vi.fn().mockResolvedValue({ patientCode: 'PAT-000001', age: 30 }),
}));

describe('Login page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchCurrentUser).mockResolvedValue(null);
  });

  it('validates required fields before calling the API', async () => {
    renderApp('/login');
    await userEvent.click(await screen.findByRole('button', { name: 'Log in' }));
    expect(await screen.findByText('E-mail is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('shows the server error for invalid credentials', async () => {
    vi.mocked(login).mockRejectedValue({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid e-mail or password' });
    renderApp('/login');
    await userEvent.type(await screen.findByLabelText(/E-mail address/), 'a@test.example');
    await userEvent.type(screen.getByLabelText(/^Password/, { selector: 'input' }), 'Wrong@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid e-mail or password');
  });

  it('redirects to the role dashboard after a successful login', async () => {
    vi.mocked(login).mockResolvedValue({ id: 9, email: 'doc@test.example', fullName: 'Dr. Test', role: 'DOCTOR' });
    renderApp('/login');
    await userEvent.type(await screen.findByLabelText(/E-mail address/), 'doc@test.example');
    await userEvent.type(screen.getByLabelText(/^Password/, { selector: 'input' }), 'Secure@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('heading', { name: 'Doctor dashboard' })).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith('doc@test.example', 'Secure@123');
  });

  it('honours a safe ?redirect= target', async () => {
    vi.mocked(login).mockResolvedValue({ id: 1, email: 'p@test.example', fullName: 'Pat Example', role: 'PATIENT' });
    renderApp('/login?redirect=%2Faccount');
    await userEvent.type(await screen.findByLabelText(/E-mail address/), 'p@test.example');
    await userEvent.type(screen.getByLabelText(/^Password/, { selector: 'input' }), 'Secure@123');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('heading', { name: 'Account settings' })).toBeInTheDocument();
  });
});

describe('Register page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchCurrentUser).mockResolvedValue(null);
  });

  async function fillValidForm(password = 'Secure@123', confirm = 'Secure@123') {
    await userEvent.type(await screen.findByLabelText(/Full name/), 'Test Patient');
    await userEvent.type(screen.getByLabelText(/Date of birth/), '1990-05-20');
    await userEvent.type(screen.getByLabelText(/E-mail address/), 'new@test.example');
    await userEvent.type(screen.getByLabelText(/Phone number/), '+91 98765 43210');
    await userEvent.type(screen.getByLabelText(/^Password/, { selector: 'input' }), password);
    await userEvent.type(screen.getByLabelText(/Confirm password/), confirm);
    await userEvent.click(screen.getByRole('checkbox'));
  }

  it('rejects a weak password and a mismatched confirmation on the client', async () => {
    renderApp('/register');
    await fillValidForm('weakpass', 'different');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Password does not meet the requirements')).toBeInTheDocument();
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
    expect(registerPatient).not.toHaveBeenCalled();
  });

  it('shows a duplicate e-mail error from the server on the e-mail field', async () => {
    vi.mocked(registerPatient).mockRejectedValue({
      status: 409, code: 'EMAIL_ALREADY_REGISTERED', message: 'An account with this e-mail address already exists', fieldErrors: [],
    });
    renderApp('/register');
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('An account with this e-mail address already exists')).toBeInTheDocument();
  });

  it('registers and moves to the login page with the e-mail pre-filled', async () => {
    vi.mocked(registerPatient).mockResolvedValue({ patientCode: 'PAT-000123', email: 'new@test.example' });
    renderApp('/register');
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByLabelText(/E-mail address/)).toHaveValue('new@test.example');
    expect(registerPatient).toHaveBeenCalledWith(expect.objectContaining({
      fullName: 'Test Patient', email: 'new@test.example', gender: null, bloodGroup: null,
    }));
  });
});

describe('Password recovery pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchCurrentUser).mockResolvedValue(null);
  });

  it('forgot password shows the neutral confirmation message', async () => {
    vi.mocked(forgotPassword).mockResolvedValue({ message: 'If an account exists for that e-mail, a password reset link has been sent.' });
    renderApp('/forgot-password');
    await userEvent.type(await screen.findByLabelText(/E-mail address/), 'x@test.example');
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(await screen.findByText(/If an account exists/)).toBeInTheDocument();
  });

  it('reset password without a token explains the link is invalid', async () => {
    renderApp('/reset-password');
    expect(await screen.findByRole('heading', { name: 'Invalid reset link' })).toBeInTheDocument();
  });

  it('reset password shows an expired-token error', async () => {
    vi.mocked(resetPassword).mockRejectedValue({
      status: 422, code: 'RESET_TOKEN_INVALID', message: 'This password reset link is invalid or has expired.', fieldErrors: [],
    });
    renderApp('/reset-password?token=abc');
    await userEvent.type(await screen.findByLabelText(/^New password/), 'Reset@1234');
    await userEvent.type(screen.getByLabelText(/Confirm new password/), 'Reset@1234');
    await userEvent.click(screen.getByRole('button', { name: 'Reset password' }));
    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith({
      token: 'abc', newPassword: 'Reset@1234', confirmPassword: 'Reset@1234',
    }));
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid or has expired');
  });
});
