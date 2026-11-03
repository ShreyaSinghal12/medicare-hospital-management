import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';

/**
 * Accessible modal built on the native <dialog> element (focus trap, Escape, backdrop).
 * Content is only rendered while open so forms reset between openings.
 */
export default function Modal({ open, title, description, onClose, children, size = 'md', busy = false }) {
  const ref = useRef(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal();
      else dialog.setAttribute('open', '');
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close();
      else dialog.removeAttribute('open');
    }
  }, [open]);

  const width = size === 'lg' ? 'w-[min(44rem,calc(100vw-2rem))]' : 'w-[min(32rem,calc(100vw-2rem))]';

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
      className={`m-auto max-h-[calc(100vh-2rem)] ${width} rounded-xl border border-slate-200 p-0 shadow-xl backdrop:bg-slate-900/40`}
    >
      {open && (
        <div className="flex max-h-[calc(100vh-2rem)] flex-col">
          <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-4">
            <div>
              <h2 id={titleId} className="text-base font-semibold text-slate-900">{title}</h2>
              {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
            </div>
            <button type="button" onClick={onClose} disabled={busy} aria-label="Close"
              className="rounded-md p-1 text-slate-500 hover:bg-slate-100 disabled:opacity-50">
              <X className="h-5 w-5" />
            </button>
          </div>
          <div className="overflow-y-auto px-6 py-5">{children}</div>
        </div>
      )}
    </dialog>
  );
}
