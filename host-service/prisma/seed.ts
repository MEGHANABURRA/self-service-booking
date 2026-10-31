import { PrismaClient } from '@prisma/client';
import { DateTime } from 'luxon';

const prisma = new PrismaClient();

// Fixed ids so the Postman environment works out of the box.
export const IDS = {
  hostAsha: '11111111-1111-4111-8111-111111111111',
  hostBen: '22222222-2222-4222-8222-222222222222',
  candA: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  candB: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  candC: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  admin: '99999999-9999-4999-8999-999999999999',
  bookingActive: 'd0000000-0000-4000-8000-000000000001',
  bookingActive2: 'd0000000-0000-4000-8000-000000000002',
  bookingCancelled: 'd0000000-0000-4000-8000-000000000003',
};

const DUMMY_HASH = '$2b$10$dummydummydummydummydummydummydummydummydummydummydummy'; // not a real hash

async function main() {
  const users = [
    { userId: IDS.hostAsha, name: 'Asha Rao', userName: 'asha.host', timeZone: 'Asia/Kolkata', userRole: 'HOST' as const },
    { userId: IDS.hostBen, name: 'Ben Carter', userName: 'ben.host', timeZone: 'America/New_York', userRole: 'HOST' as const },
    { userId: IDS.candA, name: 'Chitra Nair', userName: 'chitra', timeZone: 'Asia/Kolkata', userRole: 'CANDIDATE' as const },
    { userId: IDS.candB, name: 'Dev Patel', userName: 'dev', timeZone: 'Asia/Kolkata', userRole: 'CANDIDATE' as const },
    { userId: IDS.candC, name: 'Eve Miller', userName: 'eve', timeZone: 'America/New_York', userRole: 'CANDIDATE' as const },
    { userId: IDS.admin, name: 'Root Admin', userName: 'admin', timeZone: 'UTC', userRole: 'ADMIN' as const },
  ];
  for (const u of users) {
    await prisma.user.upsert({ where: { userId: u.userId }, update: {}, create: { ...u, password: DUMMY_HASH } });
  }

  // All times are UTC. Asha has a different window per weekday (30 min slots, 15 min grace, max 6/day).
  const asha = [
    { day: 'MON', startTime: '10:00', endTime: '18:00' },
    { day: 'TUE', startTime: '09:00', endTime: '13:00' },
    { day: 'WED', startTime: '12:00', endTime: '20:00' },
    { day: 'THU', startTime: '08:30', endTime: '14:30' },
    { day: 'FRI', startTime: '10:00', endTime: '15:00' },
  ] as const;
  for (const d of asha) {
    await prisma.hostPrefDetails.upsert({
      where: { hostId_day: { hostId: IDS.hostAsha, day: d.day } },
      update: {},
      create: { hostId: IDS.hostAsha, ...d, slotDurationMins: 30, gracePeriod: 15, maxBookings: 6 },
    });
  }
  // Ben: Tue/Thu, 60 min slots, 15 min grace, max 3/day.
  for (const d of [{ day: 'TUE', startTime: '14:00', endTime: '18:00' }, { day: 'THU', startTime: '15:00', endTime: '19:00' }] as const) {
    await prisma.hostPrefDetails.upsert({
      where: { hostId_day: { hostId: IDS.hostBen, day: d.day } },
      update: {},
      create: { hostId: IDS.hostBen, ...d, slotDurationMins: 60, gracePeriod: 15, maxBookings: 3 },
    });
  }

  // Bookings for Asha on the next Monday (UTC): 10:00-10:30, 11:30-12:00 booked, 10:45-11:15 cancelled.
  const monday = DateTime.utc().plus({ days: 1 }).startOf('day');
  let nextMon = monday;
  while (nextMon.weekday !== 1) nextMon = nextMon.plus({ days: 1 });
  const at = (h: number, m: number) => nextMon.set({ hour: h, minute: m }).toJSDate();
  const bookings = [
    { bookingId: IDS.bookingActive, slotStartTime: at(10, 0), slotEndTime: at(10, 30), candidateId: IDS.candA, status: 'BOOKED' as const },
    { bookingId: IDS.bookingActive2, slotStartTime: at(11, 30), slotEndTime: at(12, 0), candidateId: IDS.candB, status: 'BOOKED' as const },
    { bookingId: IDS.bookingCancelled, slotStartTime: at(10, 45), slotEndTime: at(11, 15), candidateId: IDS.candA, status: 'CANCELLED' as const },
  ];
  for (const b of bookings) {
    await prisma.booking.upsert({ where: { bookingId: b.bookingId }, update: {}, create: { ...b, hostId: IDS.hostAsha } });
  }
  console.log(`Seeded. Asha's bookings are on ${nextMon.toISODate()} (host id ${IDS.hostAsha})`);
}

main().finally(() => prisma.$disconnect());
