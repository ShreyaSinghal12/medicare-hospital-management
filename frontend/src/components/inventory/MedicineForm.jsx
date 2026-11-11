import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { createMedicine, MEDICINE_CATEGORIES, titleCase, updateMedicine } from '../../services/inventoryService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import Button from '../ui/Button.jsx';
import { SelectField, TextField } from '../ui/FormField.jsx';

/** Add or edit a catalog medicine. */
export default function MedicineForm({ medicine, onSaved, onCancel }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: {
      name: medicine?.name || '', genericName: medicine?.genericName || '', manufacturer: medicine?.manufacturer || '',
      category: medicine?.category || '', strength: medicine?.strength || '', unit: medicine?.unit || '',
      unitPrice: medicine?.unitPrice ?? '', reorderLevel: medicine?.reorderLevel ?? 20,
    },
  });

  async function onSubmit(v) {
    const payload = { ...v, unitPrice: Number(v.unitPrice), reorderLevel: Number(v.reorderLevel) };
    try {
      const saved = medicine ? await updateMedicine(medicine.id, payload) : await createMedicine(payload);
      toast.success(`${saved.medicine.name} ${saved.medicine.strength} ${medicine ? 'updated' : 'added'}`);
      onSaved(saved);
    } catch (err) {
      if (err.code === 'MEDICINE_EXISTS') setError('name', { type: 'server', message: err.message });
      else if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  const max = (n) => ({ value: n, message: `Max ${n} characters` });
  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
      <TextField label="Brand / name" required error={errors.name?.message}
        {...register('name', { required: 'Required', minLength: { value: 2, message: 'At least 2 characters' }, maxLength: max(150) })} />
      <TextField label="Generic name" error={errors.genericName?.message} {...register('genericName', { maxLength: max(150) })} />
      <TextField label="Manufacturer" required error={errors.manufacturer?.message}
        {...register('manufacturer', { required: 'Required', maxLength: max(150) })} />
      <SelectField label="Form" required placeholder="Select" error={errors.category?.message}
        options={MEDICINE_CATEGORIES.map((c) => ({ value: c, label: titleCase(c) }))}
        {...register('category', { required: 'Required' })} />
      <TextField label="Strength" required placeholder="500 mg" error={errors.strength?.message}
        {...register('strength', { required: 'Required', maxLength: max(50) })} />
      <TextField label="Unit" required placeholder="tablet, bottle …" error={errors.unit?.message}
        {...register('unit', { required: 'Required', maxLength: max(20) })} />
      <TextField label="Price per unit (₹)" type="number" step="0.01" min="0" required error={errors.unitPrice?.message}
        {...register('unitPrice', { required: 'Required', min: { value: 0, message: 'Cannot be negative' }, max: { value: 100000, message: 'Max 100000' } })} />
      <TextField label="Reorder level" type="number" min="0" required hint="Low-stock alert at or below this many units."
        error={errors.reorderLevel?.message}
        {...register('reorderLevel', { required: 'Required', min: { value: 0, message: 'Cannot be negative' }, max: { value: 100000, message: 'Max 100000' } })} />
      <div className="flex justify-end gap-2 sm:col-span-2">
        <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>{medicine ? 'Save' : 'Add medicine'}</Button>
      </div>
    </form>
  );
}
