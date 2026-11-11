import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderLabTestsDialog from '../../components/lab/OrderLabTestsDialog.jsx';
import {
  adminCreateLabTest, createLabOrder, finalizeLabReport, getLabCatalog, getLabOrder, saveLabResults, uploadLabReport,
} from '../../services/labService.js';
import { LabTestForm } from '../admin/LabCatalogPage.jsx';
import DoctorLabOrderPage from './DoctorLabOrderPage.jsx';
import PatientLabReportPage from './PatientLabReportPage.jsx';

vi.mock('../../services/labService.js', () => ({
  getLabOrder: vi.fn(),
  changeLabOrderStatus: vi.fn(),
  saveLabResults: vi.fn(),
  uploadLabReport: vi.fn(),
  removeLabReport: vi.fn(),
  finalizeLabReport: vi.fn(),
  labReportUrl: (id) => `/api/lab-orders/${id}/report`,
  getLabCatalog: vi.fn(),
  createLabOrder: vi.fn(),
  adminCreateLabTest: vi.fn(),
  adminUpdateLabTest: vi.fn(),
}));

const order = (over = {}) => ({
  id: 7, orderNo: 'LAB-20301010-000007', status: 'SAMPLE_COLLECTED', priority: 'ROUTINE', clinicalNotes: 'Fictional note',
  consultationId: 3, appointmentId: 2, visitDate: '2030-10-10', patientId: 4, patientName: 'Test Patient', patientCode: 'PAT-000004',
  doctorId: 1, doctorName: 'Dr. Test Doctor', specialization: 'Physician', orderedAt: '2030-10-10T05:00:00Z',
  sampleCollectedAt: '2030-10-10T05:30:00Z', completedAt: null, cancelledAt: null, cancellationReason: null,
  items: [{ id: 11, testId: 1, testName: 'Fasting Blood Sugar', price: 150, unit: 'mg/dL', referenceRange: '70–100', resultValue: null, resultComment: null }],
  report: null, version: 3, canManage: true, allowedTransitions: ['PROCESSING', 'CANCELLED'], ...over,
});

function renderAt(path, element, pattern) {
  return render(<MemoryRouter initialEntries={[path]}><Routes><Route path={pattern} element={element} /></Routes></MemoryRouter>);
}

describe('DoctorLabOrderPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('saves results typed by the doctor with the loaded version', async () => {
    vi.mocked(getLabOrder).mockResolvedValue(order());
    vi.mocked(saveLabResults).mockResolvedValue(order({ version: 4, items: [{ ...order().items[0], resultValue: '92' }] }));
    renderAt('/doctor/lab-orders/7', <DoctorLabOrderPage />, '/doctor/lab-orders/:id');

    const input = await screen.findByLabelText('Result for Fasting Blood Sugar');
    expect(screen.getByText('Reference: 70–100 mg/dL')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finalize and release to patient' })).toBeDisabled();
    await userEvent.type(input, '92');
    await userEvent.click(screen.getByRole('button', { name: 'Save results' }));
    await waitFor(() => expect(saveLabResults).toHaveBeenCalledWith(7, 3, [{ itemId: 11, resultValue: '92', resultComment: '' }]));
  });

  it('checks the file in the browser before uploading', async () => {
    vi.mocked(getLabOrder).mockResolvedValue(order());
    vi.mocked(uploadLabReport).mockResolvedValue(order({ status: 'PROCESSING', version: 4,
      report: { fileName: 'r.pdf', sizeBytes: 2048, uploadedAt: '2030-10-10T06:00:00Z', uploadedBy: 'Dr. Test Doctor', finalized: false, finalizedAt: null } }));
    renderAt('/doctor/lab-orders/7', <DoctorLabOrderPage />, '/doctor/lab-orders/:id');

    const input = await screen.findByLabelText(/Upload report/);
    await userEvent.upload(input, new File(['hello'], 'notes.txt', { type: 'text/plain' }), { applyAccept: false });
    expect(screen.getByRole('alert')).toHaveTextContent('Choose a PDF file (.pdf)');
    expect(uploadLabReport).not.toHaveBeenCalled();

    const pdf = new File(['%PDF-1.4 test %%EOF'], 'r.pdf', { type: 'application/pdf' });
    await userEvent.upload(input, pdf);
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(uploadLabReport).toHaveBeenCalledWith(7, 3, pdf));
    expect(await screen.findByText('r.pdf')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finalize and release to patient' })).toBeEnabled();
  });

  it('finalizes after confirmation', async () => {
    const withReport = order({ status: 'PROCESSING', allowedTransitions: ['CANCELLED'],
      report: { fileName: 'r.pdf', sizeBytes: 2048, uploadedAt: '2030-10-10T06:00:00Z', uploadedBy: 'Dr. Test Doctor', finalized: false, finalizedAt: null } });
    vi.mocked(getLabOrder).mockResolvedValue(withReport);
    vi.mocked(finalizeLabReport).mockResolvedValue({ ...withReport, status: 'COMPLETED', canManage: false, allowedTransitions: [] });
    renderAt('/doctor/lab-orders/7', <DoctorLabOrderPage />, '/doctor/lab-orders/:id');

    await userEvent.click(await screen.findByRole('button', { name: 'Finalize and release to patient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Finalize' }));
    await waitFor(() => expect(finalizeLabReport).toHaveBeenCalledWith(7, 3));
    expect(await screen.findByText('Read-only')).toBeInTheDocument();
  });
});

describe('PatientLabReportPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows only a waiting message before the report is released', async () => {
    vi.mocked(getLabOrder).mockResolvedValue(order({ canManage: false, clinicalNotes: null, allowedTransitions: [] }));
    renderAt('/patient/lab-reports/7', <PatientLabReportPage />, '/patient/lab-reports/:id');
    expect(await screen.findByText(/will appear here once your doctor/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Download report/ })).not.toBeInTheDocument();
  });

  it('shows results and the download once completed', async () => {
    vi.mocked(getLabOrder).mockResolvedValue(order({ status: 'COMPLETED', completedAt: '2030-10-11T05:00:00Z', canManage: false,
      clinicalNotes: null, allowedTransitions: [], items: [{ ...order().items[0], resultValue: '92', resultComment: 'Fasting' }],
      report: { fileName: 'r.pdf', sizeBytes: 2048, uploadedAt: '2030-10-10T06:00:00Z', uploadedBy: 'Dr. Test Doctor', finalized: true, finalizedAt: '2030-10-11T05:00:00Z' } }));
    renderAt('/patient/lab-reports/7', <PatientLabReportPage />, '/patient/lab-reports/:id');
    expect(await screen.findByText('92 mg/dL')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Download report/ })).toHaveAttribute('href', '/api/lab-orders/7/report');
    expect(screen.getByText(/does not interpret results/)).toBeInTheDocument();
  });
});

describe('OrderLabTestsDialog', () => {
  beforeEach(() => vi.clearAllMocks());

  it('requires a test and orders the selected ones', async () => {
    vi.mocked(getLabCatalog).mockResolvedValue([
      { id: 1, code: 'FBS', name: 'Fasting Blood Sugar', price: 150, sampleType: 'Blood', turnaroundHours: 6 },
      { id: 2, code: 'TSH', name: 'Thyroid Stimulating Hormone', price: 400, sampleType: 'Blood', turnaroundHours: 24 },
    ]);
    vi.mocked(createLabOrder).mockResolvedValue({ id: 9, orderNo: 'LAB-1' });
    render(<MemoryRouter><OrderLabTestsDialog consultationId={3} open onClose={() => {}} /></MemoryRouter>);

    await screen.findByText('Thyroid Stimulating Hormone');
    expect(screen.queryByRole('checkbox', { checked: true })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Select at least one test');

    await userEvent.click(screen.getByRole('checkbox', { name: /Thyroid/ }));
    expect(screen.getByText(/1 test\(s\)/)).toHaveTextContent('₹400');
    await userEvent.selectOptions(screen.getByLabelText('Priority'), 'URGENT');
    await userEvent.click(screen.getByRole('button', { name: 'Place order' }));
    await waitFor(() => expect(createLabOrder).toHaveBeenCalledWith({ consultationId: 3, testIds: [2], priority: 'URGENT', clinicalNotes: null }));
  });
});

describe('LabTestForm', () => {
  beforeEach(() => vi.clearAllMocks());

  it('validates and submits a new catalog test', async () => {
    vi.mocked(adminCreateLabTest).mockResolvedValue({ id: 5, name: 'Vitamin D' });
    const onSaved = vi.fn();
    render(<LabTestForm onSaved={onSaved} onCancel={() => {}} />);
    await userEvent.type(screen.getByLabelText(/^Code/), 'v d!');
    await userEvent.type(screen.getByLabelText(/^Price/), '-5');
    await userEvent.click(screen.getByRole('button', { name: 'Add test' }));
    expect(await screen.findByText('2–20 letters, digits or hyphens')).toBeInTheDocument();
    expect(screen.getByText('Cannot be negative')).toBeInTheDocument();
    expect(adminCreateLabTest).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText(/^Code/));
    await userEvent.type(screen.getByLabelText(/^Code/), 'vitd');
    await userEvent.type(screen.getByLabelText(/^Name/), 'Vitamin D');
    await userEvent.clear(screen.getByLabelText(/^Price/));
    await userEvent.type(screen.getByLabelText(/^Price/), '900');
    await userEvent.click(screen.getByRole('button', { name: 'Add test' }));
    await waitFor(() => expect(adminCreateLabTest).toHaveBeenCalledWith(expect.objectContaining({ code: 'VITD', name: 'Vitamin D', price: 900, turnaroundHours: 24 })));
    expect(onSaved).toHaveBeenCalled();
  });
});
