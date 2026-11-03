import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AppRoutes from './AppRoutes.jsx';

vi.mock('../services/systemService.js', () => ({
  getHealth: vi.fn().mockResolvedValue(null),
  getHospitalInfo: vi.fn().mockResolvedValue({
    hospitalName: 'Test Hospital',
    departments: [{ id: 1, name: 'Cardiology', description: 'Heart care' }],
  }),
}));

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

describe('AppRoutes', () => {
  it('renders the 404 page for unknown routes', () => {
    renderAt('/does-not-exist');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('renders the 403 page', () => {
    renderAt('/unauthorized');
    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
  });

  it('lists departments from the API', async () => {
    renderAt('/departments');
    expect(await screen.findByRole('heading', { name: 'Cardiology' })).toBeInTheDocument();
  });
});
