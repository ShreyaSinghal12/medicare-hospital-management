import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import AppointmentStatusBadge from '../../components/appointments/AppointmentStatusBadge.jsx';
import AllergiesConditionsPanel from '../../components/clinical/AllergiesConditionsPanel.jsx';
import PatientLabOrdersCard from '../../components/lab/PatientLabOrdersCard.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import { getPatientRecord } from '../../services/clinicalService.js';
import { formatDateShort, hhmm } from '../../utils/dates.js';

/** Authorized patient record for a treating doctor (403 otherwise; access is audited by the server). */
export default function PatientRecordPage() {
  const { patientId } = useParams();
  const { data, loading, error, reload } = useApi(() => getPatientRecord(patientId), [patientId]);

  if (loading && !data) return <Spinner label="Loading record…" />;
  if (error) {
    return (
      <ErrorState error={error} onRetry={error.status === 403 ? undefined : reload}
        title={error.status === 403 ? 'You are not treating this patient' : 'Could not load the record'} />
    );
  }
  const p = data.patient;
  return (
    <>
      <Link to="/doctor/patients" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> My patients
      </Link>
      <PageHeader title={p.fullName} description={`${p.patientCode} · ${p.age} years · ${p.bloodGroup || 'blood group not recorded'}`} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="Consultations">
            {data.consultations.length === 0 && <p className="text-sm text-slate-500">No consultations yet.</p>}
            <ul className="divide-y divide-slate-100">
              {data.consultations.map((c) => (
                <li key={c.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{formatDateShort(c.date)} · {c.doctorName} <span className="text-sm font-normal text-slate-500">({c.specialization})</span></p>
                    <p className="text-sm text-slate-600">{c.diagnosis || <em className="text-slate-500">No diagnosis yet</em>}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge tone={c.status === 'FINALIZED' ? 'green' : 'amber'}>{c.status.toLowerCase()}</StatusBadge>
                    {c.amendmentCount > 0 && <StatusBadge tone="slate">amended</StatusBadge>}
                    <Link to={`/doctor/consultations/${c.id}`} className="text-sm font-medium text-brand-700 hover:underline">Open</Link>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
          <PatientLabOrdersCard patientId={p.id} />
          <Card title="Contact & demographics">
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              {[['Date of birth', p.dateOfBirth], ['Gender', p.gender?.toLowerCase().replaceAll('_', ' ')], ['Phone', p.phone],
                ['Emergency contact', [p.emergencyContactName, p.emergencyContactPhone].filter(Boolean).join(' · ')]].map(([k, v]) => (
                <div key={k}><dt className="text-slate-500">{k}</dt><dd className="font-medium text-slate-900">{v || '—'}</dd></div>
              ))}
            </dl>
          </Card>
          <Card title="Visits">
            <ul className="divide-y divide-slate-100">
              {data.visits.map((v) => (
                <li key={v.appointmentId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <span>{formatDateShort(v.date)} {hhmm(v.startTime)} · {v.doctorName} · {v.departmentName}</span>
                  <AppointmentStatusBadge status={v.status} />
                </li>
              ))}
            </ul>
          </Card>
        </div>
        <AllergiesConditionsPanel patientId={p.id} allergies={data.allergies} conditions={data.conditions} editable onChanged={reload} />
      </div>
    </>
  );
}
