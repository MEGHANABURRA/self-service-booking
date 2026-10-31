import type { BookingStatus } from '@prisma/client';
import { prisma } from '../../plugins/prisma';
import { conflict, notFound } from '../../errors';

export async function listBookings(
  hostId: string,
  q: { status?: BookingStatus; from?: string; to?: string; page: number; pageSize: number },
) {
  const where = {
    hostId,
    ...(q.status ? { status: q.status } : {}),
    ...(q.from || q.to
      ? { slotStartTime: { ...(q.from ? { gte: new Date(q.from) } : {}), ...(q.to ? { lt: new Date(q.to) } : {}) } }
      : {}),
  };
  const [total, rows] = await Promise.all([
    prisma.booking.count({ where }),
    prisma.booking.findMany({
      where,
      orderBy: { slotStartTime: 'asc' },
      skip: (q.page - 1) * q.pageSize,
      take: q.pageSize,
      include: { candidate: { select: { userId: true, name: true, userName: true } } },
    }),
  ]);
  return {
    page: q.page,
    pageSize: q.pageSize,
    total,
    bookings: rows.map((b) => ({
      bookingId: b.bookingId,
      slotStartTime: b.slotStartTime,
      slotEndTime: b.slotEndTime,
      status: b.status,
      cancelledAt: b.cancelledAt,
      cancelReason: b.cancelReason,
      candidate: b.candidate,
    })),
  };
}

export async function cancelBooking(hostId: string, bookingId: string, reason?: string) {
  const booking = await prisma.booking.findFirst({ where: { bookingId, hostId } });
  if (!booking) throw notFound('Booking not found');
  if (booking.status !== 'BOOKED') throw conflict(`Booking is already ${booking.status}`);

  // guarded update so two concurrent cancels can't both succeed
  const res = await prisma.booking.updateMany({
    where: { bookingId, hostId, status: 'BOOKED' },
    data: { status: 'CANCELLED', cancelledAt: new Date(), cancelReason: reason ?? null },
  });
  if (res.count === 0) throw conflict('Booking was changed concurrently');
  return prisma.booking.findUniqueOrThrow({ where: { bookingId } });
}
