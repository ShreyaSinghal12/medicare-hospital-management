import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  checkIn, getDayAppointments, getQueue, registerWalkIn, requeueEntry, skipQueueEntry,
} from '../../services/receptionService.js';
import { todayIsoLocal } from '../../utils/dates.js';
import DayAppointmentsPage from './DayAppointmentsPage.jsx';
import QueuePage from './QueuePage.jsx';
import RegisterWalkInPage from './RegisterWalkInPage.jsx';

vi.mock('../../services/receptionService.js', () => ({
  checkIn: vi.fn(),
  getDayAppointments: vi.fn(),
  getQueue: vi.fn(),
  registerWalkIn: vi.fn(),
  skipQueueEntry: vi.fn(),
  requeueEntry: vi.fn(),
}));
vi.mock('../../services/doctorService.js', () => ({
  searchDoctors: vi.fn().mockResolvedValue({ content: [{ id: 1, fullName: 'Dr. Asha Rao' }] }),
}));
vi.mock('../../services/appointmentService.js', () => ({ markNoShow: vi.fn() }));

const wrap = (ui) => render(<MemoryRouter>{ui}</MemoryRouter>);

const appt = (over = {}) => ({
  id: 5, date: todayIsoLocal(), startTime: '23:30', status: 'SCHEDULED', patientName: 'Test Patient One',
  patientCode: 'PAT-000101', doctorName: 'Dr. Asha Rao', referenceNo: 'APT-20301010-000005', type: 'WALK_IN',
  queueToken: null, queueStatus: null, canCancel: true, canReschedule: true, needsReschedule: false, ...over,
});

describe('DayAppointmentsPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('checks a patient in and reloads with the issued token', async () => {
    vi.mocked(getDayAppointments)
      .mockResolvedValueOnce([appt()])
      .mockResolvedValue([appt({ status: 'CHECKED_IN', queueToken: 3, queueStatus: 'WAITING', canReschedule: false })]);
    vi.mocked(checkIn).mockResolvedValue({ id: 5, queueToken: 3 });
    wrap(<DayAppointmentsPage />);

    expect(await screen.findByText('Test Patient One')).toBeInTheDocument();
    expect(screen.getByText(/walk-in/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Check in' }));

    await waitFor(() => expect(checkIn).toHaveBeenCalledWith(5));
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Check in' })).not.toBeInTheDocument();
  });

  it('links each row to its printable confirmation', async () => {
    vi.mocked(getDayAppointments).mockResolvedValue([appt()]);
    wrap(<DayAppointmentsPage />);
    expect(await screen.findByText('Test Patient One')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Print confirmation for Test Patient One' }))
      .toHaveAttribute('href', '/reception/appointments/5/print');
  });
});

describe('QueuePage', () => {
  beforeEach(() => vi.clearAllMocks());

  const entry = (over) => ({
    doctorId: 1, doctorName: 'Dr. Asha Rao', appointmentTime: '10:00', checkedInAt: '2030-10-10T04:30:00Z',
    appointmentStatus: 'CHECKED_IN', ...over,
  });

  it('groups entries by doctor and allows skip and put back', async () => {
    vi.mocked(getQueue).mockResolvedValue([
      entry({ id: 11, tokenNumber: 1, patientName: 'Test Patient One', status: 'IN_CONSULTATION', appointmentStatus: 'IN_PROGRESS' }),
      entry({ id: 12, tokenNumber: 2, patientName: 'Test Patient Two', status: 'WAITING' }),
      entry({ id: 13, tokenNumber: 3, patientName: 'Test Patient Three', status: 'SKIPPED' }),
    ]);
    vi.mocked(skipQueueEntry).mockResolvedValue({});
    vi.mocked(requeueEntry).mockResolvedValue({});
    wrap(<QueuePage />);

    expect(await screen.findByText('1 waiting · now seeing token 1')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Skip' }));
    await waitFor(() => expect(skipQueueEntry).toHaveBeenCalledWith(12));
    await userEvent.click(screen.getByRole('button', { name: 'Put back' }));
    await waitFor(() => expect(requeueEntry).toHaveBeenCalledWith(13));
    expect(getQueue.mock.calls.length).toBeGreaterThanOrEqual(3);
  });

  it('shows an empty state when nobody is checked in', async () => {
    vi.mocked(getQueue).mockResolvedValue([]);
    wrap(<QueuePage />);
    expect(await screen.findByText('Nobody checked in yet')).toBeInTheDocument();
  });
});

describe('RegisterWalkInPage', () => {
  beforeEach(() => vi.clearAllMocks());

  async function fill() {
    await userEvent.type(screen.getByLabelText(/Full name/), 'Test Walkin');
    await userEvent.type(screen.getByLabelText(/E-mail/), 'walkin@test.example');
    await userEvent.type(screen.getByLabelText(/^Phone/), '+919800000001');
    await userEvent.type(screen.getByLabelText(/Date of birth/), '1990-05-01');
  }

  it('validates required fields before calling the API', async () => {
    wrap(<RegisterWalkInPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Register patient' }));
    expect((await screen.findAllByText('Required')).length).toBeGreaterThanOrEqual(4);
    expect(registerWalkIn).not.toHaveBeenCalled();
  });

  it('registers the patient without a password and offers booking', async () => {
    vi.mocked(registerWalkIn).mockResolvedValue({ id: 42, fullName: 'Test Walkin', patientCode: 'PAT-000042', email: 'walkin@test.example' });
    wrap(<RegisterWalkInPage />);
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Register patient' }));

    await waitFor(() => expect(registerWalkIn).toHaveBeenCalled());
    const payload = vi.mocked(registerWalkIn).mock.calls[0][0];
    expect(payload).not.toHaveProperty('password');
    expect(payload).toMatchObject({ fullName: 'Test Walkin', email: 'walkin@test.example', gender: null });
    expect(await screen.findByText('Test Walkin · PAT-000042')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Book appointment' })).toHaveAttribute('href', '/reception/book?patientId=42');
  });

  it('maps a duplicate e-mail to the e-mail field', async () => {
    vi.mocked(registerWalkIn).mockRejectedValue(Object.assign(new Error('dup'), { code: 'EMAIL_ALREADY_REGISTERED' }));
    wrap(<RegisterWalkInPage />);
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Register patient' }));
    expect(await screen.findByText(/already registered/)).toBeInTheDocument();
  });
});
