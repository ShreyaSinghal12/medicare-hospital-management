import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pill, Search } from 'lucide-react';
import PrescriptionStatusBadge from '../../components/prescriptions/PrescriptionStatusBadge.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { SelectField, TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { getIssuedPrescriptions, getMyPrescriptions } from '../../services/prescriptionService.js';
import { formatDateLong } from '../../utils/dates.js';

/**
 * Prescription list for patients (own, search by medicine/doctor/number, doctor + date filters)
 * and doctors (issued by me, status filter).
 */
export default function PrescriptionListPage({ mode }) {
  const patient = mode === 'patient';
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [doctorId, setDoctorId] = useState('');
  const [status, setStatus] = useState('');
  const [showOld, setShowOld] = useState(false);
  const [page, setPage] = useState(0);
  const query = patient
    ? () => getMyPrescriptions({ search, from, to, doctorId, includeSuperseded: showOld, page })
    : () => getIssuedPrescriptions({ search, from, to, status, page });
  const { data, loading, error, reload } = useApi(query, [search, from, to, doctorId, status, showOld, page]);
  const rows = data?.content || [];
  // Remember every doctor seen so far, so choosing one doesn't remove the others from the filter.
  const seenDoctors = useRef(new Map());
  rows.forEach((r) => seenDoctors.current.set(r.doctorId, r.doctorName));
  const doctors = [...seenDoctors.current.entries()];
  const base = patient ? '/patient/prescriptions' : '/doctor/prescriptions';
  const resetPage = (fn) => (e) => { fn(e.target.type === 'checkbox' ? e.target.checked : e.target.value); setPage(0); };

  return (
    <>
      <PageHeader title={patient ? 'My prescriptions' : 'Prescriptions I issued'}
        description={patient ? 'Prescriptions finalized by your doctors. Download any of them as PDF.' : 'Drafts and finalized prescriptions you wrote.'} />
      <Card>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_10rem_10rem_12rem]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-400" aria-hidden="true" />
            <TextField label="Search" placeholder={patient ? 'Medicine, doctor or RX number' : 'Medicine, patient or RX number'}
              value={input} className="pl-9" onChange={resetPage(setInput)} />
          </div>
          <TextField label="From" type="date" value={from} onChange={resetPage(setFrom)} />
          <TextField label="To" type="date" value={to} min={from || undefined} onChange={resetPage(setTo)} />
          {patient ? (
            <SelectField label="Doctor" placeholder="All doctors" value={doctorId} onChange={resetPage(setDoctorId)}
              options={doctors.map(([id, name]) => ({ value: String(id), label: name }))} />
          ) : (
            <SelectField label="Status" placeholder="All" value={status} onChange={resetPage(setStatus)}
              options={['DRAFT', 'FINALIZED', 'SUPERSEDED'].map((s) => ({ value: s, label: s.toLowerCase() }))} />
          )}
        </div>
        {patient && (
          <label className="mt-3 flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={showOld} onChange={resetPage(setShowOld)} className="h-4 w-4 rounded border-slate-300 text-brand-600" />
            Include superseded versions
          </label>
        )}
        <div className="mt-4">
          {loading && !data && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {!loading && !error && rows.length === 0 && <EmptyState icon={Pill} title="No prescriptions found" />}
          <ul className="divide-y divide-slate-100">
            {rows.map((r) => (
              <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                <div className="min-w-0">
                  <Link to={`${base}/${r.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">{r.prescriptionNo}</Link>
                  <p className="text-sm text-slate-500">
                    {formatDateLong(r.consultationDate)} · {patient ? `${r.doctorName} (${r.specialization})` : `${r.patientName} · ${r.patientCode}`}
                  </p>
                  <p className="truncate text-sm text-slate-600">{r.medicines.join(', ')}</p>
                </div>
                <PrescriptionStatusBadge status={r.status} versionNumber={r.versionNumber} />
              </li>
            ))}
          </ul>
          <div className="mt-4"><Pagination data={data} onPageChange={setPage} /></div>
        </div>
      </Card>
    </>
  );
}
