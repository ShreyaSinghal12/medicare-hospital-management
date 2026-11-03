/** Simple accessible tab buttons (the caller renders the active panel). */
export default function Tabs({ tabs, value, onChange, label }) {
  return (
    <div role="tablist" aria-label={label} className="mb-5 flex flex-wrap gap-1 border-b border-slate-200">
      {tabs.map((t) => (
        <button
          key={t.value}
          type="button"
          role="tab"
          aria-selected={value === t.value}
          onClick={() => onChange(t.value)}
          className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
            value === t.value
              ? 'border-brand-600 text-brand-700'
              : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
          }`}
        >
          {t.label}
          {t.count !== undefined && (
            <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{t.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
