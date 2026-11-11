import { useState } from 'react';
import LedgerTable from '../../components/inventory/LedgerTable.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { listTransactions, TXN_LABELS } from '../../services/inventoryService.js';

/** Every stock movement, newest first (the ledger is append-only). */
export default function StockLedgerPage() {
  const [type, setType] = useState('');
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(() => listTransactions({ type, page }), [type, page]);
  const rows = data?.content || [];
  return (
    <>
      <PageHeader title="Stock history" description="All stock movements. Entries are never edited or deleted." />
      <Card>
        <div className="max-w-xs">
          <SelectField label="Type" placeholder="All movements" value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}
            options={Object.entries(TXN_LABELS).map(([value, label]) => ({ value, label }))} />
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState title="No stock movements" />}
          {rows.length > 0 && <LedgerTable rows={rows} showMedicine />}
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
