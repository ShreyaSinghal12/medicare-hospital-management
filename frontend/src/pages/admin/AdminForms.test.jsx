import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminCreateDepartment, adminCreateDoctor, adminListDepartments,
} from '../../services/adminService.js';
import DepartmentsAdminPage from './DepartmentsAdminPage.jsx';
import DoctorFormPage from './DoctorFormPage.jsx';

vi.mock('../../services/adminService.js', () => ({
  adminListDepartments: vi.fn(),
  adminCreateDepartment: vi.fn(),
  adminUpdateDepartment: vi.fn(),
  adminSetDepartmentActive: vi.fn(),
  adminCreateDoctor: vi.fn(),
  adminUpdateDoctor: vi.fn(),
  adminGetDoctor: vi.fn(),
}));

// jsdom lacks <dialog>.showModal; the Modal component falls back to the "open" attribute.
const departments = [
  { id: 1, name: 'Cardiology', description: 'Heart', active: true, activeDoctorCount: 2 },
  { id: 2, name: 'Closed Dept', description: '', active: false, activeDoctorCount: 0 },
];

function renderDoctorForm() {
  return render(
    <MemoryRouter initialEntries={['/admin/doctors/new']}>
      <Routes>
        <Route path="/admin/doctors/new" element={<DoctorFormPage />} />
        <Route path="/admin/doctors" element={<h1>Doctor list</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillDoctor() {
  await userEvent.type(screen.getByLabelText(/Full name/), 'Dr. Test Person');
  await userEvent.type(screen.getByLabelText(/E-mail/), 'dr.test@test.example');
  await userEvent.type(screen.getByLabelText(/Phone/), '+91 98888 77777');
  await screen.findByRole('option', { name: 'Cardiology' });
  await userEvent.selectOptions(screen.getByLabelText(/Department/), '1');
  await userEvent.type(screen.getByLabelText(/Specialization/), 'Cardiologist');
  await userEvent.type(screen.getByLabelText(/Qualifications/), 'MBBS, MD');
  await userEvent.type(screen.getByLabelText(/licence number/i), 'REG-1234');
  await userEvent.type(screen.getByLabelText(/Consultation fee/), '650.5');
}

describe('Doctor form', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminListDepartments).mockResolvedValue(departments);
  });

  it('offers only active departments and validates required fields', async () => {
    renderDoctorForm();
    await screen.findByRole('option', { name: 'Cardiology' });
    expect(screen.queryByRole('option', { name: 'Closed Dept' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Create doctor' }));
    expect(await screen.findByText('Department is required')).toBeInTheDocument();
    expect(screen.getByText('Fee is required')).toBeInTheDocument();
    expect(adminCreateDoctor).not.toHaveBeenCalled();
  });

  it('shows a duplicate-licence error from the server on the licence field', async () => {
    vi.mocked(adminCreateDoctor).mockRejectedValue({
      status: 409, code: 'LICENSE_ALREADY_REGISTERED', message: 'A doctor with this licence number already exists', fieldErrors: [],
    });
    renderDoctorForm();
    await fillDoctor();
    await userEvent.click(screen.getByRole('button', { name: 'Create doctor' }));
    expect(await screen.findByText('A doctor with this licence number already exists')).toBeInTheDocument();
  });

  it('sends numeric values and returns to the list on success', async () => {
    vi.mocked(adminCreateDoctor).mockResolvedValue({ fullName: 'Dr. Test Person', email: 'dr.test@test.example' });
    renderDoctorForm();
    await fillDoctor();
    await userEvent.click(screen.getByRole('button', { name: 'Create doctor' }));
    expect(await screen.findByRole('heading', { name: 'Doctor list' })).toBeInTheDocument();
    expect(adminCreateDoctor).toHaveBeenCalledWith(expect.objectContaining({
      departmentId: 1, consultationFee: 650.5, experienceYears: 0, slotDurationMinutes: 15, licenseNumber: 'REG-1234',
    }));
  });
});

describe('Departments admin', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminListDepartments).mockResolvedValue(departments);
  });

  it('lists departments with status and doctor counts', async () => {
    render(<MemoryRouter><DepartmentsAdminPage /></MemoryRouter>);
    expect(await screen.findByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('2 active doctor(s)')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('shows a duplicate-name error inside the create dialog', async () => {
    vi.mocked(adminCreateDepartment).mockRejectedValue({
      status: 409, code: 'DEPARTMENT_EXISTS', message: "A department named 'Cardiology' already exists", fieldErrors: [],
    });
    render(<MemoryRouter><DepartmentsAdminPage /></MemoryRouter>);
    await screen.findByText('Cardiology');
    await userEvent.click(screen.getByRole('button', { name: 'Add department' }));
    await userEvent.type(await screen.findByLabelText(/^Name/), 'Cardiology');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));
    expect(await screen.findByText("A department named 'Cardiology' already exists")).toBeInTheDocument();
    await waitFor(() => expect(adminCreateDepartment).toHaveBeenCalledWith({ name: 'Cardiology', description: '' }));
  });
});
