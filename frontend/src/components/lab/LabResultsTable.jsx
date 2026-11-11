/**
 * Read-only results as typed by the doctor. Reference ranges come from the catalog and are shown for
 * information only; MediCare does not flag or interpret values.
 */
export default function LabResultsTable({ items }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[32rem] text-left text-sm">
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-2 pr-3 font-semibold">Test</th>
            <th scope="col" className="py-2 pr-3 font-semibold">Result</th>
            <th scope="col" className="py-2 pr-3 font-semibold">Reference range</th>
            <th scope="col" className="py-2 font-semibold">Comment</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((i) => (
            <tr key={i.id}>
              <td className="py-2 pr-3 font-medium text-slate-900">{i.testName}</td>
              <td className="py-2 pr-3 text-slate-800">{i.resultValue ? `${i.resultValue}${i.unit ? ` ${i.unit}` : ''}` : '—'}</td>
              <td className="py-2 pr-3 text-slate-600">{i.referenceRange ? `${i.referenceRange}${i.unit ? ` ${i.unit}` : ''}` : '—'}</td>
              <td className="py-2 text-slate-600">{i.resultComment || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
