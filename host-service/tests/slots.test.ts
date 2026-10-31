import { describe, expect, it } from 'vitest';
import { dayCodeOf, generateSlots, gridForDay } from '../src/modules/availability/slots';

const prefs = { startTime: '10:00', endTime: '18:00', slotDurationMins: 30, gracePeriod: 15, maxBookings: 6 };
const DATE = '2030-01-07'; // a Monday (UTC)
const past = new Date('2020-01-01T00:00:00Z');
const utc = (h: number, m = 0) => new Date(`${DATE}T${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00Z`);
const hhmm = (iso: string) => iso.slice(11, 16);

describe('slot grid (UTC)', () => {
  it('steps by duration + grace and never overruns the window', () => {
    const g = gridForDay(DATE, prefs);
    expect(g[0].start.toFormat('HH:mm')).toBe('10:00');
    expect(g[1].start.toFormat('HH:mm')).toBe('10:45');
    expect(g[2].start.toFormat('HH:mm')).toBe('11:30');
    expect(g).toHaveLength(11); // last slot 17:30-18:00
    expect(g.at(-1)!.end.toFormat('HH:mm')).toBe('18:00');
  });

  it('cuts off a slot that would end after endTime', () => {
    expect(gridForDay(DATE, { ...prefs, endTime: '10:50' })).toHaveLength(1);
  });

  it('supports a different window per day', () => {
    const mon = gridForDay('2030-01-07', { ...prefs, startTime: '10:00', endTime: '12:00' });
    const tue = gridForDay('2030-01-08', { ...prefs, startTime: '14:00', endTime: '17:00', slotDurationMins: 60 });
    expect(mon[0].start.toFormat('HH:mm')).toBe('10:00');
    expect(tue[0].start.toFormat('HH:mm')).toBe('14:00');
    expect(tue.map((x) => x.start.toFormat('HH:mm'))).toEqual(['14:00', '15:15']); // 16:30 would end 17:30 > 17:00
  });

  it('maps dates to weekday codes', () => {
    expect(dayCodeOf('2030-01-07')).toBe('MON');
    expect(dayCodeOf('2030-01-13')).toBe('SUN');
  });
});

describe('generateSlots', () => {
  it('returns every grid slot when nothing is booked', () => {
    expect(generateSlots(DATE, prefs, [], past)).toHaveLength(11);
  });

  it('removes a booked slot', () => {
    const slots = generateSlots(DATE, prefs, [{ start: utc(10), end: utc(10, 30) }], past);
    expect(slots.map((s) => hhmm(s.start))).not.toContain('10:00');
    expect(slots).toHaveLength(10);
  });

  it('enforces the grace gap around an off-grid booking', () => {
    const slots = generateSlots(DATE, prefs, [{ start: utc(10, 50), end: utc(11, 20) }], past);
    const starts = slots.map((s) => hhmm(s.start));
    expect(starts).not.toContain('10:45');
    expect(starts).not.toContain('11:30');
    expect(starts).toContain('12:15');
  });

  it('returns nothing once max_bookings is reached', () => {
    const booked = [utc(10), utc(10, 45)].map((s) => ({ start: s, end: new Date(s.getTime() + 30 * 60_000) }));
    expect(generateSlots(DATE, { ...prefs, maxBookings: 2 }, booked, past)).toEqual([]);
  });

  it('drops slots that have already started', () => {
    const now = utc(12);
    const slots = generateSlots(DATE, prefs, [], now);
    expect(slots.every((s) => new Date(s.start) > now)).toBe(true);
  });

  it('keeps times in UTC and only renders local fields in the display timezone', () => {
    const [first] = generateSlots(DATE, prefs, [], past, 'Asia/Kolkata');
    expect(first.start).toBe('2030-01-07T10:00:00.000Z');
    expect(first.localStart).toContain('15:30:00.000+05:30');
  });
});
