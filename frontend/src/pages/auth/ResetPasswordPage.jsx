import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PasswordChecklist from '../../components/ui/PasswordChecklist.jsx';
import { resetPassword } from '../../services/authService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { isStrongPassword } from '../../utils/validation.js';
import AuthCard from './AuthCard.jsx';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const navigate = useNavigate();
  const [formError, setFormError] = useState(null);
  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { newPassword: '', confirmPassword: '' } });
  const newPassword = watch('newPassword');

  async function onSubmit(values) {
    setFormError(null);
    try {
      const res = await resetPassword({ token, ...values });
      toast.success(res.message);
      navigate('/login', { replace: true });
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setFormError(err.message);
    }
  }

  if (!token) {
    return (
      <AuthCard title="Invalid reset link" footer={<Link to="/forgot-password" className="font-medium text-brand-700 hover:underline">Request a new link</Link>}>
        <p className="text-sm text-slate-600">This page must be opened from the link in your password reset e-mail.</p>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password" footer={<Link to="/login" className="font-medium text-brand-700 hover:underline">Back to log in</Link>}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {formError && (
          <div role="alert" className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>
              {formError}{' '}
              <Link to="/forgot-password" className="font-medium underline">Request a new link</Link>
            </span>
          </div>
        )}
        <div>
          <TextField
            label="New password" type="password" required autoComplete="new-password" error={errors.newPassword?.message}
            {...register('newPassword', { required: 'Password is required', validate: isStrongPassword })}
          />
          <PasswordChecklist value={newPassword} />
        </div>
        <TextField
          label="Confirm new password" type="password" required autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword', {
            required: 'Please confirm your password',
            validate: (v) => v === newPassword || 'Passwords do not match',
          })}
        />
        <Button type="submit" className="w-full" loading={isSubmitting}>Reset password</Button>
      </form>
    </AuthCard>
  );
}
