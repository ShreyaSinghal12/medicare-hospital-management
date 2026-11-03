import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Award, Building2, Clock, GraduationCap } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import { getDoctor } from '../../services/doctorService.js';
import { formatCurrency } from '../../utils/format.js';
import Card from '../ui/Card.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import Spinner from '../ui/Spinner.jsx';

/** Public doctor profile. Working days and bookable slots are added in Phase 4 (scheduling). */
export default function DoctorProfile({ backTo, backLabel = 'Back to doctors' }) {
  const { id } = useParams();
  const { data: doctor, loading, error, reload } = useApi(() => getDoctor(id), [id]);

  return (
    <div>
      <Link to={backTo} className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {backLabel}
      </Link>
      <div className="mt-4">
        {loading && <Spinner label="Loading profile…" />}
        {error && (
          <ErrorState title={error.status === 404 ? 'Doctor not found' : 'Could not load profile'} error={error}
            onRetry={error.status === 404 ? undefined : reload} />
        )}
        {doctor && (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <Card>
                <h1 className="text-2xl font-bold text-slate-900">{doctor.fullName}</h1>
                <p className="mt-1 font-medium text-brand-700">{doctor.specialization}</p>
                <dl className="mt-6 grid gap-4 sm:grid-cols-2">
                  {[
                    { icon: Building2, label: 'Department', value: doctor.departmentName },
                    { icon: GraduationCap, label: 'Qualifications', value: doctor.qualifications },
                    { icon: Award, label: 'Experience', value: `${doctor.experienceYears} years` },
                    { icon: Clock, label: 'Consultation length', value: `${doctor.slotDurationMinutes} minutes` },
                  ].map(({ icon: Icon, label, value }) => (
                    <div key={label} className="flex gap-3">
                      <Icon className="mt-0.5 h-5 w-5 text-brand-600" aria-hidden="true" />
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
                        <dd className="text-sm text-slate-900">{value}</dd>
                      </div>
                    </div>
                  ))}
                </dl>
                {doctor.bio && (
                  <div className="mt-6 border-t border-slate-100 pt-5">
                    <h2 className="text-sm font-semibold text-slate-900">About</h2>
                    <p className="mt-1 whitespace-pre-line text-sm text-slate-600">{doctor.bio}</p>
                  </div>
                )}
              </Card>
            </div>
            <Card title="Consultation fee">
              <p className="text-3xl font-bold text-slate-900">{formatCurrency(doctor.consultationFee)}</p>
              <p className="mt-2 text-sm text-slate-500">
                Payable at the hospital. Taxes, if any, are shown on the invoice.
              </p>
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
