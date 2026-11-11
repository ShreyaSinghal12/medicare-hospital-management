import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { looksLikeCardNumber, PAYMENT_METHODS, recordPayment, recordRefund, sumMoney } from '../../services/billingService.js';
import { formatCurrency } from '../../utils/format.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import Button from '../ui/Button.jsx';
import { SelectField, TextAreaField, TextField } from '../ui/FormField.jsx';
import Modal from '../ui/Modal.jsx';

/** Record a payment or a refund. Never asks for card details. */
export default function MoneyDialog({ invoice, mode, onClose, onDone }) {
  const refund = mode === 'refund';
  const limit = (refund ? sumMoney([invoice?.amountPaid, -(invoice?.refundedAmount || 0)]) : Number(invoice?.balanceDue)) || 0;
  return (
    <Modal open={!!invoice && !!mode} title={refund ? 'Record refund' : 'Record payment'} onClose={onClose}
      description={invoice ? `${invoice.invoiceNo} · ${refund ? 'refundable' : 'balance due'} ${formatCurrency(limit)}` : undefined}>
      {/* Mounted only while open, so every opening starts from fresh defaults. */}
      {invoice && <MoneyForm invoice={invoice} refund={refund} limit={limit} onClose={onClose} onDone={onDone} />}
    </Modal>
  );
}

function MoneyForm({ invoice, refund, limit, onClose, onDone }) {
  const { register, handleSubmit, watch, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { method: 'CASH', amount: limit ? String(limit) : '', transactionReference: '', notes: '' },
  });
  const method = watch('method');

  async function onSubmit(v) {
    const payload = { method: v.method, amount: Number(v.amount), transactionReference: v.transactionReference.trim() || null, notes: v.notes.trim() || null };
    try {
      const updated = refund ? await recordRefund(invoice.id, payload) : await recordPayment(invoice.id, payload);
      toast.success(refund ? `Refund of ${formatCurrency(payload.amount)} recorded` : `Payment of ${formatCurrency(payload.amount)} recorded`);
      onDone(updated);
    } catch (err) {
      if (err.code === 'CARD_NUMBER_NOT_ALLOWED' || err.code === 'REFERENCE_REQUIRED') setError('transactionReference', { type: 'server', message: err.message });
      else if (['AMOUNT_EXCEEDS_BALANCE', 'REFUND_EXCEEDS_PAID'].includes(err.code)) setError('amount', { type: 'server', message: err.message });
      else if (err.code === 'REASON_REQUIRED') setError('notes', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          <SelectField label="Method" required options={Object.entries(PAYMENT_METHODS).map(([value, label]) => ({ value, label }))}
            {...register('method')} />
          <TextField label="Amount (₹)" type="number" step="0.01" min="0.01" required error={errors.amount?.message}
            {...register('amount', {
              required: 'Required',
              validate: (x) => {
                if (!(Number(x) > 0)) return 'Must be more than 0';
                if (Number(x) > limit) return `At most ${formatCurrency(limit)}`;
                return true;
              },
            })} />
          <TextField label="Transaction reference" required={method !== 'CASH'}
            hint="UPI transaction ID or card terminal slip number. Never enter a card number."
            error={errors.transactionReference?.message}
            {...register('transactionReference', {
              maxLength: { value: 100, message: 'Max 100 characters' },
              validate: (x) => {
                if (looksLikeCardNumber(x)) return 'Do not enter card numbers';
                if (method !== 'CASH' && x.trim().length < 3) return 'Required for non-cash payments';
                return true;
              },
            })} />
          <TextAreaField label={refund ? 'Reason for refund' : 'Notes'} required={refund} rows={2} error={errors.notes?.message}
            {...register('notes', {
              maxLength: { value: 500, message: 'Max 500 characters' },
              validate: (x) => !refund || x.trim().length >= 3 || 'Enter the reason (at least 3 characters)',
            })} />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>Cancel</Button>
            <Button type="submit" variant={refund ? 'danger' : 'primary'} loading={isSubmitting}>{refund ? 'Record refund' : 'Record payment'}</Button>
          </div>
        </form>
  );
}
