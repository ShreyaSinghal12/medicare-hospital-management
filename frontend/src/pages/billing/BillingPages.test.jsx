import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createInvoice, getBillableCharges, getInvoice, recordPayment } from '../../services/billingService.js';
import InvoicePage from './InvoicePage.jsx';
import NewInvoicePage from './NewInvoicePage.jsx';

vi.mock('../../services/billingService.js', async (orig) => ({
  ...(await orig()),
  getBillableCharges: vi.fn(),
  createInvoice: vi.fn(),
  getInvoice: vi.fn(),
  recordPayment: vi.fn(),
  recordRefund: vi.fn(),
  cancelInvoice: vi.fn(),
}));
vi.mock('../../services/receptionService.js', () => ({ searchReceptionPatients: vi.fn() }));

const billable = {
  patientId: 4, patientName: 'Test Patient', patientCode: 'PAT-000004', taxRatePercent: 5, outstandingBalance: 0,
  charges: [
    { type: 'CONSULTATION', referenceId: 10, description: 'Consultation - Dr. Test (General)', quantity: 1, unitPrice: 500, lineTotal: 500, serviceDate: '2030-10-10', source: 'APT-1' },
    { type: 'PHARMACY', referenceId: 20, description: 'Medicine - Paracetamol 500 mg', quantity: 10, unitPrice: 2, lineTotal: 20, serviceDate: '2030-10-10', source: 'DSP-1' },
  ],
};

const invoice = (over = {}) => ({
  id: 7, invoiceNo: 'INV-20301010-000007', status: 'UNPAID', patientId: 4, patientName: 'Test Patient', patientCode: 'PAT-000004',
  subtotal: 520, discountAmount: 0, taxRatePercent: 0, taxAmount: 0, totalAmount: 520, amountPaid: 0, refundedAmount: 0, balanceDue: 520, notes: null,
  issuedBy: 'Test Desk', issuedAt: '2030-10-10T06:00:00Z', cancelledAt: null, cancellationReason: null,
  items: [{ id: 1, type: 'CONSULTATION', description: 'Consultation - Dr. Test', quantity: 1, unitPrice: 500, lineTotal: 500 }],
  payments: [], canPay: true, canRefund: false, canCancel: true, ...over,
});

function renderAt(path, pattern, element) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path={pattern} element={element} />
    <Route path="/reception/billing/:id" element={<p>invoice page</p>} />
  </Routes></MemoryRouter>);
}

describe('NewInvoicePage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('preselects unbilled charges, previews tax and sends only charge references', async () => {
    vi.mocked(getBillableCharges).mockResolvedValue(billable);
    vi.mocked(createInvoice).mockResolvedValue({ id: 7, invoiceNo: 'INV-1' });
    renderAt('/reception/billing/new?patientId=4', '/reception/billing/new', <NewInvoicePage />);

    expect(await screen.findByText('Consultation - Dr. Test (General)')).toBeInTheDocument();
    // 520 subtotal, 5% tax = 26, total 546
    expect(screen.getByText('Tax (5%)').nextSibling).toHaveTextContent('26.00');
    expect(screen.getByText('Total').nextSibling).toHaveTextContent('546.00');

    await userEvent.click(screen.getByRole('checkbox', { name: /Paracetamol/ }));
    await userEvent.type(screen.getByLabelText('Discount (₹)'), '50');
    await userEvent.click(screen.getByRole('button', { name: 'Create invoice' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Give the reason for the discount');
    expect(createInvoice).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText(/^Notes/), 'Staff family (fictional)');
    await userEvent.click(screen.getByRole('button', { name: 'Create invoice' }));
    await waitFor(() => expect(createInvoice).toHaveBeenCalledWith({
      patientId: 4, charges: [{ type: 'CONSULTATION', referenceId: 10 }], otherItems: [], discountAmount: 50, notes: 'Staff family (fictional)',
    }));
    expect(await screen.findByText('invoice page')).toBeInTheDocument();
  });

  it('rejects an incomplete extra item', async () => {
    vi.mocked(getBillableCharges).mockResolvedValue({ ...billable, charges: [] });
    renderAt('/reception/billing/new?patientId=4', '/reception/billing/new', <NewInvoicePage />);
    expect(await screen.findByText('Nothing left to bill for this patient.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add extra item' }));
    await userEvent.type(screen.getByLabelText('Extra item'), 'Dressing');
    await userEvent.click(screen.getByRole('button', { name: 'Create invoice' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Select at least one charge or add an item');
    await userEvent.type(screen.getByLabelText('Price (₹)'), '40');
    vi.mocked(createInvoice).mockResolvedValue({ id: 8, invoiceNo: 'INV-2' });
    await userEvent.click(screen.getByRole('button', { name: 'Create invoice' }));
    await waitFor(() => expect(createInvoice).toHaveBeenCalledWith(expect.objectContaining({
      charges: [], otherItems: [{ description: 'Dressing', quantity: 1, unitPrice: 40 }],
    })));
  });
});

describe('InvoicePage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('blocks card numbers and overpayment before calling the server', async () => {
    vi.mocked(getInvoice).mockResolvedValue(invoice());
    vi.mocked(recordPayment).mockResolvedValue(invoice({ status: 'PARTIALLY_PAID', amountPaid: 200, balanceDue: 320, canCancel: false,
      payments: [{ id: 1, paymentNo: 'PAY-1', type: 'PAYMENT', method: 'UPI_SIMULATED', amount: 200, transactionReference: 'UPI-77', notes: null, recordedBy: 'Test Desk', paidAt: '2030-10-10T07:00:00Z' }] }));
    renderAt('/reception/billing/7', '/reception/billing/:id', <InvoicePage mode="reception" />);

    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByLabelText(/Method/), 'CARD_SIMULATED');
    await userEvent.clear(within(dialog).getByLabelText(/Amount/));
    await userEvent.type(within(dialog).getByLabelText(/Amount/), '600');
    await userEvent.type(within(dialog).getByLabelText(/Transaction reference/), '4111 1111 1111 1111');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    expect(await within(dialog).findByText('Do not enter card numbers')).toBeInTheDocument();
    expect(within(dialog).getByText(/At most/)).toBeInTheDocument();
    expect(recordPayment).not.toHaveBeenCalled();

    await userEvent.selectOptions(within(dialog).getByLabelText(/Method/), 'UPI_SIMULATED');
    await userEvent.clear(within(dialog).getByLabelText(/Amount/));
    await userEvent.type(within(dialog).getByLabelText(/Amount/), '200');
    await userEvent.clear(within(dialog).getByLabelText(/Transaction reference/));
    await userEvent.type(within(dialog).getByLabelText(/Transaction reference/), 'UPI-77');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    await waitFor(() => expect(recordPayment).toHaveBeenCalledWith(7, { method: 'UPI_SIMULATED', amount: 200, transactionReference: 'UPI-77', notes: null }));
    expect(await screen.findByText('Payment PAY-1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel invoice' })).not.toBeInTheDocument();

    // regression: reopening starts from fresh defaults (cash, the new balance), not the previous entry
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    const again = screen.getByRole('dialog');
    expect(within(again).getByLabelText(/Amount/)).toHaveValue(320);
    expect(within(again).getByLabelText(/Method/)).toHaveValue('CASH');
    expect(within(again).getByLabelText(/Transaction reference/)).toHaveValue('');
  });

  it('limits a refund to what is still refundable and shows refunds separately', async () => {
    vi.mocked(getInvoice).mockResolvedValue(invoice({ status: 'PAID', amountPaid: 520, refundedAmount: 120, balanceDue: 0,
      canPay: false, canRefund: true, canCancel: false }));
    renderAt('/reception/billing/7', '/reception/billing/:id', <InvoicePage mode="reception" />);
    expect(await screen.findByText('Refunded')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Refund' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByLabelText(/Amount/)).toHaveValue(400);
    await userEvent.clear(within(dialog).getByLabelText(/Amount/));
    await userEvent.type(within(dialog).getByLabelText(/Amount/), '401');
    await userEvent.type(within(dialog).getByLabelText(/Reason for refund/), 'Returned medicine');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record refund' }));
    expect(await within(dialog).findByText(/At most/)).toBeInTheDocument();
  });

  it('shows no money actions to patients', async () => {
    vi.mocked(getInvoice).mockResolvedValue(invoice({ canPay: false, canRefund: false, canCancel: false }));
    renderAt('/patient/invoices/7', '/patient/invoices/:id', <InvoicePage mode="patient" />);
    expect(await screen.findByText('INV-20301010-000007')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /PDF/ })).toHaveAttribute('href', '/api/billing/invoices/7/pdf');
    expect(screen.queryByRole('button', { name: 'Record payment' })).not.toBeInTheDocument();
  });
});
