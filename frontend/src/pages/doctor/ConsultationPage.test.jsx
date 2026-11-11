import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  amendConsultation, finalizeConsultation, getConsultation, saveConsultationDraft,
} from '../../services/clinicalService.js';
import ConsultationPage from './ConsultationPage.jsx';

vi.mock('../../services/labService.js', () => ({
  getConsultationLabOrders: vi.fn().mockResolvedValue([]),
  getLabCatalog: vi.fn().mockResolvedValue([]),
  createLabOrder: vi.fn(),
}));
vi.mock('../../services/prescriptionService.js', () => ({
  getConsultationPrescriptions: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../services/clinicalService.js', () => ({
  getConsultation: vi.fn(),
  getPatientRecord: vi.fn().mockResolvedValue({ allergies: [], conditions: [] }),
  getAmendments: vi.fn().mockResolvedValue([]),
  saveConsultationDraft: vi.fn(),
  finalizeConsultation: vi.fn(),
  amendConsultation: vi.fn(),
  addAllergy: vi.fn(), addCondition: vi.fn(), setAllergyActive: vi.fn(), setConditionStatus: vi.fn(),
}));

const base = {
  id: 9, appointmentId: 4, appointmentReference: 'APT-1', appointmentStatus: 'IN_PROGRESS', date: '2030-01-07', startTime: '10:00',
  visitReason: 'Headache', patientId: 3, patientName: 'Ravi Kumar', patientCode: 'PAT-000001', doctorId: 1,
  doctorName: 'Dr. Mehta', symptoms: null, observations: null, diagnosis: null, treatmentPlan: null, notes: null,
  followUpDate: null, followUpNotes: null, status: 'DRAFT', amendmentCount: 0, version: 2, canEdit: true, canAmend: false,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/doctor/consultations/9']}>
      <Routes><Route path="/doctor/consultations/:id" element={<ConsultationPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('ConsultationPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('saves a draft with the loaded version and blank fields as null', async () => {
    vi.mocked(getConsultation).mockResolvedValue(base);
    vi.mocked(saveConsultationDraft).mockResolvedValue({ ...base, version: 3 });
    renderPage();
    await userEvent.type(await screen.findByLabelText(/Symptoms/), 'Fever');
    await userEvent.click(screen.getByRole('button', { name: /Save draft/ }));
    await waitFor(() => expect(saveConsultationDraft).toHaveBeenCalledWith('9', expect.objectContaining({
      symptoms: 'Fever', diagnosis: null, followUpDate: null, version: 2,
    })));
  });

  it('asks for confirmation before finalizing', async () => {
    vi.mocked(getConsultation).mockResolvedValue({ ...base, diagnosis: 'Viral fever' });
    vi.mocked(finalizeConsultation).mockResolvedValue({ ...base, status: 'FINALIZED' });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Finalize consultation' }));
    expect(await screen.findByText('Finalize this consultation?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Finalize' }));
    await waitFor(() => expect(finalizeConsultation).toHaveBeenCalledWith('9', expect.objectContaining({ diagnosis: 'Viral fever', version: 2 })));
  });

  it('shows finalized notes read-only and requires a reason to amend', async () => {
    vi.mocked(getConsultation).mockResolvedValue({ ...base, status: 'FINALIZED', diagnosis: 'Migraine', canEdit: false, canAmend: true });
    renderPage();
    expect(await screen.findByLabelText(/Diagnosis/)).toBeDisabled();
    expect(screen.queryByRole('button', { name: /Save draft/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Amend notes' }));
    expect(screen.getByLabelText(/Diagnosis/)).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: 'Save amendment' }));
    expect(await screen.findByText('Give a reason of at least 5 characters')).toBeInTheDocument();
    expect(amendConsultation).not.toHaveBeenCalled();
  });
});
