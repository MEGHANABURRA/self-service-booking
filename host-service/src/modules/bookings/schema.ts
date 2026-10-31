import { z } from 'zod';

export const bookingParams = z.object({ hostId: z.string().uuid(), bookingId: z.string().uuid() });

export const cancelBody = z.object({ reason: z.string().max(500).optional() }).optional();

export const listBookingsQuery = z.object({
  status: z.enum(['BOOKED', 'CANCELLED', 'COMPLETED']).optional(),
  from: z.string().datetime({ offset: true }).optional(),
  to: z.string().datetime({ offset: true }).optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});
