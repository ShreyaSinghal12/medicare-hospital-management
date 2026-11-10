import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { getMyPrescriptions } from '../../services/prescriptionService.js';
import PatientPrescriptionPage from './PatientPrescriptionPage.jsx';
import PrescriptionListPage from './PrescriptionListPage.jsx';
import { Route, Routes } from 'react-router-dom';

vi.mock('../../services/prescriptionService.js', () => ({
  getMyPrescriptions: vi.fn().mockResolvedValue({
    content: [{ id: 4, prescriptionNo: 'RX-20261110-000004', versionNumber: 2, status: 'FINALIZED', consultationDate: '2026-11-10',
      doctorId: 1, doctorName: 'Dr. Arjun Rao', specialization: 'General Physician', medicines: ['Drug A', 'Drug B'] }],
    page: 0, totalPages: 1, totalElements: 1, first: true, last: true,
  }),
  getIssuedPrescriptions: vi.fn(),
  getPrescription: vi.fn().mockResolvedValue({
    id: 4, prescriptionNo: 'RX-20261110-000004', versionNumber: 2, status: 'SUPERSEDED', consultationDate: '2026-11-10',
    doctorName: 'Dr. Arjun Rao', specialization: 'General Physician', items: [{ medicineName: 'Drug A', dosage: '1', frequency: 'BD', duration: '5 days' }],
  }),
  getPrescriptionVersions: vi.fn().mockResolvedValue([]),
  prescriptionPdfUrl: (id) => `/api/prescriptions/${id}/pdf`,
}));

describe('Patient prescriptions', () => {
  it('lists prescriptions and passes search and superseded filters to the API', async () => {
    render(<MemoryRouter><PrescriptionListPage mode="patient" /></MemoryRouter>);
    expect(await screen.findByRole('link', { name: 'RX-20261110-000004' })).toHaveAttribute('href', '/patient/prescriptions/4');
    expect(screen.getByText('Drug A, Drug B')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Search'), 'drug');
    await userEvent.click(screen.getByLabelText('Include superseded versions'));
    await waitFor(() => expect(getMyPrescriptions).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'drug', includeSuperseded: true })));
  });

  it('shows a superseded warning and the PDF download link', async () => {
    render(
      <MemoryRouter initialEntries={['/patient/prescriptions/4']}>
        <Routes><Route path="/patient/prescriptions/:id" element={<PatientPrescriptionPage />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText(/replaced by a newer one/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Download PDF/ })).toHaveAttribute('href', '/api/prescriptions/4/pdf');
  });
});
