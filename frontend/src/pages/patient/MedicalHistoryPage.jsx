import { Link } from 'react-router-dom';
import { CalendarPlus, ClipboardList } from 'lucide-react';
import AllergiesConditionsPanel from '../../components/clinical/AllergiesConditionsPanel.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import useApi from '../../hooks/useApi.js';
import { getMyMedicalHistory } from '../../services/clinicalService.js';
import { formatDateLong, hhmm, todayIsoLocal } from '../../utils/dates.js';

/** Patient's chronological medical history (finalized doctor-entered records only, read-only). */
export default function MedicalHistoryPage() {
  const { data, loading, error, reload } = useApi(getMyMedicalHistory);
  if (loading && !data) return <Spinner label="Loading your history…" />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  const today = todayIsoLocal();

  return (
    <>
      <PageHeader title="Medical history" description="Records entered by your doctors. Contact your doctor if something looks wrong." />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card title="Timeline">
            {data.timeline.length === 0 && (
              <EmptyState icon={ClipboardList} title="No consultations yet" message="Notes appear here after your doctor finalizes a consultation." />
            )}
            <ol className="relative space-y-6 border-l-2 border-brand-100 pl-6">
              {data.timeline.map((e) => (
                <li key={e.consultationId} className="relative">
                  <span className="absolute -left-[1.95rem] top-1 h-3.5 w-3.5 rounded-full border-2 border-white bg-brand-500" aria-hidden="true" />
                  <p className="text-sm font-semibold text-slate-900">{formatDateLong(e.date)} · {hhmm(e.time)}</p>
                  <p className="text-sm text-slate-500">{e.doctorName} · {e.specialization} · {e.departmentName}
                    {e.amended && <> · <StatusBadge tone="slate">amended</StatusBadge></>}</p>
                  <dl className="mt-2 space-y-1.5 rounded-lg bg-slate-50 p-3 text-sm">
                    {[['Symptoms', e.symptoms], ['Diagnosis', e.diagnosis], ['Treatment plan', e.treatmentPlan]].filter(([, v]) => v).map(([k, v]) => (
                      <div key={k}><dt className="font-medium text-slate-700">{k}</dt><dd className="whitespace-pre-line text-slate-600">{v}</dd></div>
                    ))}
                  </dl>
                  {e.prescriptions?.length > 0 && (
                    <ul className="mt-2 space-y-1 text-sm">
                      {e.prescriptions.map((p) => (
                        <li key={p.id}>
                          <Link to={`/patient/prescriptions/${p.id}`} className="font-medium text-brand-700 hover:underline">
                            Prescription {p.prescriptionNo}{p.versionNumber > 1 ? ` (v${p.versionNumber})` : ''}
                          </Link>
                          <span className="text-slate-500"> · {p.medicines.join(', ')}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                  {e.followUpDate && (
                    <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
                      <span className="text-slate-700">Follow-up recommended on <strong>{formatDateLong(e.followUpDate)}</strong>{e.followUpNotes ? ` — ${e.followUpNotes}` : ''}</span>
                      {e.followUpDate >= today && (
                        <Button size="sm" variant="secondary" to={`/patient/book/${e.doctorId}?date=${e.followUpDate}&followUpOf=${e.appointmentId}`}>
                          <CalendarPlus className="h-4 w-4" aria-hidden="true" /> Book follow-up
                        </Button>
                      )}
                    </div>
                  )}
                </li>
              ))}
            </ol>
          </Card>
        </div>
        <AllergiesConditionsPanel allergies={data.allergies} conditions={data.conditions} editable={false} />
      </div>
    </>
  );
}
