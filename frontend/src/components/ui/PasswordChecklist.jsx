import { Check, X } from 'lucide-react';
import { passwordChecks } from '../../utils/validation.js';

/** Live password-strength feedback shown under "new password" inputs. */
export default function PasswordChecklist({ value }) {
  return (
    <ul className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs" aria-label="Password requirements">
      {passwordChecks(value).map(({ label, ok }) => (
        <li key={label} className={`flex items-center gap-1.5 ${ok ? 'text-emerald-700' : 'text-slate-500'}`}>
          {ok ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <X className="h-3.5 w-3.5" aria-hidden="true" />}
          <span>{label}</span>
          <span className="sr-only">{ok ? '(met)' : '(not met)'}</span>
        </li>
      ))}
    </ul>
  );
}
