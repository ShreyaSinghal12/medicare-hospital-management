import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import Button from '../../components/ui/Button.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { homeFor, safeRedirect } from '../../utils/roles.js';
import { EMAIL_PATTERN } from '../../utils/validation.js';
import AuthCard from './AuthCard.jsx';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [formError, setFormError] = useState(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { email: params.get('email') || '', password: '' } });

  async function onSubmit(values) {
    setFormError(null);
    try {
      const user = await login(values.email.trim(), values.password);
      toast.success(`Welcome back, ${user.fullName}!`);
      navigate(safeRedirect(params.get('redirect')) || homeFor(user), { replace: true });
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setFormError(err.message);
    }
  }

  return (
    <AuthCard
      title="Log in"
      subtitle="Access your MediCare account."
      footer={
        <>
          New patient? <Link to="/register" className="font-medium text-brand-700 hover:underline">Create an account</Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {formError && (
          <div role="alert" className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {formError}
          </div>
        )}
        <TextField
          label="E-mail address"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...register('email', {
            required: 'E-mail is required',
            pattern: { value: EMAIL_PATTERN, message: 'Enter a valid e-mail address' },
          })}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          error={errors.password?.message}
          {...register('password', { required: 'Password is required' })}
        />
        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm font-medium text-brand-700 hover:underline">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          {isSubmitting ? 'Logging in…' : 'Log in'}
        </Button>
      </form>
    </AuthCard>
  );
}
