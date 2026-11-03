import DoctorDirectory from '../../components/doctors/DoctorDirectory.jsx';

export default function DoctorsPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold text-slate-900">Find a doctor</h1>
      <p className="mt-2 text-slate-600">Search our specialists by name, department or specialization.</p>
      <div className="mt-8">
        <DoctorDirectory profileBase="/doctors" />
      </div>
    </div>
  );
}
