import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Pill, Plus, Search } from 'lucide-react';
import MedicineForm from '../../components/inventory/MedicineForm.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import Modal from '../../components/ui/Modal.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { listMedicines, MEDICINE_CATEGORIES, titleCase } from '../../services/inventoryService.js';
import { formatDateShort } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

/** Medicine catalog with current stock. */
export default function MedicinesPage() {
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [category, setCategory] = useState('');
  const [inactive, setInactive] = useState(false);
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const { data, loading, error, reload } = useApi(() => listMedicines({ search, category, includeInactive: inactive, page }),
    [search, category, inactive, page]);
  const rows = data?.content || [];

  return (
    <>
      <PageHeader title="Medicines" description="Catalog and stock. Usable stock excludes expired batches."
        actions={<Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" aria-hidden="true" /> Add medicine</Button>} />
      <Card>
        <div className="grid gap-3 sm:grid-cols-[1fr_12rem_auto] sm:items-end">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
            <TextField label="Search" placeholder="Name, generic name or manufacturer" value={input} className="pl-9"
              onChange={(e) => { setInput(e.target.value); setPage(0); }} />
          </div>
          <SelectField label="Form" placeholder="All forms" value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}
            options={MEDICINE_CATEGORIES.map((c) => ({ value: c, label: titleCase(c) }))} />
          <label className="flex items-center gap-2 pb-2 text-sm text-slate-700">
            <input type="checkbox" checked={inactive} onChange={(e) => { setInactive(e.target.checked); setPage(0); }}
              className="h-4 w-4 rounded border-slate-300 text-brand-600" /> Show inactive
          </label>
        </div>
        <div className="mt-5">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && rows.length === 0 && <EmptyState icon={Pill} title="No medicines found" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((m) => (
              <li key={m.id}>
                <Link to={`/admin/inventory/medicines/${m.id}`} className="flex flex-wrap items-center justify-between gap-3 rounded-lg px-2 py-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">
                      {m.name} {m.strength} <span className="text-sm font-normal text-slate-500">· {titleCase(m.category)} · {m.manufacturer}</span>
                    </p>
                    <p className="text-sm text-slate-500">
                      {formatCurrency(m.unitPrice)} per {m.unit} · reorder at {m.reorderLevel}
                      {m.nearestExpiry ? ` · next expiry ${formatDateShort(m.nearestExpiry)}` : ''}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-slate-900">{m.availableQuantity} {m.unit}(s)</span>
                    {!m.active && <StatusBadge tone="slate">Inactive</StatusBadge>}
                    {m.lowStock && <StatusBadge tone="amber">{m.availableQuantity === 0 ? 'Out of stock' : 'Low stock'}</StatusBadge>}
                    {m.expiredQuantity > 0 && <StatusBadge tone="red">{m.expiredQuantity} expired</StatusBadge>}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
      <Modal open={adding} title="Add medicine" size="lg" onClose={() => setAdding(false)}>
        <MedicineForm onCancel={() => setAdding(false)} onSaved={(d) => { setAdding(false); navigate(`/admin/inventory/medicines/${d.medicine.id}`); }} />
      </Modal>
    </>
  );
}
