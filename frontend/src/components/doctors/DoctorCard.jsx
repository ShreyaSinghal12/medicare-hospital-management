import { Link } from 'react-router-dom';
import { Award, Building2, Clock } from 'lucide-react';
import { formatCurrency } from '../../utils/format.js';

function initials(name) {
  return name.replace(/^Dr\.?\s+/i, '').split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
}

export default function DoctorCard({ doctor, to }) {
  return (
    <article className="flex flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="flex items-start gap-4">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-800" aria-hidden="true">
          {initials(doctor.fullName)}
        </span>
        <div className="min-w-0">
          <h2 className="font-semibold text-slate-900">
            <Link to={to} className="hover:text-brand-700 hover:underline">{doctor.fullName}</Link>
          </h2>
          <p className="text-sm text-brand-700">{doctor.specialization}</p>
          <p className="mt-0.5 truncate text-xs text-slate-500">{doctor.qualifications}</p>
        </div>
      </div>
      <ul className="mt-4 space-y-1.5 text-sm text-slate-600">
        <li className="flex items-center gap-2"><Building2 className="h-4 w-4 text-slate-400" aria-hidden="true" />{doctor.departmentName}</li>
        <li className="flex items-center gap-2"><Award className="h-4 w-4 text-slate-400" aria-hidden="true" />{doctor.experienceYears} years experience</li>
        <li className="flex items-center gap-2"><Clock className="h-4 w-4 text-slate-400" aria-hidden="true" />{doctor.slotDurationMinutes}-minute consultations</li>
      </ul>
      <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4">
        <p className="text-sm text-slate-500">
          Fee <span className="font-semibold text-slate-900">{formatCurrency(doctor.consultationFee)}</span>
        </p>
        <Link to={to} className="text-sm font-medium text-brand-700 hover:underline" aria-label={`View profile of ${doctor.fullName}`}>
          View profile →
        </Link>
      </div>
    </article>
  );
}
