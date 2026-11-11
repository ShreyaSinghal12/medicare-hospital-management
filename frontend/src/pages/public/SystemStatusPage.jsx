import SystemStatusCard from '../../components/SystemStatusCard.jsx';
import PageHeading from '../../components/ui/PageHeading.jsx';

export default function SystemStatusPage() {
  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <PageHeading title={'System status'} className="text-3xl font-bold text-slate-900">System status</PageHeading>
      <p className="mt-2 text-slate-600">
        Verifies that the React frontend, the Spring Boot API and the MySQL database are connected.
      </p>
      <div className="mt-8">
        <SystemStatusCard />
      </div>
    </div>
  );
}
