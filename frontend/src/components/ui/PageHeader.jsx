import PageHeading from './PageHeading.jsx';

/** Page title block: heading (document title + focus after navigation), description and actions. */
export default function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <PageHeading title={typeof title === 'string' ? title : undefined} className="text-2xl font-bold tracking-tight text-slate-900">
          {title}
        </PageHeading>
        {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
