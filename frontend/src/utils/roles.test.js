import { describe, expect, it } from 'vitest';
import { ALL_ROLES, ROLE_HOME, homeFor } from './roles.js';

describe('roles', () => {
  it('defines exactly the four supported roles', () => {
    expect(ALL_ROLES).toEqual(['PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN']);
    expect(Object.keys(ROLE_HOME)).toHaveLength(4);
  });

  it('falls back to the home page for an unknown role', () => {
    expect(homeFor({ role: 'SOMETHING_ELSE' })).toBe('/');
  });
});
