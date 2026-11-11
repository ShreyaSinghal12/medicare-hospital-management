import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Info } from 'lucide-react';
import LabResultsTable from '../../components/lab/LabResultsTable.jsx';
import LabStatusBadge from '../../components/lab/LabStatusBadge.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import useApi from '../../hooks/useApi.js';
import { getLabOrder, labReportUrl } from '../../services/labService.js';
import { formatDateLong } from '../../utils/dates.js';
import { formatDateTime } from '../../utils/format.js';

/** A patient's lab order: status while pending; results and the PDF once released by the doctor. */
export default function PatientLabReportPage() {
  const { id } = useParams();
  const { data: o, loading, error, reload } = useApi(() => getLabOrder(id), [id]);

  return (
    <>
      <Link to="/patient/lab-reports" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Lab reports
      </Link>
      {loading && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {o && (
        <>
          <PageHeader title={o.items.map((i) => i.testName).join(', ')}
            description={`${o.orderNo} · ordered by ${o.doctorName} on ${formatDateLong(o.visitDate)}`}
            actions={<LabStatusBadge status={o.status} />} />
          {o.status === 'COMPLETED' ? (
            <div className="space-y-6">
              <Card title="Results" subtitle={`Released ${formatDateTime(o.completedAt)}`}
                actions={o.report && (
                  <a href={labReportUrl(o.id)} download
                    className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700">
                    <Download className="h-4 w-4" aria-hidden="true" /> Download report (PDF)
                  </a>
                )}>
                <LabResultsTable items={o.items} />
              </Card>
              <p className="flex items-start gap-2 text-sm text-slate-600">
                <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
                Reference ranges are general information from the laboratory. MediCare does not interpret results; please discuss them with your doctor.
              </p>
            </div>
          ) : (
            <Card>
              <p className="text-sm text-slate-700">
                {o.status === 'CANCELLED'
                  ? `This order was cancelled${o.cancellationReason ? `: ${o.cancellationReason}` : ''}.`
                  : 'Your results will appear here once your doctor has reviewed and released the report. You will get a notification.'}
              </p>
            </Card>
          )}
        </>
      )}
    </>
  );
}
