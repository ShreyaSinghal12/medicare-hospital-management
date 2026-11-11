import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import NotificationPreferencesCard from '../../components/account/NotificationPreferencesCard.jsx';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import PasswordChecklist from '../../components/ui/PasswordChecklist.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { changePassword, updateAccount } from '../../services/accountService.js';
import { applyServerFieldErrors } from '../../utils/formErrors.js';
import { ROLE_LABEL } from '../../utils/roles.js';
import { NAME_PATTERN, PHONE_PATTERN, isStrongPassword } from '../../utils/validation.js';

function AccountForm() {
  const { user, setUser } = useAuth();
  const {
    register, handleSubmit, setError, reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm({
    defaultValues: { fullName: user.fullName, phone: user.phone || '' },
  });

  async function onSubmit(values) {
    try {
      // the e-mail master switch is edited in the notifications card; keep its current value here
      const me = await updateAccount({ ...values, emailNotificationsEnabled: user.emailNotificationsEnabled });
      setUser(me);
      reset({ fullName: me.fullName, phone: me.phone || '' });
      toast.success('Account updated');
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toast.error(err.message);
    }
  }

  return (
    <Card title="Account details" subtitle="Your e-mail address and role can only be changed by an administrator.">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
        <TextField label="E-mail" value={user.email} disabled readOnly />
        <TextField label="Role" value={ROLE_LABEL[user.role]} disabled readOnly />
        <TextField
          label="Full name" required error={errors.fullName?.message}
          {...register('fullName', {
            required: 'Full name is required',
            pattern: { value: NAME_PATTERN, message: 'Use letters, spaces, dots, apostrophes or hyphens (2–120)' },
          })}
        />
        <TextField
          label="Phone" type="tel" required error={errors.phone?.message}
          {...register('phone', {
            required: 'Phone is required',
            pattern: { value: PHONE_PATTERN, message: 'Enter a valid phone number' },
          })}
        />
        <div className="sm:col-span-2">
          <Button type="submit" loading={isSubmitting} disabled={!isDirty}>Save changes</Button>
        </div>
      </form>
    </Card>
  );
}

function PasswordForm() {
  const {
    register, handleSubmit, watch, setError, reset,
    formState: { errors, isSubmitting },
  } = useForm({ defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' } });
  const newPassword = watch('newPassword');

  async function onSubmit(values) {
    try {
      await changePassword(values);
      reset();
      toast.success('Password changed. Other devices have been signed out.');
    } catch (err) {
      if (err.code === 'CURRENT_PASSWORD_INCORRECT') {
        setError('currentPassword', { type: 'server', message: err.message });
      } else if (err.code === 'PASSWORD_UNCHANGED') {
        setError('newPassword', { type: 'server', message: err.message });
      } else if (!applyServerFieldErrors(err, setError)) {
        toast.error(err.message);
      }
    }
  }

  return (
    <Card title="Change password">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2 sm:max-w-sm">
          <TextField
            label="Current password" type="password" required autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register('currentPassword', { required: 'Current password is required' })}
          />
        </div>
        <div>
          <TextField
            label="New password" type="password" required autoComplete="new-password" error={errors.newPassword?.message}
            {...register('newPassword', { required: 'New password is required', validate: isStrongPassword })}
          />
          <PasswordChecklist value={newPassword} />
        </div>
        <TextField
          label="Confirm new password" type="password" required autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword', {
            required: 'Please confirm the new password',
            validate: (v) => v === newPassword || 'Passwords do not match',
          })}
        />
        <div className="sm:col-span-2">
          <Button type="submit" loading={isSubmitting}>Change password</Button>
        </div>
      </form>
    </Card>
  );
}

export default function AccountSettingsPage() {
  return (
    <>
      <PageHeader title="Account settings" description="Manage your contact details, notifications and password." />
      <div className="space-y-6">
        <AccountForm />
        <NotificationPreferencesCard />
        <PasswordForm />
      </div>
    </>
  );
}
