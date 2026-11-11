import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { AlertCircle, MailCheck } from 'lucide-react';
import Button from '../../components/ui/Button.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import { forgotPassword } from '../../services/authService.js';
import { EMAIL_PATTERN } from '../../utils/validation.js';
import AuthCard from './AuthCard.jsx';

export default function ForgotPasswordPage() {
  const [sentMessage, setSentMessage] = useState(null);
  const [formError, setFormError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { email: '' } });

  async function onSubmit({ email }) {
    setFormError(null);
    try {
      const res = await forgotPassword(email.trim());
      setSentMessage(res.message);
    } catch (err) {
      setFormError(err.message);
    }
  }

  return (
    <AuthCard
      title="Forgot your password?"
      subtitle="Enter your e-mail and we'll send you a link to reset it."
      footer={<Link to="/login" className="font-medium text-brand-700 hover:underline">Back to log in</Link>}
    >
      {sentMessage ? (
        <div role="status" className="flex flex-col items-center text-center">
          <MailCheck className="h-12 w-12 text-brand-700" aria-hidden="true" />
          <p className="mt-3 text-sm text-slate-700">{sentMessage}</p>
          <p className="mt-2 text-xs text-slate-500">The link expires after a short time and can be used only once.</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          {formError && (
            <div role="alert" className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {formError}
            </div>
          )}
          <TextField
            label="E-mail address" type="email" autoComplete="email" required error={errors.email?.message}
            {...register('email', {
              required: 'E-mail is required',
              pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' },
            })}
          />
          <Button type="submit" className="w-full" loading={isSubmitting}>Send reset link</Button>
        </form>
      )}
    </AuthCard>
  );
}
