import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import InvoiceView from '../../components/billing/InvoiceView.jsx';
import MoneyDialog from '../../components/billing/MoneyDialog.jsx';
import Button from '../../components/ui/Button.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextAreaField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import { cancelInvoice, getInvoice } from '../../services/billingService.js';

/** Invoice detail. Receptionists record payments, refunds and cancellations; others only read. */
export default function InvoicePage({ mode }) {
  const { id } = useParams();
  const back = { reception: ['/reception/billing', 'Invoices'], admin: ['/admin/invoices', 'Invoices'], patient: ['/patient/invoices', 'My invoices'] }[mode];
  const [inv, setInv] = useState(null);
  const [error, setError] = useState(null);
  const [dialog, setDialog] = useState(null); // 'pay' | 'refund' | 'cancel'
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setError(null);
    try {
      setInv(await getInvoice(id));
    } catch (err) {
      setError(err);
    }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { load(); }, [id]);

  async function doCancel() {
    if (reason.trim().length < 3) {
      setReasonError('Enter a reason (at least 3 characters)');
      return;
    }
    setBusy(true);
    try {
      setInv(await cancelInvoice(inv.id, reason.trim()));
      toast.success('Invoice cancelled; its charges can be billed again');
      setDialog(null);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (error) return <ErrorState error={error} onRetry={load} />;
  if (!inv) return <Spinner />;
  const actions = (inv.canPay || inv.canRefund || inv.canCancel) && (
    <>
      {inv.canPay && <Button onClick={() => setDialog('pay')}>Record payment</Button>}
      {inv.canRefund && <Button variant="secondary" onClick={() => setDialog('refund')}>Refund</Button>}
      {inv.canCancel && <Button variant="ghost" onClick={() => { setReason(''); setReasonError(null); setDialog('cancel'); }}>Cancel invoice</Button>}
    </>
  );

  return (
    <>
      <Link to={back[0]} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {back[1]}
      </Link>
      <InvoiceView invoice={inv} actions={actions} />
      <MoneyDialog invoice={dialog === 'pay' || dialog === 'refund' ? inv : null} mode={dialog}
        onClose={() => setDialog(null)} onDone={(updated) => { setInv(updated); setDialog(null); }} />
      <Modal open={dialog === 'cancel'} title={`Cancel ${inv.invoiceNo}?`} description="Only unpaid invoices can be cancelled. The charges become billable again."
        onClose={() => setDialog(null)} busy={busy}>
        <TextAreaField label="Reason" required rows={3} maxLength={500} value={reason} error={reasonError}
          onChange={(e) => { setReason(e.target.value); setReasonError(null); }} />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setDialog(null)} disabled={busy}>Keep invoice</Button>
          <Button variant="danger" onClick={doCancel} loading={busy}>Cancel invoice</Button>
        </div>
      </Modal>
    </>
  );
}
