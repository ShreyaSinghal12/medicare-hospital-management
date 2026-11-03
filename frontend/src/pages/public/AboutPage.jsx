import { Mail, MapPin, Phone } from 'lucide-react';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getHospitalInfo } from '../../services/systemService.js';

export default function AboutPage() {
  const { data, loading, error, reload } = useApi(getHospitalInfo);

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <h1 className="text-3xl font-bold text-slate-900">About {data?.hospitalName || 'the hospital'}</h1>
      <p className="mt-3 text-slate-600">
        A single-location community hospital using MediCare to manage patient care digitally — from booking an
        appointment to collecting a prescription — while keeping medical records private and access-controlled.
      </p>

      <div className="mt-8">
        <Card title="Contact information">
          {loading && <Spinner />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && (
            <ul className="space-y-3 text-sm">
              <li className="flex items-start gap-3"><MapPin className="mt-0.5 h-4 w-4 text-brand-600" aria-hidden="true" /> {data.address || '—'}</li>
              <li className="flex items-start gap-3"><Phone className="mt-0.5 h-4 w-4 text-brand-600" aria-hidden="true" /> {data.phone || '—'}</li>
              <li className="flex items-start gap-3"><Mail className="mt-0.5 h-4 w-4 text-brand-600" aria-hidden="true" /> {data.email || '—'}</li>
            </ul>
          )}
        </Card>
      </div>

      {data?.cancellationPolicy && (
        <div className="mt-6">
          <Card title="Appointment policy">
            <p className="text-sm text-slate-600">{data.cancellationPolicy}</p>
          </Card>
        </div>
      )}
    </div>
  );
}
