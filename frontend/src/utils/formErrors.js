/**
 * Copies server-side validation errors ({ fieldErrors: [{field, message}] })
 * onto react-hook-form fields. Returns true when at least one field was set.
 */
export function applyServerFieldErrors(error, setError) {
  const fields = error?.fieldErrors || [];
  fields.forEach(({ field, message }) => setError(field, { type: 'server', message }));
  return fields.length > 0;
}
