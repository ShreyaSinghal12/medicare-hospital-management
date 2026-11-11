import { useState } from 'react';
import { Search } from 'lucide-react';
import useApi from '../../hooks/useApi.js';
import useDebouncedValue from '../../hooks/useDebouncedValue.js';
import { searchReceptionPatients } from '../../services/receptionService.js';
import Button from '../ui/Button.jsx';
import { TextField } from '../ui/FormField.jsx';
import Spinner from '../ui/Spinner.jsx';

/** Search-as-you-type patient selector for front-desk booking. */
export default function PatientPicker({ onPick }) {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input);
  const { data, loading } = useApi(() => (search.trim().length >= 2 ? searchReceptionPatients({ search, size: 8 }) : Promise.resolve(null)), [search]);
  return (
    <div>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-[2.15rem] z-10 h-4 w-4 text-slate-500" aria-hidden="true" />
        <TextField label="Find patient" placeholder="Name, phone, e-mail or patient ID (min. 2 characters)" value={input}
          className="pl-9" onChange={(e) => setInput(e.target.value)} />
      </div>
      {loading && search.length >= 2 && <Spinner className="mt-2" />}
      {data && data.content.length === 0 && (
        <p className="mt-2 text-sm text-slate-500">No patient found. <Button to="/reception/patients/new" variant="ghost" size="sm">Register walk-in</Button></p>
      )}
      <ul className="mt-2 divide-y divide-slate-100">
        {(data?.content || []).map((p) => (
          <li key={p.id} className="flex items-center justify-between gap-2 py-2 text-sm">
            <span><strong>{p.fullName}</strong> · {p.patientCode} · {p.phone} · born {p.dateOfBirth}</span>
            <Button size="sm" variant="secondary" onClick={() => onPick(p)}>Select</Button>
          </li>
        ))}
      </ul>
    </div>
  );
}
