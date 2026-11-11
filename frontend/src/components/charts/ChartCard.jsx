import Card from '../ui/Card.jsx';

/**
 * A chart with a title, an accessible summary and a data table that can be opened, so the numbers never depend
 * on reading the chart alone.
 */
export default function ChartCard({ title, subtitle, summary, columns, rows, children, empty }) {
  return (
    <Card title={title} subtitle={subtitle}>
      {empty ? <p className="text-sm text-slate-500">{empty}</p> : (
        <>
          <figure aria-label={summary} className="m-0">
            <div className="h-56 w-full">{children}</div>
            <figcaption className="sr-only">{summary}</figcaption>
          </figure>
          <details className="mt-3 text-sm">
            <summary className="cursor-pointer text-slate-500 hover:text-slate-700">Show data</summary>
            <div className="mt-2 max-h-56 overflow-auto" tabIndex={0} role="region" aria-label="Scrollable table">
              <table className="w-full text-left text-xs">
                <thead className="text-slate-500"><tr>{columns.map((c) => <th key={c} scope="col" className="py-1 pr-3 font-semibold">{c}</th>)}</tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((r, i) => <tr key={i}>{r.map((v, j) => <td key={j} className="py-1 pr-3 text-slate-700">{v}</td>)}</tr>)}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </Card>
  );
}
