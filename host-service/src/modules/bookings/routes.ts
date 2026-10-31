import type { FastifyInstance } from 'fastify';
import type { ZodTypeProvider } from 'fastify-type-provider-zod';
import { hostParams } from '../availability/schema';
import { bookingParams, cancelBody, listBookingsQuery } from './schema';
import { cancelBooking, listBookings } from './service';

export async function bookingRoutes(app: FastifyInstance) {
  const r = app.withTypeProvider<ZodTypeProvider>();

  r.get('/bookings', { schema: { params: hostParams, querystring: listBookingsQuery } }, async (req) =>
    listBookings(req.params.hostId, req.query),
  );

  r.delete('/bookings/:bookingId', { schema: { params: bookingParams, body: cancelBody } }, async (req) =>
    cancelBooking(req.params.hostId, req.params.bookingId, req.body?.reason),
  );
}
