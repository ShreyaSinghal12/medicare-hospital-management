import DoctorProfile from '../../components/doctors/DoctorProfile.jsx';

export default function DoctorProfilePage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <DoctorProfile backTo="/doctors" />
    </div>
  );
}
