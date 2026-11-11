import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import PageHeader from '../components/ui/PageHeader.jsx';
import DashboardLayout from './DashboardLayout.jsx';
import { navigationItems } from './navigation.js';

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: () => ({ user: { id: 1, fullName: 'Asha Admin', role: 'ADMIN' }, logout: vi.fn() }),
}));
vi.mock('../components/NotificationBell.jsx', () => ({ default: () => null }));

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<DashboardLayout />}>
          <Route path="/admin" element={<PageHeader title="Administration" />} />
          <Route path="/admin/audit" element={<PageHeader title="Audit log" />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('DashboardLayout navigation', () => {
  it('groups links into sections; the section with the current page cannot be collapsed', async () => {
    renderAt('/admin');
    const nav = screen.getByRole('navigation', { name: 'Dashboard' });
    const people = within(nav).getByRole('button', { name: 'People' });
    expect(people).toHaveAttribute('aria-expanded', 'true');
    await userEvent.click(people);
    expect(people).toHaveAttribute('aria-expanded', 'false');
    expect(within(nav).queryByRole('link', { name: 'Staff accounts' })).not.toBeInTheDocument();

    await userEvent.click(within(nav).getByRole('link', { name: 'Audit log' }));
    expect(await screen.findByRole('heading', { name: 'Audit log', level: 1 })).toHaveFocus();
    expect(document.title).toBe('Audit log · MediCare');
    const system = within(nav).getByRole('button', { name: 'System' });
    await userEvent.click(system);
    expect(system).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText(/Dates and times are shown in hospital time/)).toBeInTheDocument();
  });

  it('lists every admin page once', () => {
    const links = navigationItems('ADMIN').map((i) => i.to);
    expect(new Set(links).size).toBe(links.length);
    expect(links).toContain('/admin/settings');
  });
});
