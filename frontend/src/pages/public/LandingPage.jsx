import {
  Activity, CalendarCheck, ClipboardList, FlaskConical, Pill, Receipt, ShieldCheck, Stethoscope,
} from 'lucide-react';
import SystemStatusCard from '../../components/SystemStatusCard.jsx';
import Button from '../../components/ui/Button.jsx';
import useApi from '../../hooks/useApi.js';
import { getHospitalInfo } from '../../services/systemService.js';

const FEATURES = [
  { icon: CalendarCheck, title: 'Smart scheduling', text: 'Slots generated from doctor schedules, with conflict-free booking even under simultaneous requests.' },
  { icon: Stethoscope, title: 'Doctor consultations', text: 'Structured clinical notes with audit-tracked amendments.' },
  { icon: ClipboardList, title: 'Digital prescriptions', text: 'Draft, finalise and download versioned prescriptions as PDF.' },
  { icon: FlaskConical, title: 'Laboratory', text: 'Test orders, sample tracking and secure PDF report delivery.' },
  { icon: Pill, title: 'Pharmacy inventory', text: 'Batches, expiry tracking, low-stock alerts and dispensing records.' },
  { icon: Receipt, title: 'Billing', text: 'Itemised invoices, partial payments, refunds and PDF invoices.' },
  { icon: ShieldCheck, title: 'Privacy by design', text: 'Role-based and record-level access control with audit logs.' },
  { icon: Activity, title: 'Analytics', text: 'Real-time operational dashboards for administrators.' },
];

export default function LandingPage() {
  const { data: info } = useApi(getHospitalInfo);

  return (
    <>
      <section className="bg-gradient-to-b from-brand-50 to-slate-50">
        <div className="mx-auto grid max-w-6xl items-center gap-10 px-4 py-16 md:grid-cols-2 md:py-24">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-brand-700">
              {info?.hospitalName || 'MediCare'}
            </p>
            <h1 className="mt-2 text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
              Smart, connected care for your clinic.
            </h1>
            <p className="mt-4 text-lg text-slate-600">
              MediCare brings appointments, consultations, prescriptions, laboratory, pharmacy and billing
              into one secure web application for patients, doctors and hospital staff.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Button to="/departments" size="lg">Explore departments</Button>
              <Button to="/status" size="lg" variant="secondary">Check system status</Button>
            </div>
          </div>
          <div className="rounded-2xl border border-brand-100 bg-white p-6 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-700">Our departments</h2>
            <ul className="mt-4 grid grid-cols-2 gap-3">
              {(info?.departments || []).map((d) => (
                <li key={d.id} className="rounded-lg bg-brand-50 px-3 py-2 text-sm font-medium text-brand-800">
                  {d.name}
                </li>
              ))}
              {!info && <li className="col-span-2 text-sm text-slate-400">Loading departments…</li>}
            </ul>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-14" aria-labelledby="features-heading">
        <h2 id="features-heading" className="text-2xl font-bold text-slate-900">Everything in one place</h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div key={title} className="rounded-xl border border-slate-200 bg-white p-5">
              <Icon className="h-6 w-6 text-brand-600" aria-hidden="true" />
              <h3 className="mt-3 font-semibold text-slate-900">{title}</h3>
              <p className="mt-1 text-sm text-slate-600">{text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 pb-16">
        <SystemStatusCard />
      </section>
    </>
  );
}
