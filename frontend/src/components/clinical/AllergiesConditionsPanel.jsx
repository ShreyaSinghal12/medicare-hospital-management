import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { AlertTriangle, HeartPulse, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { addAllergy, addCondition, setAllergyActive, setConditionStatus } from '../../services/clinicalService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import Button from '../ui/Button.jsx';
import Card from '../ui/Card.jsx';
import { SelectField, TextField } from '../ui/FormField.jsx';
import StatusBadge from '../ui/StatusBadge.jsx';

const SEVERITY_TONE = { MILD: 'slate', MODERATE: 'amber', SEVERE: 'red' };
const CONDITION_TONE = { ACTIVE: 'amber', CHRONIC: 'blue', RESOLVED: 'green' };

function AllergyForm({ patientId, onSaved }) {
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { allergen: '', reaction: '', severity: '' },
  });
  async function onSubmit(v) {
    try {
      await addAllergy(patientId, { ...v, severity: v.severity || null });
      toast.success('Allergy recorded');
      reset();
      onSaved();
    } catch (err) {
      if (err.code === 'ALLERGY_EXISTS') setError('allergen', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-3 grid gap-2 rounded-lg bg-slate-50 p-3">
      <TextField label="Allergen" required error={errors.allergen?.message}
        {...register('allergen', { required: 'Required', maxLength: { value: 150, message: 'Max 150' } })} />
      <TextField label="Reaction" {...register('reaction', { maxLength: { value: 255, message: 'Max 255' } })} />
      <SelectField label="Severity" placeholder="Not specified"
        options={['MILD', 'MODERATE', 'SEVERE'].map((s) => ({ value: s, label: s.toLowerCase() }))} {...register('severity')} />
      <Button type="submit" size="sm" loading={isSubmitting}>Save allergy</Button>
    </form>
  );
}

function ConditionForm({ patientId, onSaved }) {
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { conditionName: '', status: 'ACTIVE', diagnosedOn: '', notes: '' },
  });
  async function onSubmit(v) {
    try {
      await addCondition(patientId, { ...v, diagnosedOn: v.diagnosedOn || null });
      toast.success('Condition recorded');
      reset();
      onSaved();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-3 grid gap-2 rounded-lg bg-slate-50 p-3">
      <TextField label="Condition" required error={errors.conditionName?.message}
        {...register('conditionName', { required: 'Required', maxLength: { value: 150, message: 'Max 150' } })} />
      <SelectField label="Status" options={['ACTIVE', 'CHRONIC', 'RESOLVED'].map((s) => ({ value: s, label: s.toLowerCase() }))}
        {...register('status')} />
      <TextField label="Diagnosed on" type="date" error={errors.diagnosedOn?.message} {...register('diagnosedOn')} />
      <TextField label="Notes" {...register('notes', { maxLength: { value: 500, message: 'Max 500' } })} />
      <Button type="submit" size="sm" loading={isSubmitting}>Save condition</Button>
    </form>
  );
}

/**
 * Allergies and conditions of a patient. Editable only by a treating doctor (`editable`);
 * every change is entered manually — nothing is suggested.
 */
export default function AllergiesConditionsPanel({ patientId, allergies, conditions, editable, onChanged }) {
  const [showAllergyForm, setShowAllergyForm] = useState(false);
  const [showConditionForm, setShowConditionForm] = useState(false);

  async function toggleAllergy(a) {
    try {
      await setAllergyActive(patientId, a.id, !a.active);
      onChanged();
    } catch (err) {
      toast.error(err.message);
    }
  }
  async function changeCondition(c, status) {
    try {
      await setConditionStatus(patientId, c.id, status);
      onChanged();
    } catch (err) {
      toast.error(err.message);
    }
  }

  return (
    <div className="space-y-6">
      <Card title="Allergies" actions={editable && (
        <Button size="sm" variant="secondary" onClick={() => setShowAllergyForm((v) => !v)}>
          <Plus className="h-4 w-4" aria-hidden="true" /> Add
        </Button>
      )}>
        {allergies.length === 0 && <p className="text-sm text-slate-500">No known allergies recorded.</p>}
        <ul className="space-y-2">
          {allergies.map((a) => (
            <li key={a.id} className={`flex items-start justify-between gap-2 text-sm ${a.active ? '' : 'opacity-50'}`}>
              <div>
                <p className="flex items-center gap-1.5 font-medium text-slate-900">
                  <AlertTriangle className="h-4 w-4 text-red-500" aria-hidden="true" /> {a.allergen}
                  {a.severity && <StatusBadge tone={SEVERITY_TONE[a.severity]}>{a.severity.toLowerCase()}</StatusBadge>}
                  {!a.active && <StatusBadge tone="slate">inactive</StatusBadge>}
                </p>
                {a.reaction && <p className="text-slate-500">{a.reaction}</p>}
              </div>
              {editable && (
                <button type="button" onClick={() => toggleAllergy(a)} className="text-xs font-medium text-brand-700 hover:underline">
                  {a.active ? 'Deactivate' : 'Reactivate'}
                </button>
              )}
            </li>
          ))}
        </ul>
        {editable && showAllergyForm && <AllergyForm patientId={patientId} onSaved={() => { setShowAllergyForm(false); onChanged(); }} />}
      </Card>

      <Card title="Conditions" actions={editable && (
        <Button size="sm" variant="secondary" onClick={() => setShowConditionForm((v) => !v)}>
          <Plus className="h-4 w-4" aria-hidden="true" /> Add
        </Button>
      )}>
        {conditions.length === 0 && <p className="text-sm text-slate-500">No conditions recorded.</p>}
        <ul className="space-y-2">
          {conditions.map((c) => (
            <li key={c.id} className="flex items-start justify-between gap-2 text-sm">
              <div>
                <p className="flex items-center gap-1.5 font-medium text-slate-900">
                  <HeartPulse className="h-4 w-4 text-brand-600" aria-hidden="true" /> {c.conditionName}
                  <StatusBadge tone={CONDITION_TONE[c.status]}>{c.status.toLowerCase()}</StatusBadge>
                </p>
                {(c.diagnosedOn || c.notes) && <p className="text-slate-500">{[c.diagnosedOn, c.notes].filter(Boolean).join(' · ')}</p>}
              </div>
              {editable && (
                <select aria-label={`Status of ${c.conditionName}`} value={c.status} onChange={(e) => changeCondition(c, e.target.value)}
                  className="rounded border border-slate-300 px-1.5 py-0.5 text-xs">
                  {['ACTIVE', 'CHRONIC', 'RESOLVED'].map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}
                </select>
              )}
            </li>
          ))}
        </ul>
        {editable && showConditionForm && <ConditionForm patientId={patientId} onSaved={() => { setShowConditionForm(false); onChanged(); }} />}
      </Card>
    </div>
  );
}
