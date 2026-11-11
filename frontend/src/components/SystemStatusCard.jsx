import { CheckCircle2, Database, RefreshCw, Server, XCircle } from 'lucide-react';
import useApi from '../hooks/useApi.js';
import { getHealth } from '../services/systemService.js';
import { formatDateTime } from '../utils/format.js';
import Button from './ui/Button.jsx';
import Card from './ui/Card.jsx';
import ErrorState from './ui/ErrorState.jsx';
import Spinner from './ui/Spinner.jsx';
import StatusBadge from './ui/StatusBadge.jsx';

function Row({ label, value }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-800">{value ?? '—'}</dd>
    </div>
  );
}

/**
 * Live connectivity check: React → Spring Boot (/api/health) → MySQL.
 * Proves all three layers of the stack are wired together.
 */
export default function SystemStatusCard() {
  const { data, loading, error, reload } = useApi(getHealth);

  return (
    <Card
      title="System status"
      subtitle="Live check of React → Spring Boot → MySQL"
      actions={
        <Button variant="secondary" size="sm" onClick={reload} loading={loading} aria-label="Refresh system status">
          {!loading && <RefreshCw className="h-4 w-4" aria-hidden="true" />} Refresh
        </Button>
      }
    >
      {loading && !data && <Spinner label="Contacting backend…" />}

      {error && (
        <ErrorState title="Backend unreachable" error={error} onRetry={reload} />
      )}

      {data && !error && (
        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
              <Server className="h-4 w-4 text-brand-700" aria-hidden="true" /> Java backend
              <StatusBadge tone="green">UP</StatusBadge>
            </h3>
            <dl className="divide-y divide-slate-100">
              <Row label="Application" value={data.application} />
              <Row label="Version" value={data.version} />
              <Row label="Java" value={data.javaVersion} />
              <Row label="Server time" value={formatDateTime(data.serverTime)} />
            </dl>
          </div>
          <div>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-700">
              <Database className="h-4 w-4 text-brand-700" aria-hidden="true" /> MySQL database
              <StatusBadge tone={data.database?.status === 'UP' ? 'green' : 'red'}>
                {data.database?.status ?? 'UNKNOWN'}
              </StatusBadge>
            </h3>
            {data.database?.status === 'UP' ? (
              <dl className="divide-y divide-slate-100">
                <Row label="Server" value={`${data.database.product} ${data.database.productVersion}`} />
                <Row label="Schema version" value={`V${data.database.schemaVersion}`} />
                <Row label="Tables" value={data.database.tableCount} />
              </dl>
            ) : (
              <p className="flex items-center gap-2 text-sm text-red-700">
                <XCircle className="h-4 w-4" aria-hidden="true" />
                {data.database?.error || 'Database unavailable'}
              </p>
            )}
          </div>
          {data.status === 'UP' && (
            <p className="flex items-center gap-2 text-sm text-emerald-700 sm:col-span-2">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> All systems operational.
            </p>
          )}
        </div>
      )}
    </Card>
  );
}
