import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import NotificationPreferencesCard from '../../components/account/NotificationPreferencesCard.jsx';
import { getNotificationPreferences, updateNotificationPreferences } from '../../services/accountService.js';
import { getReminderLog, getReminderSettings, runRemindersNow, updateReminderSettings } from '../../services/reminderService.js';
import RemindersPage from './RemindersPage.jsx';

const setUser = vi.fn();
vi.mock('../../context/AuthContext.jsx', () => ({
  useAuth: () => ({ user: { id: 1, fullName: 'Test User', emailNotificationsEnabled: true }, setUser }),
}));
vi.mock('../../services/accountService.js', () => ({
  getNotificationPreferences: vi.fn(),
  updateNotificationPreferences: vi.fn(),
}));
vi.mock('../../services/reminderService.js', () => ({
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
  runRemindersNow: vi.fn(),
  getReminderLog: vi.fn(),
}));

const prefs = { emailEnabled: true, categories: { APPOINTMENTS: true, REMINDERS: true, RESULTS: true, BILLING: true } };

describe('NotificationPreferencesCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('saves category choices and disables categories when e-mail is off', async () => {
    vi.mocked(getNotificationPreferences).mockResolvedValue(prefs);
    vi.mocked(updateNotificationPreferences).mockImplementation(async (p) => p);
    render(<NotificationPreferencesCard />);

    const billing = await screen.findByRole('checkbox', { name: /Billing/ });
    expect(screen.getByRole('button', { name: 'Save preferences' })).toBeDisabled();
    expect(screen.getByText(/Security e-mails .* are always sent/)).toBeInTheDocument();
    await userEvent.click(billing);
    await userEvent.click(screen.getByRole('button', { name: 'Save preferences' }));
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledWith({
      emailEnabled: true, categories: { APPOINTMENTS: true, REMINDERS: true, RESULTS: true, BILLING: false },
    }));

    await userEvent.click(screen.getByRole('checkbox', { name: 'Send me e-mails' }));
    expect(screen.getByRole('checkbox', { name: /Reminders/ })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: /Reminders/ })).not.toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Save preferences' }));
    await waitFor(() => expect(setUser).toHaveBeenLastCalledWith(expect.objectContaining({ emailNotificationsEnabled: false })));
  });
});

describe('RemindersPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('validates the lead time, saves settings and runs reminders on demand', async () => {
    vi.mocked(getReminderSettings).mockResolvedValue({ emailRemindersEnabled: true, reminderHoursBefore: 24 });
    vi.mocked(getReminderLog).mockResolvedValue({ content: [
      { id: 1, type: 'UPCOMING', appointmentId: 42, consultationId: null, status: 'SENT', scheduledFor: '2030-10-10T04:30:00Z', sentAt: '2030-10-10T05:00:00Z', failureReason: null },
    ], page: 0, totalPages: 1, totalElements: 1 });
    vi.mocked(updateReminderSettings).mockResolvedValue({ emailRemindersEnabled: true, reminderHoursBefore: 48 });
    vi.mocked(runRemindersNow).mockResolvedValue({ upcomingSent: 2, followUpsSent: 1, quietHours: false });
    render(<MemoryRouter><RemindersPage /></MemoryRouter>);

    expect(await screen.findByText('Appointment #42')).toBeInTheDocument();
    const hours = await screen.findByLabelText(/hours before/);
    await userEvent.clear(hours);
    await userEvent.type(hours, '200');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    expect(await screen.findByText('At most 168 hours (7 days)')).toBeInTheDocument();
    expect(updateReminderSettings).not.toHaveBeenCalled();
    await userEvent.clear(hours);
    await userEvent.type(hours, '48');
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith({ emailRemindersEnabled: true, reminderHoursBefore: 48 }));

    await userEvent.click(screen.getByRole('button', { name: /Send due reminders now/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('Sent 2 appointment reminder(s) and 1 follow-up reminder(s).');
  });
});
