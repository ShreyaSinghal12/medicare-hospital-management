import { useState } from 'react';
import { Banknote, FileText, Hourglass, RotateCcw } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import useApi from '../../hooks/useApi.js';
import { getBillingSummary, ITEM_TYPES, PAYMENT_METHODS } from '../../services/billingService.js';
import { formatDateLong } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

const STATUS_LABELS = { UNPAID: 'Unpaid', PARTIALLY_PAID: 'Partly paid', PAID: 'Paid', CANCELLED: 'Cancelled', REFUNDED: 'Refunded' };

function Breakdown({ title, entries, labels, format = formatCurrency }) {
  const rows = Object.entries(entries || {});
  return (
    <Card title={title}>
      {rows.length === 0 ? <p className="text-sm text-slate-500">Nothing in this period.</p> : (
        <dl className="space-y-1 text-sm">
          {rows.map(([k, v]) => (
            <div key={k} className="flex justify-between"><dt className="text-slate-600">{labels[k] || k}</dt><dd className="font-medium">{format(v)}</dd></div>
          ))}
        </dl>
      )}
    </Card>
  );
}

/** Billing figures for administrators (read-only). */
export default function BillingSummaryPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const { data, loading, error, reload } = useApi(() => getBillingSummary({ from, to }), [from, to]);
  return (
    <>
      <PageHeader title="Billing" description="Invoiced, collected and outstanding amounts. Defaults to the current month."
        actions={<Button to="/admin/invoices" variant="secondary">All invoices</Button>} />
      <Card>
        <div className="grid max-w-md gap-3 sm:grid-cols-2">
          <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField label="To" type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
        </div>
      </Card>
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (
        <>
          <p className="mt-4 text-sm text-slate-500">{formatDateLong(data.from)} – {formatDateLong(data.to)}</p>
          <div className="mt-2 grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard icon={FileText} label={`Invoiced (${data.invoiceCount} invoices)`} value={formatCurrency(data.invoicedTotal)} />
            <StatCard icon={Banknote} label="Collected" value={formatCurrency(data.collected)} />
            <StatCard icon={RotateCcw} label="Refunded" value={formatCurrency(data.refunded)} tone="amber" />
            <StatCard icon={Hourglass} label={`Outstanding (${data.outstandingCount}, all dates)`} value={formatCurrency(data.outstandingTotal)} tone="amber" />
          </div>
          <div className="mt-6 grid gap-6 lg:grid-cols-3">
            <Breakdown title="Collected by method" entries={data.collectedByMethod} labels={PAYMENT_METHODS} />
            <Breakdown title="Invoiced by service" entries={data.invoicedByType} labels={ITEM_TYPES} />
            <Breakdown title="Invoices by status" entries={data.invoicesByStatus} labels={STATUS_LABELS} format={(v) => v} />
          </div>
        </>
      )}
    </>
  );
}
