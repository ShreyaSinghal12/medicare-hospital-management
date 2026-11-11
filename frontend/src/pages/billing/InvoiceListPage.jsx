import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Receipt, Search } from 'lucide-react';
import InvoiceStatusBadge from '../../components/billing/InvoiceStatusBadge.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { getMyInvoices, searchInvoices } from '../../services/billingService.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';

const STATUSES = [['UNPAID', 'Unpaid'], ['PARTIALLY_PAID', 'Partly paid'], ['PAID', 'Paid'], ['CANCELLED', 'Cancelled'], ['REFUNDED', 'Refunded']];

/** Invoices: reception and admin (search all), patient (own). */
export default function InvoiceListPage({ mode }) {
  const patient = mode === 'patient';
  const base = { reception: '/reception/billing', admin: '/admin/invoices', patient: '/patient/invoices' }[mode];
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useApi(
    () => (patient ? getMyInvoices({ status, page }) : searchInvoices({ search, status, from, to, page })),
    [search, status, from, to, page],
  );
  const rows = data?.content || [];
  const reset = (fn) => (e) => { fn(e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={patient ? 'My invoices' : 'Invoices'}
        description={patient ? 'Bills for your visits, tests and medicines. Download any of them as PDF.' : 'Search by invoice number, patient name or patient ID.'}
        actions={mode === 'reception' && <Button to="/reception/billing/new">New invoice</Button>} />
      <Card>
        <div className={`grid gap-3 ${patient ? 'max-w-xs' : 'sm:grid-cols-2 lg:grid-cols-[1fr_12rem_10rem_10rem]'}`}>
          {!patient && (
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-500" aria-hidden="true" />
              <TextField label="Search" placeholder="INV number, patient name or ID" value={input} className="pl-9" onChange={reset(setInput)} />
            </div>
          )}
          <SelectField label="Status" placeholder="All" value={status} onChange={reset(setStatus)} options={STATUSES.map(([value, label]) => ({ value, label }))} />
          {!patient && <TextField label="From" type="date" value={from} onChange={reset(setFrom)} />}
          {!patient && <TextField label="To" type="date" value={to} min={from || undefined} onChange={reset(setTo)} />}
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={Receipt} title="No invoices" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((i) => (
              <li key={i.id}>
                <Link to={`${base}/${i.id}`} className="flex flex-wrap items-center justify-between gap-3 rounded-lg px-2 py-3 hover:bg-slate-50">
                  <div>
                    <p className="font-medium text-slate-900">{i.invoiceNo}{patient ? '' : ` · ${i.patientName}`}</p>
                    <p className="text-sm text-slate-500">{patient ? '' : `${i.patientCode} · `}{formatDateTime(i.issuedAt)}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="text-right text-sm">
                      <p className="font-semibold text-slate-900">{formatCurrency(i.totalAmount)}</p>
                      {Number(i.balanceDue) > 0 && i.status !== 'CANCELLED' && <p className="text-amber-800">{formatCurrency(i.balanceDue)} due</p>}
                    </div>
                    <InvoiceStatusBadge status={i.status} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
