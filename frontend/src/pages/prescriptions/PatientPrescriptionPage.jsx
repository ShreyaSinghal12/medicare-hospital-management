import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import PrescriptionStatusBadge from '../../components/prescriptions/PrescriptionStatusBadge.jsx';
import PrescriptionView from '../../components/prescriptions/PrescriptionView.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getPrescription, getPrescriptionVersions } from '../../services/prescriptionService.js';

/** Patient prescription detail with versions and PDF download. */
export default function PatientPrescriptionPage() {
  const { id } = useParams();
  const { data: rx, loading, error, reload } = useApi(() => getPrescription(id), [id]);
  const versions = useApi(() => getPrescriptionVersions(id), [id]);
  if (loading && !rx) return <Spinner label="Loading prescription…" />;
  if (error) return <ErrorState error={error} onRetry={error.status === 404 ? undefined : reload} title={error.status === 404 ? 'Prescription not found' : undefined} />;
  return (
    <>
      <Link to="/patient/prescriptions" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> My prescriptions
      </Link>
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2"><PrescriptionView rx={rx} /></div>
        {(versions.data || []).length > 1 && (
          <Card title="Versions">
            <ul className="space-y-2 text-sm">
              {versions.data.map((v) => (
                <li key={v.id} className="flex items-center justify-between gap-2">
                  <Link to={`/patient/prescriptions/${v.id}`} className="font-medium text-brand-700 hover:underline">Version {v.versionNumber}</Link>
                  <PrescriptionStatusBadge status={v.status} />
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>
    </>
  );
}
