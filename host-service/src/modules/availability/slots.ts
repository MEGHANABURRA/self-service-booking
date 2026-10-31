import { DateTime } from 'luxon';

export type DayCode = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';
export const DAY_CODES: DayCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

export interface SlotPrefs {
  startTime: string; // "HH:mm" in UTC
  endTime: string;
  slotDurationMins: number;
  gracePeriod: number; // minutes required between two meetings
  maxBookings: number; // per day
}

export interface BusyRange {
  start: Date;
  end: Date;
}

export interface Slot {
  start: string; // UTC ISO
  end: string;
  localStart: string; // host-local ISO with offset
  localEnd: string;
}

/** Weekday code for a YYYY-MM-DD calendar date (timezone independent). */
export function dayCodeOf(date: string): DayCode {
  const dt = DateTime.fromISO(date, { zone: 'utc' });
  return DAY_CODES[dt.weekday - 1];
}

/**
 * All slots a day's config could ever offer (ignores bookings and "now").
 * Slots start at startTime and advance by duration + grace; each must end <= endTime.
 */
export function gridForDay(date: string, prefs: SlotPrefs): { start: DateTime; end: DateTime }[] {
  const dayStart = DateTime.fromISO(`${date}T${prefs.startTime}`, { zone: 'utc' });
  const dayEnd = DateTime.fromISO(`${date}T${prefs.endTime}`, { zone: 'utc' });
  if (!dayStart.isValid || !dayEnd.isValid) return [];

  const out: { start: DateTime; end: DateTime }[] = [];
  let cursor = dayStart;
  while (true) {
    const end = cursor.plus({ minutes: prefs.slotDurationMins });
    if (end > dayEnd) break;
    out.push({ start: cursor, end });
    cursor = cursor.plus({ minutes: prefs.slotDurationMins + prefs.gracePeriod });
  }
  return out;
}

/**
 * Open slots for a UTC calendar date (weekday rules are defined in UTC).
 * `displayTz` only affects the localStart/localEnd fields.
 *  - drops slots that overlap an existing booking, extended by the grace period on both sides
 *  - returns nothing once the day's max_bookings is reached
 *  - drops slots that already started (relative to `now`)
 */
export function generateSlots(
  date: string,
  prefs: SlotPrefs,
  booked: BusyRange[],
  now: Date = new Date(),
  displayTz = 'UTC',
): Slot[] {
  const dayBookings = booked.filter(
    (b) => DateTime.fromJSDate(b.start, { zone: 'utc' }).toISODate() === date,
  );
  if (dayBookings.length >= prefs.maxBookings) return [];

  const graceMs = prefs.gracePeriod * 60_000;
  return gridForDay(date, prefs)
    .filter(({ start }) => start.toMillis() > now.getTime())
    .filter(({ start, end }) =>
      booked.every(
        (b) => !(start.toMillis() < b.end.getTime() + graceMs && end.toMillis() + graceMs > b.start.getTime()),
      ),
    )
    .map(({ start, end }) => ({
      start: start.toUTC().toISO()!,
      end: end.toUTC().toISO()!,
      localStart: start.setZone(displayTz).toISO()!,
      localEnd: end.setZone(displayTz).toISO()!,
    }));
}
