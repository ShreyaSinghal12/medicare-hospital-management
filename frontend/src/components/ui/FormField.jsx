import { forwardRef, useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

const BASE = `block w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm
  placeholder:text-slate-400 focus:outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-500`;

function stateClass(error) {
  return error
    ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
    : 'border-slate-300 focus:border-brand-500 focus:ring-brand-100';
}

/** Labelled form control wrapper with accessible error and hint text. */
function Field({ id, label, required, error, hint, children }) {
  return (
    <div>
      {label && (
        <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
          {label} {required && <span className="text-red-600" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {hint && !error && <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && (
        <p id={`${id}-error`} role="alert" className="mt-1 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}

function describedBy(id, error, hint) {
  if (error) return `${id}-error`;
  if (hint) return `${id}-hint`;
  return undefined;
}

export const TextField = forwardRef(function TextField(
  { label, error, hint, required, type = 'text', className = '', ...rest },
  ref,
) {
  const id = useId();
  const [show, setShow] = useState(false);
  const isPassword = type === 'password';
  return (
    <Field id={id} label={label} required={required} error={error} hint={hint}>
      <div className="relative">
        <input
          id={id}
          ref={ref}
          type={isPassword && show ? 'text' : type}
          aria-invalid={!!error}
          aria-required={required || undefined}
          aria-describedby={describedBy(id, error, hint)}
          className={`${BASE} ${stateClass(error)} ${isPassword ? 'pr-10' : ''} ${className}`}
          {...rest}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShow((v) => !v)}
            className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-500 hover:text-slate-700"
            aria-label={show ? 'Hide password' : 'Show password'}
          >
            {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        )}
      </div>
    </Field>
  );
});

export const SelectField = forwardRef(function SelectField(
  { label, error, hint, required, options, placeholder, ...rest },
  ref,
) {
  const id = useId();
  return (
    <Field id={id} label={label} required={required} error={error} hint={hint}>
      <select
        id={id}
        ref={ref}
        aria-invalid={!!error}
        aria-describedby={describedBy(id, error, hint)}
        className={`${BASE} ${stateClass(error)}`}
        {...rest}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Field>
  );
});

export const TextAreaField = forwardRef(function TextAreaField(
  { label, error, hint, required, rows = 3, ...rest },
  ref,
) {
  const id = useId();
  return (
    <Field id={id} label={label} required={required} error={error} hint={hint}>
      <textarea
        id={id}
        ref={ref}
        rows={rows}
        aria-invalid={!!error}
        aria-describedby={describedBy(id, error, hint)}
        className={`${BASE} ${stateClass(error)}`}
        {...rest}
      />
    </Field>
  );
});
