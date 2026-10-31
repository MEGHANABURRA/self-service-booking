import Fastify from 'fastify';
import { validatorCompiler, serializerCompiler } from 'fastify-type-provider-zod';
import { AppError } from './errors';
import { requireOwnHost } from './plugins/identity';
import { availabilityRoutes } from './modules/availability/routes';
import { bookingRoutes } from './modules/bookings/routes';

export function buildApp() {
  const app = Fastify({ logger: process.env.NODE_ENV !== 'test' });
  app.setValidatorCompiler(validatorCompiler);
  app.setSerializerCompiler(serializerCompiler);

  app.setErrorHandler((err: any, _req, reply) => {
    if (err instanceof AppError) {
      return reply.code(err.statusCode).send({ error: { code: err.code, message: err.message } });
    }
    if (err.validation) {
      return reply.code(400).send({ error: { code: 'VALIDATION_ERROR', message: err.message, details: err.validation } });
    }
    app.log.error(err);
    return reply.code(500).send({ error: { code: 'INTERNAL', message: 'Internal server error' } });
  });

  app.get('/health', async () => ({ status: 'ok' }));

  app.register(
    async (scope) => {
      scope.addHook('preHandler', requireOwnHost as any);
      await scope.register(availabilityRoutes);
      await scope.register(bookingRoutes);
    },
    { prefix: '/api/v1/hosts/:hostId' },
  );

  return app;
}
