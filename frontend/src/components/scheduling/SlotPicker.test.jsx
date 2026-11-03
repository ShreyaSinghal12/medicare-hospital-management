import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getNextAvailable, getSlots } from '../../services/appointmentService.js';
import { addDaysIso, todayIsoLocal } from '../../utils/dates.js';
import SlotPicker from './SlotPicker.jsx';

vi.mock('../../services/appointmentService.js', () => ({ getSlots: vi.fn(), getNextAvailable: vi.fn() }));

const day = (slots, extra = {}) => ({
  doctorId: 1, date: todayIsoLocal(), slotDurationMinutes: 15, working: true, message: null,
  availableCount: slots.filter((s) => s.status === 'AVAILABLE').length, slots, ...extra,
});

describe('SlotPicker', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows available slots as buttons and disables booked ones', async () => {
    vi.mocked(getSlots).mockResolvedValue(day([
      { startTime: '09:00', endTime: '09:15', status: 'AVAILABLE' },
      { startTime: '09:15', endTime: '09:30', status: 'BOOKED' },
    ]));
    const onChange = vi.fn();
    render(<SlotPicker doctorId={1} value={null} onChange={onChange} />);
    const free = await screen.findByRole('button', { name: '09:00' });
    expect(screen.getByRole('button', { name: '09:15 (booked)' })).toBeDisabled();
    await userEvent.click(free);
    expect(onChange).toHaveBeenCalledWith({ date: todayIsoLocal(), startTime: '09:00', endTime: '09:15' });
  });

  it('explains why a day has no slots', async () => {
    vi.mocked(getSlots).mockResolvedValue(day([], { working: false, message: 'The doctor does not work on this day.' }));
    render(<SlotPicker doctorId={1} value={null} onChange={vi.fn()} />);
    expect(await screen.findByText('The doctor does not work on this day.')).toBeInTheDocument();
  });

  it('jumps to the next available slot', async () => {
    const target = addDaysIso(todayIsoLocal(), 2);
    vi.mocked(getSlots).mockResolvedValue(day([]));
    vi.mocked(getNextAvailable).mockResolvedValue({ doctorId: 1, date: target, startTime: '10:30', endTime: '10:45' });
    const onChange = vi.fn();
    render(<SlotPicker doctorId={1} value={null} onChange={onChange} />);
    await userEvent.click(await screen.findByRole('button', { name: /Next available/ }));
    expect(onChange).toHaveBeenCalledWith({ date: target, startTime: '10:30', endTime: '10:45' });
    expect(getSlots).toHaveBeenLastCalledWith(1, target);
  });
});

describe('SlotPicker date strip', () => {
  it('pages forward with the arrow without jumping back to the selected date', async () => {
    vi.mocked(getSlots).mockResolvedValue({ slots: [], message: 'x', availableCount: 0, slotDurationMinutes: 15 });
    render(<SlotPicker doctorId={1} value={null} onChange={vi.fn()} />);
    const firstDay = addDaysIso(todayIsoLocal(), 7);
    await userEvent.click(screen.getByRole('button', { name: 'Next days' }));
    expect(screen.getAllByRole('button', { pressed: false })[0]).toHaveAccessibleName(expect.stringContaining(
      new Date(Number(firstDay.slice(0, 4)), Number(firstDay.slice(5, 7)) - 1, Number(firstDay.slice(8))).getDate().toString(),
    ));
  });
});
