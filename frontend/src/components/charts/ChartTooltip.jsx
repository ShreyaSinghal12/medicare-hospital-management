/** Tooltip in text colours (never the series colour); the value is primary, the label secondary. */
export default function ChartTooltip({ active, payload, label, format = (v) => v, labelFormat = (l) => l }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="text-slate-500">{labelFormat(label)}</p>
      <p className="mt-0.5 text-sm font-semibold text-slate-900">{format(payload[0].value)}</p>
    </div>
  );
}
