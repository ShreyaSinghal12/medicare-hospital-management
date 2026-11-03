import { Loader2 } from 'lucide-react';

export default function Spinner({ label = 'Loading…', className = '' }) {
  return (
    <div role="status" className={`flex items-center gap-2 text-sm text-slate-500 ${className}`}>
      <Loader2 className="h-5 w-5 animate-spin text-brand-600" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
