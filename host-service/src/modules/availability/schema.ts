import { z } from 'zod';

const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Expected HH:mm (24h)');
const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Expected YYYY-MM-DD');

export const hostParams = z.object({ hostId: z.string().uuid() });

const dayCode = z.enum(['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']);

// Each selected weekday has its own UTC window. Windows can't cross midnight UTC (start < end).
const daySchedule = z
  .object({ day: dayCode, startTime: hhmm, endTime: hhmm })
  .refine((d) => d.startTime < d.endTime, { message: 'startTime must be before endTime', path: ['endTime'] });

export const setAvailabilityBody = z
  .object({
    days: z.array(daySchedule).min(1).max(7),
    slotDurationMins: z.union([z.literal(30), z.literal(45), z.literal(60)]),
    gracePeriodMins: z.number().int().min(0).max(120).default(15),
    maxBookingsPerDay: z.number().int().min(1).max(50),
  })
  .refine((b) => new Set(b.days.map((d) => d.day)).size === b.days.length, {
    message: 'Each day may appear only once',
    path: ['days'],
  });

export const setAvailabilityQuery = z.object({
  // when true, weekdays not listed in `days` are soft-deleted
  replace: z.enum(['true', 'false']).default('false'),
});

export const getAvailabilityQuery = z
  .object({ date: isoDate.optional(), from: isoDate.optional(), to: isoDate.optional() })
  .refine((q) => q.date || (q.from && q.to), { message: 'Provide either date or both from and to' });

export type SetAvailabilityBody = z.infer<typeof setAvailabilityBody>;
