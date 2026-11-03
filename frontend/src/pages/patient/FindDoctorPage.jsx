import DoctorDirectory from '../../components/doctors/DoctorDirectory.jsx';
import DoctorProfile from '../../components/doctors/DoctorProfile.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';

export function FindDoctorPage() {
  return (
    <>
      <PageHeader title="Find a doctor" description="Search by name, department or specialization." />
      <DoctorDirectory profileBase="/patient/doctors" />
    </>
  );
}

export function PatientDoctorProfilePage() {
  return <DoctorProfile backTo="/patient/doctors" />;
}
