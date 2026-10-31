import { DateTime } from 'luxon';
import { prisma } from '../../plugins/prisma';
import { badRequest, notFound } from '../../errors';
import { dayCodeOf, generateSlots, gridForDay } from './slots';
import type { SetAvailabilityBody } from './schema';

async function getHost(hostId: string) {
  const host = await prisma.user.findFirst({ where: { userId: hostId, userRole: 'HOST', deleted: false } });
  if (!host) throw notFound('Host not found');
  return host;
}

export async function setAvailability(hostId: string, body: SetAvailabilityBody, replace: boolean) {
  const host = await getHost(hostId);

  // sanity check per day: the window must fit at least one slot and the cap can't exceed what fits
  const slotsPerDay: Record<string, number> = {};
  for (const d of body.days) {
    const fit = gridForDay('2030-01-07', {
      startTime: d.startTime,
      endTime: d.endTime,
      slotDurationMins: body.slotDurationMins,
      gracePeriod: body.gracePeriodMins,
      maxBookings: body.maxBookingsPerDay,
    }).length;
    if (fit === 0) throw badRequest(`${d.day}: window is too short for even one slot`);
    if (body.maxBookingsPerDay > fit) {
      throw badRequest(`${d.day}: maxBookingsPerDay (${body.maxBookingsPerDay}) exceeds the ${fit} slots that fit in the window`);
    }
    slotsPerDay[d.day] = fit;
  }

  const shared = {
    slotDurationMins: body.slotDurationMins,
    gracePeriod: body.gracePeriodMins,
    maxBookings: body.maxBookingsPerDay,
    deleted: false,
  };
  const dayNames = body.days.map((d) => d.day);

  await prisma.$transaction([
    ...body.days.map((d) => {
      const data = { ...shared, startTime: d.startTime, endTime: d.endTime };
      return prisma.hostPrefDetails.upsert({
        where: { hostId_day: { hostId, day: d.day } },
        create: { hostId, day: d.day, ...data },
        update: data,
      });
    }),
    ...(replace
      ? [prisma.hostPrefDetails.updateMany({ where: { hostId, day: { notIn: dayNames } }, data: { deleted: true } })]
      : []),
  ]);

  const prefs = await prisma.hostPrefDetails.findMany({ where: { hostId, deleted: false }, orderBy: { day: 'asc' } });
  return { hostId, timesAreUtc: true, preferences: prefs, slotsPerDay };
}

export async function getAvailability(hostId: string, q: { date?: string; from?: string; to?: string }) {
  const host = await getHost(hostId);
  const from = q.date ?? q.from!;
  const to = q.date ?? q.to!;

  const start = DateTime.fromISO(from, { zone: 'utc' }).startOf('day');
  const end = DateTime.fromISO(to, { zone: 'utc' }).startOf('day');
  if (!start.isValid || !end.isValid) throw badRequest('Invalid date');
  if (end < start) throw badRequest('to must not be before from');
  if (end.diff(start, 'days').days > 13) throw badRequest('Range is limited to 14 days');

  const [prefs, bookings] = await Promise.all([
    prisma.hostPrefDetails.findMany({ where: { hostId, deleted: false } }),
    prisma.booking.findMany({
      where: {
        hostId,
        status: 'BOOKED',
        // pad by a day each side so grace-period neighbours across midnight are seen
        slotStartTime: { gte: start.minus({ days: 1 }).toJSDate(), lt: end.plus({ days: 2 }).toJSDate() },
      },
      select: { slotStartTime: true, slotEndTime: true },
    }),
  ]);
  const busy = bookings.map((b) => ({ start: b.slotStartTime, end: b.slotEndTime }));

  const days: { date: string; day: string; slots: ReturnType<typeof generateSlots> }[] = [];
  for (let d = start; d <= end; d = d.plus({ days: 1 })) {
    const date = d.toISODate()!;
    const day = dayCodeOf(date);
    const pref = prefs.find((p) => p.day === day);
    days.push({
      date,
      day,
      slots: pref
        ? generateSlots(date, {
            startTime: pref.startTime,
            endTime: pref.endTime,
            slotDurationMins: pref.slotDurationMins,
            gracePeriod: pref.gracePeriod,
            maxBookings: pref.maxBookings,
          }, busy, new Date(), host.timeZone)
        : [],
    });
  }
  return { hostId, dateIsUtc: true, displayTimeZone: host.timeZone, days };
}
