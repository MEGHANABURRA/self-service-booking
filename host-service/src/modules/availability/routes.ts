import type { FastifyInstance } from 'fastify';
import type { ZodTypeProvider } from 'fastify-type-provider-zod';
import { getAvailabilityQuery, hostParams, setAvailabilityBody, setAvailabilityQuery } from './schema';
import { getAvailability, setAvailability } from './service';

export async function availabilityRoutes(app: FastifyInstance) {
  const r = app.withTypeProvider<ZodTypeProvider>();

  r.post(
    '/availability',
    { schema: { params: hostParams, body: setAvailabilityBody, querystring: setAvailabilityQuery } },
    async (req, reply) => {
      const result = await setAvailability(req.params.hostId, req.body, req.query.replace === 'true');
      return reply.code(201).send(result);
    },
  );

  r.get('/availability', { schema: { params: hostParams, querystring: getAvailabilityQuery } }, async (req) =>
    getAvailability(req.params.hostId, req.query),
  );
}
