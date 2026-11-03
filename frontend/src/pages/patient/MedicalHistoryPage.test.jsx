import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { addDaysIso, todayIsoLocal } from '../../utils/dates.js';
import MedicalHistoryPage from './MedicalHistoryPage.jsx';

const followUp = addDaysIso(todayIsoLocal(), 5);
vi.mock('../../services/clinicalService.js', () => ({
  getMyMedicalHistory: vi.fn().mockImplementation(async () => ({
    allergies: [{ id: 1, allergen: 'Penicillin', severity: 'SEVERE', active: true }],
    conditions: [{ id: 2, conditionName: 'Hypertension', status: 'CHRONIC' }],
    timeline: [{
      consultationId: 5, appointmentId: 8, date: '2026-10-01', time: '10:00', doctorId: 3, doctorName: 'Dr. Mehta',
      specialization: 'Cardiologist', departmentName: 'Cardiology', symptoms: 'Chest pain', diagnosis: 'Angina (fictional)',
      treatmentPlan: 'Rest', followUpDate: followUp, followUpNotes: 'Review', amended: true,
    }],
  })),
}));

describe('MedicalHistoryPage', () => {
  it('renders the timeline, allergies and a follow-up booking link', async () => {
    render(<MemoryRouter><MedicalHistoryPage /></MemoryRouter>);
    expect(await screen.findByText('Angina (fictional)')).toBeInTheDocument();
    expect(screen.getByText('Penicillin')).toBeInTheDocument();
    expect(screen.getByText('amended')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Book follow-up/ }))
      .toHaveAttribute('href', `/patient/book/3?date=${followUp}&followUpOf=8`);
    expect(screen.queryByRole('button', { name: /Add/ })).not.toBeInTheDocument();
  });
});
