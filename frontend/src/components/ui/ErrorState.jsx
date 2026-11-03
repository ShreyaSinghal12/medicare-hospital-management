import { AlertTriangle, RefreshCw } from 'lucide-react';
import Button from './Button.jsx';

export default function ErrorState({ error, onRetry, title = 'Something went wrong' }) {
  return (
    <div role="alert" className="flex flex-col items-center justify-center px-4 py-8 text-center">
      <AlertTriangle className="h-10 w-10 text-amber-500" aria-hidden="true" />
      <p className="mt-3 font-medium text-slate-800">{title}</p>
      <p className="mt-1 max-w-md text-sm text-slate-500">{error?.message || 'Unknown error'}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" /> Try again
        </Button>
      )}
    </div>
  );
}
