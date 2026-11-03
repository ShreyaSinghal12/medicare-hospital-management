import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bookAppointment, getSlots } from '../../services/appointmentService.js';
import { todayIsoLocal } from '../../utils/dates.js';
import BookAppointmentPage from './BookAppointmentPage.jsx';

vi.mock('../../services/doctorService.js', () => ({
  getDoctor: vi.fn().mockResolvedValue({
    id: 5, fullName: 'Dr. Kavya Mehta', specialization: 'Cardiologist', departmentName: 'Cardiology', consultationFee: 800,
  }),
}));
vi.mock('../../services/appointmentService.js', () => ({ getSlots: vi.fn(), getNextAvailable: vi.fn(), bookAppointment: vi.fn() }));

const today = todayIsoLocal();

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/patient/book/5?date=${today}&time=10:00`]}>
      <Routes>
        <Route path="/patient/book/:doctorId" element={<BookAppointmentPage />} />
        <Route path="/patient/appointments/:id" element={<h1>Confirmation page</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('BookAppointmentPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getSlots).mockResolvedValue({
      doctorId: 5, date: today, slotDurationMinutes: 20, working: true, message: null, availableCount: 1,
      slots: [{ startTime: '10:00', endTime: '10:20', status: 'AVAILABLE' }],
    });
  });

  it('requires a reason before booking', async () => {
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /Confirm booking/ }));
    expect(await screen.findByText(/describe the reason/)).toBeInTheDocument();
    expect(bookAppointment).not.toHaveBeenCalled();
  });

  it('books the pre-selected slot and opens the confirmation', async () => {
    vi.mocked(bookAppointment).mockResolvedValue({ id: 77, referenceNo: 'APT-20301010-000077' });
    renderPage();
    await userEvent.type(await screen.findByLabelText(/Reason for visit/), 'Chest pain follow-up');
    await userEvent.click(screen.getByRole('button', { name: /Confirm booking/ }));
    expect(await screen.findByRole('heading', { name: 'Confirmation page' })).toBeInTheDocument();
    expect(bookAppointment).toHaveBeenCalledWith({
      doctorId: 5, date: today, startTime: '10:00', reason: 'Chest pain follow-up', followUpOfId: null,
    });
  });

  it('handles a slot taken by someone else (409) by clearing the selection and reloading slots', async () => {
    vi.mocked(bookAppointment).mockRejectedValue({
      status: 409, code: 'SLOT_ALREADY_BOOKED', message: 'Sorry, this slot has just been booked.', fieldErrors: [],
    });
    renderPage();
    await userEvent.type(await screen.findByLabelText(/Reason for visit/), 'Checkup');
    const callsBefore = vi.mocked(getSlots).mock.calls.length;
    await userEvent.click(screen.getByRole('button', { name: /Confirm booking/ }));
    expect(await screen.findByRole('button', { name: /Confirm booking/ })).toBeDisabled();
    expect(vi.mocked(getSlots).mock.calls.length).toBeGreaterThan(callsBefore);
  });
});
