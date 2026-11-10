import { Link } from 'react-router-dom';
import { Pill } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import { getConsultationPrescriptions } from '../../services/prescriptionService.js';
import Button from '../ui/Button.jsx';
import Card from '../ui/Card.jsx';
import Spinner from '../ui/Spinner.jsx';
import PrescriptionStatusBadge from './PrescriptionStatusBadge.jsx';

/** Prescription block on the consultation page: create the first one or open existing versions. */
export default function ConsultationPrescriptionCard({ consultationId, canWrite }) {
  const { data, loading } = useApi(() => getConsultationPrescriptions(consultationId), [consultationId]);
  const active = (data || []).filter((p) => p.status !== 'SUPERSEDED');

  return (
    <Card title="Prescription" actions={<Pill className="h-5 w-5 text-brand-600" aria-hidden="true" />}>
      {loading && <Spinner />}
      {data && active.length === 0 && (
        canWrite
          ? <Button to={`/doctor/consultations/${consultationId}/prescriptions/new`}>Write prescription</Button>
          : <p className="text-sm text-slate-500">No prescription issued.</p>
      )}
      <ul className="space-y-2">
        {active.map((p) => (
          <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <Link to={`/doctor/prescriptions/${p.id}`} className="font-medium text-brand-700 hover:underline">
              {p.prescriptionNo} · {p.items.length} medicine(s)
            </Link>
            <PrescriptionStatusBadge status={p.status} versionNumber={p.versionNumber} />
          </li>
        ))}
      </ul>
    </Card>
  );
}
