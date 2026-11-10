import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { Plus, Trash2 } from 'lucide-react';
import Button from '../ui/Button.jsx';
import { TextAreaField, TextField } from '../ui/FormField.jsx';

const EMPTY_ITEM = { medicineName: '', strength: '', dosage: '', frequency: '', duration: '', instructions: '', quantity: '' };
export const MAX_ITEMS = 20;

function toForm(rx) {
  return {
    generalInstructions: rx?.generalInstructions || '',
    items: rx?.items?.length ? rx.items.map((i) => ({ ...EMPTY_ITEM, ...i, strength: i.strength || '', instructions: i.instructions || '', quantity: i.quantity ?? '' }))
      : [{ ...EMPTY_ITEM }],
  };
}

export function toPayload(values) {
  return {
    generalInstructions: values.generalInstructions || null,
    items: values.items.map((i) => ({
      medicineName: i.medicineName.trim(), strength: i.strength || null, dosage: i.dosage.trim(), frequency: i.frequency.trim(),
      duration: i.duration.trim(), instructions: i.instructions || null, quantity: i.quantity === '' ? null : Number(i.quantity),
    })),
  };
}

/**
 * Medicine-line editor. Every field is typed by the doctor; the system offers no
 * suggestions, defaults or dose calculations.
 */
export default function PrescriptionEditor({ rx, onSave, onFinalize, busy }) {
  const { register, control, handleSubmit, reset, formState: { errors, isDirty } } = useForm({ defaultValues: toForm(rx) });
  const { fields, append, remove } = useFieldArray({ control, name: 'items' });
  const [error, setError] = useState(null);
  useEffect(() => reset(toForm(rx)), [rx, reset]);

  const req = (label) => ({ validate: (v) => (v && v.trim().length > 0) || `${label} is required` });
  const submit = (fn) => handleSubmit((v) => { setError(null); return fn(toPayload(v), isDirty); }, () => setError('Please fix the highlighted fields'));

  return (
    <form noValidate onSubmit={(e) => e.preventDefault()} className="space-y-4">
      {fields.map((field, idx) => {
        const e = errors.items?.[idx] || {};
        return (
          <fieldset key={field.id} className="rounded-lg border border-slate-200 p-4">
            <legend className="px-1 text-sm font-semibold text-slate-700">Medicine {idx + 1}</legend>
            <div className="grid gap-3 sm:grid-cols-6">
              <div className="sm:col-span-4">
                <TextField label="Medicine name" required error={e.medicineName?.message}
                  {...register(`items.${idx}.medicineName`, { ...req('Medicine name'), maxLength: { value: 150, message: 'Max 150' } })} />
              </div>
              <div className="sm:col-span-2">
                <TextField label="Strength" placeholder="e.g. 500 mg" {...register(`items.${idx}.strength`, { maxLength: { value: 50, message: 'Max 50' } })} />
              </div>
              <div className="sm:col-span-2">
                <TextField label="Dosage" required error={e.dosage?.message} {...register(`items.${idx}.dosage`, req('Dosage'))} />
              </div>
              <div className="sm:col-span-2">
                <TextField label="Frequency" required error={e.frequency?.message} {...register(`items.${idx}.frequency`, req('Frequency'))} />
              </div>
              <div className="sm:col-span-2">
                <TextField label="Duration" required error={e.duration?.message} {...register(`items.${idx}.duration`, req('Duration'))} />
              </div>
              <div className="sm:col-span-5">
                <TextField label="Instructions" {...register(`items.${idx}.instructions`, { maxLength: { value: 500, message: 'Max 500' } })} />
              </div>
              <div className="sm:col-span-1">
                <TextField label="Qty" type="number" min={1} error={e.quantity?.message}
                  {...register(`items.${idx}.quantity`, { validate: (v) => v === '' || (Number(v) >= 1 && Number.isInteger(Number(v))) || 'Whole number ≥ 1' })} />
              </div>
            </div>
            {fields.length > 1 && (
              <Button variant="ghost" size="sm" className="mt-2" onClick={() => remove(idx)} aria-label={`Remove medicine ${idx + 1}`}>
                <Trash2 className="h-4 w-4" aria-hidden="true" /> Remove
              </Button>
            )}
          </fieldset>
        );
      })}
      <Button variant="secondary" size="sm" onClick={() => append({ ...EMPTY_ITEM })} disabled={fields.length >= MAX_ITEMS}>
        <Plus className="h-4 w-4" aria-hidden="true" /> Add medicine
      </Button>
      <TextAreaField label="General instructions" rows={2} {...register('generalInstructions', { maxLength: { value: 1000, message: 'Max 1000' } })} />
      {error && <p role="alert" className="text-sm font-medium text-red-600">{error}</p>}
      <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-4">
        <Button variant="secondary" onClick={submit(onSave)} loading={busy === 'save'} disabled={!!busy}>Save draft</Button>
        <Button onClick={submit(onFinalize)} loading={busy === 'finalize'} disabled={!!busy}>Finalize prescription</Button>
      </div>
    </form>
  );
}
