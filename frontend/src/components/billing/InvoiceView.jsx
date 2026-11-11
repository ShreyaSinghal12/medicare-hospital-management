import { Download } from 'lucide-react';
import { invoicePdfUrl, ITEM_TYPES, PAYMENT_METHODS } from '../../services/billingService.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';
import Card from '../ui/Card.jsx';
import InvoiceStatusBadge from './InvoiceStatusBadge.jsx';

/** Invoice lines, totals and payment history (read-only). */
export default function InvoiceView({ invoice: inv, actions }) {
  const rows = [
    ['Subtotal', formatCurrency(inv.subtotal)],
    ...(Number(inv.discountAmount) > 0 ? [['Discount', `− ${formatCurrency(inv.discountAmount)}`]] : []),
    [`Tax (${Number(inv.taxRatePercent)}%)`, formatCurrency(inv.taxAmount)],
  ];
  return (
    <div className="space-y-6">
      <Card title={inv.invoiceNo} subtitle={`${inv.patientName} · ${inv.patientCode} · issued ${formatDateTime(inv.issuedAt)} by ${inv.issuedBy}`}
        actions={(
          <div className="flex flex-wrap items-center gap-2">
            <InvoiceStatusBadge status={inv.status} />
            <a href={invoicePdfUrl(inv.id)} download
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
              <Download className="h-4 w-4" aria-hidden="true" /> PDF
            </a>
          </div>
        )}>
        <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Scrollable table">
          <table className="w-full min-w-[32rem] text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-3 font-semibold">Item</th>
                <th scope="col" className="py-2 pr-3 text-right font-semibold">Qty</th>
                <th scope="col" className="py-2 pr-3 text-right font-semibold">Unit price</th>
                <th scope="col" className="py-2 text-right font-semibold">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {inv.items.map((i) => (
                <tr key={i.id}>
                  <td className="py-2 pr-3">
                    <p className="text-slate-900">{i.description}</p>
                    <p className="text-xs text-slate-500">{ITEM_TYPES[i.type] || i.type}</p>
                  </td>
                  <td className="py-2 pr-3 text-right">{i.quantity}</td>
                  <td className="py-2 pr-3 text-right">{formatCurrency(i.unitPrice)}</td>
                  <td className="py-2 text-right font-medium">{formatCurrency(i.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="ml-auto mt-4 max-w-xs space-y-1 text-sm">
          {rows.map(([k, v]) => <div key={k} className="flex justify-between"><dt className="text-slate-500">{k}</dt><dd>{v}</dd></div>)}
          <div className="flex justify-between border-t border-slate-200 pt-1 font-semibold"><dt>Total</dt><dd>{formatCurrency(inv.totalAmount)}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Paid</dt><dd>{formatCurrency(inv.amountPaid)}</dd></div>
          {Number(inv.refundedAmount) > 0 && (
            <div className="flex justify-between"><dt className="text-slate-500">Refunded</dt><dd>− {formatCurrency(inv.refundedAmount)}</dd></div>
          )}
          <div className="flex justify-between text-base font-semibold text-slate-900"><dt>Balance due</dt><dd>{formatCurrency(inv.balanceDue)}</dd></div>
        </dl>
        {inv.notes && <p className="mt-3 text-sm text-slate-600">Notes: {inv.notes}</p>}
        {inv.status === 'CANCELLED' && <p className="mt-2 text-sm text-slate-600">Cancelled {formatDateTime(inv.cancelledAt)}: {inv.cancellationReason}</p>}
        {actions && <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">{actions}</div>}
      </Card>
      <Card title="Payments">
        {inv.payments.length === 0 ? <p className="text-sm text-slate-500">No payments recorded.</p> : (
          <ul className="divide-y divide-slate-100 text-sm">
            {inv.payments.map((p) => (
              <li key={p.id} className="flex flex-wrap items-start justify-between gap-2 py-2">
                <div>
                  <p className="font-medium text-slate-900">{p.type === 'REFUND' ? 'Refund' : 'Payment'} {p.paymentNo}</p>
                  <p className="text-slate-500">
                    {PAYMENT_METHODS[p.method] || p.method}{p.transactionReference ? ` · ref ${p.transactionReference}` : ''} · {formatDateTime(p.paidAt)} · {p.recordedBy}
                  </p>
                  {p.notes && <p className="text-slate-600">{p.notes}</p>}
                </div>
                <span className={`font-semibold ${p.type === 'REFUND' ? 'text-red-700' : 'text-emerald-700'}`}>
                  {p.type === 'REFUND' ? '− ' : ''}{formatCurrency(p.amount)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
