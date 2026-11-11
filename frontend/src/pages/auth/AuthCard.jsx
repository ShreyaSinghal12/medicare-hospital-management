import PageHeading from '../../components/ui/PageHeading.jsx';
/** Centered card used by every authentication page. */
export default function AuthCard({ title, subtitle, children, footer, wide = false }) {
  return (
    <div className="flex justify-center px-4 py-12 sm:py-16">
      <div className={`w-full ${wide ? 'max-w-2xl' : 'max-w-md'}`}>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <PageHeading title={title} className="text-2xl font-bold tracking-tight text-slate-900">{title}</PageHeading>
          {subtitle && <p className="mt-1 text-sm text-slate-600">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>
        {footer && <div className="mt-4 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </div>
  );
}
