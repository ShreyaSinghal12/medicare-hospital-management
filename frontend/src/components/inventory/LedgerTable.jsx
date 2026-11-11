import { TXN_LABELS } from '../../services/inventoryService.js';
import { formatDateTime } from '../../utils/format.js';

/** Stock ledger rows (newest first). */
export default function LedgerTable({ rows, showMedicine = false }) {
  return (
    <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Scrollable table">
      <table className="w-full min-w-[40rem] text-left text-sm">
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-2 pr-3 font-semibold">When</th>
            {showMedicine && <th scope="col" className="py-2 pr-3 font-semibold">Medicine</th>}
            <th scope="col" className="py-2 pr-3 font-semibold">Batch</th>
            <th scope="col" className="py-2 pr-3 font-semibold">Type</th>
            <th scope="col" className="py-2 pr-3 text-right font-semibold">Qty</th>
            <th scope="col" className="py-2 pr-3 text-right font-semibold">Balance</th>
            <th scope="col" className="py-2 font-semibold">Details</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((t) => {
            const plus = t.type === 'STOCK_IN' || t.type === 'ADJUSTMENT_IN';
            return (
              <tr key={t.id}>
                <td className="py-2 pr-3 text-slate-600">{formatDateTime(t.createdAt)}</td>
                {showMedicine && <td className="py-2 pr-3 font-medium text-slate-900">{t.medicineName} {t.strength}</td>}
                <td className="py-2 pr-3 font-mono text-xs text-slate-700">{t.batchNumber}</td>
                <td className="py-2 pr-3">{TXN_LABELS[t.type] || t.type}</td>
                <td className={`py-2 pr-3 text-right font-semibold ${plus ? 'text-emerald-700' : 'text-red-700'}`}>{plus ? '+' : '−'}{t.quantity}</td>
                <td className="py-2 pr-3 text-right">{t.balanceAfter}</td>
                <td className="py-2 text-slate-600">{[t.reference, t.notes, t.performedBy].filter(Boolean).join(' · ')}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
