import { afterEach, describe, expect, it } from 'vitest';
import { formatDateTime } from './format.js';
import { hasStarted, hospitalNow, hospitalTimeZone, setHospitalTimeZone } from './hospitalTime.js';

const original = hospitalTimeZone();
afterEach(() => setHospitalTimeZone(original));

describe('hospital time', () => {
  it('uses the hospital zone for "now", whatever the device zone', () => {
    setHospitalTimeZone('Asia/Kolkata');
    // 2026-11-11 20:00 UTC is already 12 Nov 01:30 in India
    expect(hospitalNow(new Date('2026-11-11T20:00:00Z'))).toEqual({ date: '2026-11-12', time: '01:30' });
    setHospitalTimeZone('America/New_York');
    expect(hospitalNow(new Date('2026-11-11T20:00:00Z'))).toEqual({ date: '2026-11-11', time: '15:00' });
  });

  it('decides whether an appointment has started in hospital time', () => {
    setHospitalTimeZone('Asia/Kolkata');
    const now = new Date('2026-11-11T04:00:00Z'); // 09:30 IST
    expect(hasStarted('2026-11-11', '09:15', now)).toBe(true);
    expect(hasStarted('2026-11-11', '09:45:00', now)).toBe(false);
    expect(hasStarted('2026-11-10', '23:00', now)).toBe(true);
  });

  it('formats timestamps in the hospital zone and ignores invalid zones', () => {
    setHospitalTimeZone('Asia/Kolkata');
    expect(formatDateTime('2026-11-11T04:00:00Z')).toMatch(/9:30/);
    expect(setHospitalTimeZone('Not/AZone')).toBe(false);
    expect(hospitalTimeZone()).toBe('Asia/Kolkata');
  });
});
