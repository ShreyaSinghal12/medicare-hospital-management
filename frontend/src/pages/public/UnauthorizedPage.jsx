import { ShieldAlert } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';

export default function UnauthorizedPage() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center px-4 py-24 text-center">
      <ShieldAlert className="h-14 w-14 text-amber-400" aria-hidden="true" />
      <p className="mt-4 text-sm font-semibold text-amber-700">403</p>
      <h1 className="mt-1 text-3xl font-bold text-slate-900">Access denied</h1>
      <p className="mt-2 text-slate-600">
        You do not have permission to view this page. If you believe this is a mistake, contact the hospital administrator.
      </p>
      <Button to="/" className="mt-8">Back to home</Button>
    </div>
  );
}
