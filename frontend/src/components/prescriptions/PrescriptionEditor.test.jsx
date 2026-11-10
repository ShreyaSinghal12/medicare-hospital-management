import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import PrescriptionEditor from './PrescriptionEditor.jsx';

describe('PrescriptionEditor', () => {
  it('starts with one empty line and no suggested content', () => {
    render(<PrescriptionEditor rx={null} onSave={vi.fn()} onFinalize={vi.fn()} />);
    expect(screen.getByLabelText(/Medicine name/)).toHaveValue('');
    expect(screen.getByLabelText(/^Dosage/)).toHaveValue('');
    expect(screen.queryByRole('button', { name: /Remove medicine/ })).not.toBeInTheDocument();
  });

  it('validates required fields before saving', async () => {
    const onSave = vi.fn();
    render(<PrescriptionEditor rx={null} onSave={onSave} onFinalize={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText('Medicine name is required')).toBeInTheDocument();
    expect(screen.getByText('Dosage is required')).toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();
  });

  it('adds lines and sends a clean payload', async () => {
    const onFinalize = vi.fn();
    render(<PrescriptionEditor rx={null} onSave={vi.fn()} onFinalize={onFinalize} />);
    const fill = async (idx, name) => {
      await userEvent.type(screen.getAllByLabelText(/Medicine name/)[idx], name);
      await userEvent.type(screen.getAllByLabelText(/^Dosage/)[idx], '1 tablet');
      await userEvent.type(screen.getAllByLabelText(/^Frequency/)[idx], 'Twice daily');
      await userEvent.type(screen.getAllByLabelText(/^Duration/)[idx], '5 days');
    };
    await fill(0, 'Drug A');
    await userEvent.click(screen.getByRole('button', { name: /Add medicine/ }));
    await fill(1, 'Drug B');
    await userEvent.type(screen.getAllByLabelText('Qty')[1], '10');
    await userEvent.click(screen.getByRole('button', { name: 'Finalize prescription' }));
    await waitFor(() => expect(onFinalize).toHaveBeenCalled());
    expect(onFinalize.mock.calls[0][0]).toEqual({
      generalInstructions: null,
      items: [
        { medicineName: 'Drug A', strength: null, dosage: '1 tablet', frequency: 'Twice daily', duration: '5 days', instructions: null, quantity: null },
        { medicineName: 'Drug B', strength: null, dosage: '1 tablet', frequency: 'Twice daily', duration: '5 days', instructions: null, quantity: 10 },
      ],
    });
  });
});
