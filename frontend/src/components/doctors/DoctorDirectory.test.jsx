import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDoctor, searchDoctors } from '../../services/doctorService.js';
import DoctorDirectory from './DoctorDirectory.jsx';
import DoctorProfile from './DoctorProfile.jsx';

vi.mock('../../context/AuthContext.jsx', () => ({ useAuth: () => ({ user: null }) }));
vi.mock('../../services/appointmentService.js', () => ({
  getDoctorSchedule: vi.fn().mockResolvedValue({ days: [] }),
  getSlots: vi.fn().mockResolvedValue({ slots: [], message: 'The doctor does not work on this day.' }),
  getNextAvailable: vi.fn().mockResolvedValue(null),
}));
vi.mock('../../services/doctorService.js', () => ({
  searchDoctors: vi.fn(),
  getDoctor: vi.fn(),
  getSpecializations: vi.fn().mockResolvedValue(['Cardiologist', 'Dermatologist']),
  getPublicDepartments: vi.fn().mockResolvedValue([
    { id: 1, name: 'Cardiology' }, { id: 2, name: 'Dermatology' },
  ]),
}));

const mehta = {
  id: 7, fullName: 'Dr. Kavya Mehta', departmentId: 1, departmentName: 'Cardiology', specialization: 'Cardiologist',
  qualifications: 'MBBS, MD', experienceYears: 12, consultationFee: 800, slotDurationMinutes: 20,
};
const page = (content) => ({ content, page: 0, size: 12, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true });

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/doctors" element={<DoctorDirectory profileBase="/doctors" />} />
        <Route path="/doctors/:id" element={<DoctorProfile backTo="/doctors" />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DoctorDirectory', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists doctors with department, fee and a profile link', async () => {
    vi.mocked(searchDoctors).mockResolvedValue(page([mehta]));
    renderAt('/doctors');
    expect(await screen.findByRole('link', { name: 'Dr. Kavya Mehta' })).toHaveAttribute('href', '/doctors/7');
    expect(screen.getByText('Cardiology', { selector: 'li' })).toBeInTheDocument();
    expect(screen.getByText('₹800.00')).toBeInTheDocument();
  });

  it('applies the department filter from the URL and from the select', async () => {
    vi.mocked(searchDoctors).mockResolvedValue(page([mehta]));
    renderAt('/doctors?departmentId=1');
    await screen.findByRole('link', { name: 'Dr. Kavya Mehta' });
    expect(searchDoctors).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: '1' }));

    await screen.findByRole('option', { name: 'Dermatologist' });
    await userEvent.selectOptions(screen.getByLabelText('Specialization'), 'Dermatologist');
    await waitFor(() => expect(searchDoctors).toHaveBeenLastCalledWith(
      expect.objectContaining({ departmentId: '1', specialization: 'Dermatologist', page: 0 }),
    ));
  });

  it('debounces the search box and shows an empty state', async () => {
    vi.mocked(searchDoctors).mockResolvedValue(page([]));
    renderAt('/doctors');
    await userEvent.type(screen.getByLabelText('Search doctors'), 'zzz');
    await waitFor(() => expect(searchDoctors).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'zzz' })));
    expect(await screen.findByText('No doctors found')).toBeInTheDocument();
  });

  it('shows a not-found message for an unavailable doctor profile', async () => {
    vi.mocked(getDoctor).mockRejectedValue({ status: 404, code: 'NOT_FOUND', message: 'Doctor not found: 99' });
    renderAt('/doctors/99');
    expect(await screen.findByText('Doctor not found')).toBeInTheDocument();
  });
});
