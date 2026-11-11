import { Link } from 'react-router-dom';
import { AlertTriangle, CalendarClock, PackageX } from 'lucide-react';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import useApi from '../../hooks/useApi.js';
import { getInventoryAlerts } from '../../services/inventoryService.js';
import { formatDateShort } from '../../utils/dates.js';

function BatchList({ rows, empty, expired }) {
  if (rows.length === 0) return <p className="text-sm text-slate-500">{empty}</p>;
  return (
    <ul className="divide-y divide-slate-100">
      {rows.map((b) => (
        <li key={b.batchId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
          <Link to={`/admin/inventory/medicines/${b.medicineId}`} className="font-medium text-brand-700 hover:underline">
            {b.medicineName} {b.strength}
          </Link>
          <span className="text-slate-600">
            Batch {b.batchNumber} · {b.quantityAvailable} {b.unit}(s) · {expired
              ? `expired ${formatDateShort(b.expiryDate)}`
              : `expires ${formatDateShort(b.expiryDate)} (${b.daysLeft === 0 ? 'today' : `in ${b.daysLeft} day(s)`})`}
          </span>
        </li>
      ))}
    </ul>
  );
}

/** Low stock, expiring and expired stock. Administrators also get a daily notification. */
export default function InventoryAlertsPage() {
  const { data, loading, error, reload } = useApi(getInventoryAlerts);
  return (
    <>
      <PageHeader title="Stock alerts" description="Checked live. A summary is also sent to administrators every morning." />
      {loading && !data && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-3">
            <StatCard icon={AlertTriangle} label="Low or out of stock" value={data.lowStock.length} />
            <StatCard icon={CalendarClock} label={`Expiring within ${data.expiryWarningDays} days`} value={data.expiringSoon.length} />
            <StatCard icon={PackageX} label="Expired batches in stock" value={data.expired.length} />
          </div>
          <div className="space-y-6">
            <Card title="Low or out of stock" subtitle="Usable stock at or below the reorder level.">
              {data.lowStock.length === 0 ? <p className="text-sm text-slate-500">All medicines are above their reorder level.</p> : (
                <ul className="divide-y divide-slate-100">
                  {data.lowStock.map((m) => (
                    <li key={m.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                      <Link to={`/admin/inventory/medicines/${m.id}`} className="font-medium text-brand-700 hover:underline">{m.name} {m.strength}</Link>
                      <span className={m.availableQuantity === 0 ? 'font-semibold text-red-700' : 'text-amber-800'}>
                        {m.availableQuantity} {m.unit}(s) left · reorder at {m.reorderLevel}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Expiring soon" subtitle="Dispense these first or plan a return.">
              <BatchList rows={data.expiringSoon} empty="No batches expire within the warning window." />
            </Card>
            <Card title="Expired, still in stock" subtitle="Cannot be dispensed. Write them off from the medicine page.">
              <BatchList rows={data.expired} expired empty="No expired stock." />
            </Card>
          </div>
        </>
      )}
    </>
  );
}
