import { SearchX } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';

export default function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center px-4 py-24 text-center">
      <SearchX className="h-14 w-14 text-slate-300" aria-hidden="true" />
      <p className="mt-4 text-sm font-semibold text-brand-700">404</p>
      <h1 className="mt-1 text-3xl font-bold text-slate-900">Page not found</h1>
      <p className="mt-2 text-slate-600">The page you are looking for does not exist or has been moved.</p>
      <Button to="/" className="mt-8">Back to home</Button>
    </div>
  );
}
