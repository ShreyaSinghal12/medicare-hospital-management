import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SystemStatusCard from './SystemStatusCard.jsx';
import { getHealth } from '../services/systemService.js';

vi.mock('../services/systemService.js', () => ({ getHealth: vi.fn() }));

const healthy = {
  status: 'UP',
  application: 'medicare-backend',
  version: '1.0.0',
  javaVersion: '21.0.12',
  serverTime: '2026-11-03T10:00:00Z',
  database: { status: 'UP', product: 'MySQL', productVersion: '8.0.46', schemaVersion: '8', tableCount: 38 },
};

function renderCard() {
  return render(
    <MemoryRouter>
      <SystemStatusCard />
    </MemoryRouter>,
  );
}

describe('SystemStatusCard', () => {
  beforeEach(() => vi.mocked(getHealth).mockReset());

  it('shows backend and database details when healthy', async () => {
    vi.mocked(getHealth).mockResolvedValue(healthy);
    renderCard();
    expect(await screen.findByText('All systems operational.')).toBeInTheDocument();
    expect(screen.getByText('MySQL 8.0.46')).toBeInTheDocument();
    expect(screen.getByText('V8')).toBeInTheDocument();
  });

  it('shows DOWN when the database is unavailable', async () => {
    vi.mocked(getHealth).mockResolvedValue({
      ...healthy,
      status: 'DEGRADED',
      database: { status: 'DOWN', error: 'Database is unreachable' },
    });
    renderCard();
    expect(await screen.findByText('DOWN')).toBeInTheDocument();
    expect(screen.getByText('Database is unreachable')).toBeInTheDocument();
    expect(screen.queryByText('All systems operational.')).not.toBeInTheDocument();
  });

  it('shows a network error and recovers after retry', async () => {
    vi.mocked(getHealth)
      .mockRejectedValueOnce({ code: 'NETWORK_ERROR', message: 'Cannot reach the MediCare server.' })
      .mockResolvedValueOnce(healthy);
    renderCard();
    expect(await screen.findByText('Backend unreachable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('All systems operational.')).toBeInTheDocument();
    expect(getHealth).toHaveBeenCalledTimes(2);
  });
});
