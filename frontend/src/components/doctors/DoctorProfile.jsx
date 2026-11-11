import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Award, Building2, Clock, GraduationCap } from 'lucide-react';
import { useAuth } from '../../context/AuthContext.jsx';
import useApi from '../../hooks/useApi.js';
import { getDoctorSchedule } from '../../services/appointmentService.js';
import SlotPicker from '../scheduling/SlotPicker.jsx';
import WeeklyScheduleView from '../scheduling/WeeklyScheduleView.jsx';
import Button from '../ui/Button.jsx';
import { getDoctor } from '../../services/doctorService.js';
import { formatCurrency } from '../../utils/format.js';
import Card from '../ui/Card.jsx';
import ErrorState from '../ui/ErrorState.jsx';
import Spinner from '../ui/Spinner.jsx';
import PageHeading from '../ui/PageHeading.jsx';

/** Doctor profile with weekly hours and a live slot picker that leads into booking. */
export default function DoctorProfile({ backTo, backLabel = 'Back to doctors' }) {
  const { id } = useParams();
  const { data: doctor, loading, error, reload } = useApi(() => getDoctor(id), [id]);
  const { data: schedule } = useApi(() => getDoctorSchedule(id), [id]);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [selected, setSelected] = useState(null);

  function continueToBook() {
    const target = `/patient/book/${id}?date=${selected.date}&time=${selected.startTime}`;
    navigate(user ? target : `/login?redirect=${encodeURIComponent(target)}`);
  }

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
                <PageHeading title={doctor.fullName} className="text-2xl font-bold text-slate-900">{doctor.fullName}</PageHeading>
                <p className="mt-1 font-medium text-brand-700">{doctor.specialization}</p>
                <dl className="mt-6 grid gap-4 sm:grid-cols-2">
                  {[
                    { icon: Building2, label: 'Department', value: doctor.departmentName },
                    { icon: GraduationCap, label: 'Qualifications', value: doctor.qualifications },
                    { icon: Award, label: 'Experience', value: `${doctor.experienceYears} years` },
                    { icon: Clock, label: 'Consultation length', value: `${doctor.slotDurationMinutes} minutes` },
                  ].map(({ icon: Icon, label, value }) => (
                    <div key={label}>
                      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-slate-500">
                        <Icon className="h-5 w-5 text-brand-700" aria-hidden="true" /> {label}
                      </dt>
                      <dd className="pl-7 text-sm text-slate-900">{value}</dd>
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
            <div className="space-y-6">
              <Card title="Consultation fee">
                <p className="text-3xl font-bold text-slate-900">{formatCurrency(doctor.consultationFee)}</p>
                <p className="mt-2 text-sm text-slate-500">
                  Payable at the hospital. Taxes, if any, are shown on the invoice.
                </p>
              </Card>
              <Card title="Working hours"><WeeklyScheduleView schedule={schedule} /></Card>
            </div>
            <div className="lg:col-span-3">
              <Card title="Book an appointment" subtitle="Pick a date and a free time slot.">
                <SlotPicker doctorId={id} value={selected} onChange={setSelected} />
                <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4">
                  {user && user.role !== 'PATIENT' ? (
                    <p className="text-sm text-slate-500">
                      Online booking is for patients. Reception books appointments for walk-in patients from the reception desk.
                    </p>
                  ) : (
                    <>
                      <Button onClick={continueToBook} disabled={!selected}>
                        {user ? 'Continue to book' : 'Log in to book'}
                      </Button>
                      {selected ? (
                        <span className="text-sm text-slate-600">Selected: {selected.date} at {selected.startTime}</span>
                      ) : (
                        <span className="text-sm text-slate-500">Select a time slot to continue.</span>
                      )}
                    </>
                  )}
                </div>
              </Card>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
