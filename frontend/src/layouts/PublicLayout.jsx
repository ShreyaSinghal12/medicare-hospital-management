import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { Menu, Plus, X } from 'lucide-react';
import Button from '../components/ui/Button.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { homeFor } from '../utils/roles.js';

const NAV = [
  { to: '/', label: 'Home', end: true },
  { to: '/about', label: 'About' },
  { to: '/departments', label: 'Departments' },
  { to: '/status', label: 'System status' },
];

function navClass({ isActive }) {
  return `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
  }`;
}

export function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2" aria-label="MediCare home">
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white">
        <Plus className="h-5 w-5" strokeWidth={3} aria-hidden="true" />
      </span>
      <span className="text-lg font-bold tracking-tight text-slate-900">
        Medi<span className="text-brand-600">Care</span>
      </span>
    </Link>
  );
}

function AuthButtons({ onNavigate }) {
  const { user, initializing } = useAuth();
  if (initializing) return null;
  if (user) {
    return <Button to={homeFor(user)} size="sm" onClick={onNavigate}>Go to dashboard</Button>;
  }
  return (
    <>
      <Button to="/login" variant="secondary" size="sm" onClick={onNavigate}>Log in</Button>
      <Button to="/register" size="sm" onClick={onNavigate}>Register</Button>
    </>
  );
}

export default function PublicLayout() {
  const [open, setOpen] = useState(false);

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <Logo />
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                {item.label}
              </NavLink>
            ))}
            <div className="ml-3 flex items-center gap-2 border-l border-slate-200 pl-4">
              <AuthButtons />
            </div>
          </nav>
          <button
            type="button"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 md:hidden"
            aria-label={open ? 'Close menu' : 'Open menu'}
            aria-expanded={open}
            aria-controls="mobile-nav"
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>
        {open && (
          <nav id="mobile-nav" className="border-t border-slate-100 px-4 py-2 md:hidden" aria-label="Mobile">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={(s) => `block ${navClass(s)}`}
                onClick={() => setOpen(false)}
              >
                {item.label}
              </NavLink>
            ))}
            <div className="mt-2 flex gap-2 border-t border-slate-100 pt-3">
              <AuthButtons onNavigate={() => setOpen(false)} />
            </div>
          </nav>
        )}
      </header>

      <main id="main" className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm text-slate-500">
          <p>© {new Date().getFullYear()} MediCare — academic prototype. All patient data in this system is fictional.</p>
          <p className="mt-1">Not a certified medical device. Clinical decisions are made only by authorised doctors.</p>
        </div>
      </footer>
    </div>
  );
}
