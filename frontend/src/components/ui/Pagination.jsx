import { ChevronLeft, ChevronRight } from 'lucide-react';
import Button from './Button.jsx';

/** Previous/next pager for PageResponse objects ({ page, totalPages, totalElements, first, last }). */
export default function Pagination({ data, onPageChange }) {
  if (!data || data.totalPages <= 1) {
    return data ? <p className="text-sm text-slate-500">{data.totalElements} result(s)</p> : null;
  }
  return (
    <nav className="flex flex-wrap items-center justify-between gap-3" aria-label="Pagination">
      <p className="text-sm text-slate-500">
        Page {data.page + 1} of {data.totalPages} · {data.totalElements} result(s)
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={data.first} onClick={() => onPageChange(data.page - 1)}>
          <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={data.last} onClick={() => onPageChange(data.page + 1)}>
          Next <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </nav>
  );
}
