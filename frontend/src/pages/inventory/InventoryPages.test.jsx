import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adjustBatch, dispense, getInventoryAlerts, getMedicine, listTransactions, lookupPrescriptionForDispensing, receiveStock,
} from '../../services/inventoryService.js';
import DispensePage from './DispensePage.jsx';
import InventoryAlertsPage from './InventoryAlertsPage.jsx';
import MedicineDetailPage from './MedicineDetailPage.jsx';

vi.mock('../../services/inventoryService.js', async (orig) => ({
  ...(await orig()),
  getMedicine: vi.fn(),
  listTransactions: vi.fn(),
  receiveStock: vi.fn(),
  adjustBatch: vi.fn(),
  writeOffBatch: vi.fn(),
  setMedicineActive: vi.fn(),
  getInventoryAlerts: vi.fn(),
  lookupPrescriptionForDispensing: vi.fn(),
  dispense: vi.fn(),
}));

const rxView = {
  prescriptionId: 5, prescriptionNo: 'RX-20301010-000005', versionNumber: 1, finalizedAt: '2030-10-10T05:00:00Z',
  patientName: 'Test Patient', patientCode: 'PAT-000009', doctorName: 'Dr. Test', previousDispensings: [],
  items: [
    { prescriptionItemId: 11, medicineName: 'Paracetamol (fictional)', strength: '500 mg', prescribedQuantity: 10, dispensedQuantity: 0,
      remainingQuantity: 10, fullyDispensed: false,
      candidates: [{ medicineId: 1, name: 'Paracetamol', strength: '500 mg', unit: 'tablet', unitPrice: 2,
        batches: [{ batchId: 101, batchNumber: 'PCM-01', expiryDate: '2030-11-01', available: 4 }, { batchId: 102, batchNumber: 'PCM-02', expiryDate: '2031-06-01', available: 50 }] }] },
    { prescriptionItemId: 12, medicineName: 'Unlisted cream', strength: null, prescribedQuantity: 1, dispensedQuantity: 0,
      remainingQuantity: 1, fullyDispensed: false, candidates: [] },
  ],
};

function renderRoutes(path, routes) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>{routes}</Routes></MemoryRouter>);
}

describe('DispensePage', () => {
  beforeEach(() => vi.clearAllMocks());

  async function lookUp() {
    vi.mocked(lookupPrescriptionForDispensing).mockResolvedValue(rxView);
    renderRoutes('/admin/dispensing/new', <>
      <Route path="/admin/dispensing/new" element={<DispensePage />} />
      <Route path="/admin/dispensing/:id" element={<p>record page</p>} />
    </>);
    await userEvent.type(screen.getByLabelText('Prescription number'), 'RX-20301010-000005');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await screen.findByText(/Test Patient/);
  }

  it('shows only matching medicines and explains unmatched lines', async () => {
    await lookUp();
    expect(screen.getByText(/No matching medicine in the catalog/)).toBeInTheDocument();
    const medicine = screen.getByLabelText('Medicine for Paracetamol (fictional)');
    expect(within(medicine).getAllByRole('option')).toHaveLength(2);
    expect(screen.getByLabelText('Batch for Paracetamol (fictional)')).toBeDisabled();
  });

  it('blocks more than the batch holds or the prescription allows, then submits batch-wise lines', async () => {
    await lookUp();
    await userEvent.selectOptions(screen.getByLabelText('Medicine for Paracetamol (fictional)'), '1');
    await userEvent.selectOptions(screen.getByLabelText('Batch for Paracetamol (fictional)'), '101');
    await userEvent.type(screen.getByLabelText('Quantity for Paracetamol (fictional)'), '6');
    await userEvent.click(screen.getByRole('button', { name: 'Record dispensing' }));
    expect(await screen.findByText(/Batch stock exceeded/)).toBeInTheDocument();
    expect(dispense).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText('Quantity for Paracetamol (fictional)'));
    await userEvent.type(screen.getByLabelText('Quantity for Paracetamol (fictional)'), '4');
    await userEvent.click(screen.getByRole('button', { name: 'Another batch' }));
    const medicines = screen.getAllByLabelText('Medicine for Paracetamol (fictional)');
    await userEvent.selectOptions(medicines[1], '1');
    await userEvent.selectOptions(screen.getAllByLabelText('Batch for Paracetamol (fictional)')[1], '102');
    await userEvent.type(screen.getAllByLabelText('Quantity for Paracetamol (fictional)')[1], '7');
    await userEvent.click(screen.getByRole('button', { name: 'Record dispensing' }));
    expect(await screen.findByText('At most 10 can still be dispensed')).toBeInTheDocument();

    await userEvent.clear(screen.getAllByLabelText('Quantity for Paracetamol (fictional)')[1]);
    await userEvent.type(screen.getAllByLabelText('Quantity for Paracetamol (fictional)')[1], '6');
    vi.mocked(dispense).mockResolvedValue({ id: 77, dispensingNo: 'DSP-1' });
    await userEvent.click(screen.getByRole('button', { name: 'Record dispensing' }));
    await waitFor(() => expect(dispense).toHaveBeenCalledWith({
      prescriptionId: 5, notes: null,
      lines: [{ prescriptionItemId: 11, batchId: 101, quantity: 4 }, { prescriptionItemId: 11, batchId: 102, quantity: 6 }],
    }));
    expect(await screen.findByText('record page')).toBeInTheDocument();
  });

  it('shows the server message when the prescription is not finalized', async () => {
    vi.mocked(lookupPrescriptionForDispensing).mockRejectedValue({ status: 404, code: 'RESOURCE_NOT_FOUND', message: 'No finalized prescription with number RX-X.' });
    renderRoutes('/admin/dispensing/new', <Route path="/admin/dispensing/new" element={<DispensePage />} />);
    await userEvent.type(screen.getByLabelText('Prescription number'), 'RX-X');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    expect(await screen.findByText(/No finalized prescription/)).toBeInTheDocument();
  });
});

describe('MedicineDetailPage', () => {
  const detail = {
    expiryWarningDays: 60,
    medicine: { id: 1, name: 'Cetirizine', genericName: 'Cetirizine', manufacturer: 'Demo Pharma', category: 'TABLET', strength: '10 mg',
      unit: 'tablet', unitPrice: 3.5, reorderLevel: 30, active: true, availableQuantity: 20, expiredQuantity: 0, nearestExpiry: '2031-01-01', lowStock: true },
    batches: [{ id: 9, batchNumber: 'CTZ-01', expiryDate: '2031-01-01', quantityReceived: 20, quantityAvailable: 20, purchasePrice: null,
      supplier: 'Demo Supplier', receivedAt: '2030-01-01T00:00:00Z', expired: false, expiringSoon: false }],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getMedicine).mockResolvedValue(detail);
    vi.mocked(listTransactions).mockResolvedValue({ content: [], page: 0, totalPages: 0, totalElements: 0 });
  });

  const open = () => renderRoutes('/admin/inventory/medicines/1', <Route path="/admin/inventory/medicines/:id" element={<MedicineDetailPage />} />);

  it('validates and sends a stock receipt', async () => {
    vi.mocked(receiveStock).mockResolvedValue(detail);
    open();
    await userEvent.click(await screen.findByRole('button', { name: /Receive stock/ }));
    const dialog = screen.getByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Receive stock' }));
    expect((await within(dialog).findAllByText('Required')).length).toBe(3);
    await userEvent.type(within(dialog).getByLabelText(/Batch number/), 'CTZ-02');
    await userEvent.type(within(dialog).getByLabelText(/Expiry date/), '2031-05-05');
    await userEvent.type(within(dialog).getByLabelText(/Quantity/), '100');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Receive stock' }));
    await waitFor(() => expect(receiveStock).toHaveBeenCalledWith(1, expect.objectContaining({
      batchNumber: 'CTZ-02', expiryDate: '2031-05-05', quantity: 100, purchasePrice: null,
    })));
  });

  it('shows the server stock error on the quantity field', async () => {
    vi.mocked(adjustBatch).mockRejectedValue({ status: 422, code: 'INSUFFICIENT_STOCK', message: 'Batch CTZ-01 has only 20 tablet left', fieldErrors: [] });
    open();
    await userEvent.click(await screen.findByRole('button', { name: 'Adjust batch CTZ-01' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByLabelText(/Adjustment/), 'ADJUSTMENT_OUT');
    await userEvent.type(within(dialog).getByLabelText(/Quantity/), '25');
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Damaged');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save adjustment' }));
    expect(await within(dialog).findByText('Batch CTZ-01 has only 20 tablet left')).toBeInTheDocument();
  });
});

describe('InventoryAlertsPage', () => {
  it('lists low stock, expiring and expired batches', async () => {
    vi.mocked(getInventoryAlerts).mockResolvedValue({
      expiryWarningDays: 60,
      lowStock: [{ id: 1, name: 'Cetirizine', strength: '10 mg', unit: 'tablet', availableQuantity: 0, reorderLevel: 30 }],
      expiringSoon: [{ batchId: 2, medicineId: 3, medicineName: 'Paracetamol', strength: '500 mg', unit: 'tablet', batchNumber: 'PCM-01', expiryDate: '2030-11-01', quantityAvailable: 40, daysLeft: 20 }],
      expired: [{ batchId: 4, medicineId: 5, medicineName: 'Amoxicillin', strength: '250 mg', unit: 'capsule', batchNumber: 'AMX-00', expiryDate: '2030-09-01', quantityAvailable: 15, daysLeft: -10 }],
    });
    render(<MemoryRouter><InventoryAlertsPage /></MemoryRouter>);
    expect(await screen.findByText('0 tablet(s) left · reorder at 30')).toBeInTheDocument();
    expect(screen.getByText(/Batch PCM-01 · 40 tablet\(s\) · expires .* \(in 20 day\(s\)\)/)).toBeInTheDocument();
    expect(screen.getByText(/Batch AMX-00 · 15 capsule\(s\) · expired/)).toBeInTheDocument();
  });
});
