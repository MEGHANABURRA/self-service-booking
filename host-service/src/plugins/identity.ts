import type { FastifyReply, FastifyRequest } from 'fastify';
import { forbidden } from '../errors';

/**
 * The upstream interceptor has already authenticated the caller and only lets HOST users through.
 * It forwards x-user-id / x-user-role. This is a cheap defence-in-depth guard:
 * the caller must be a HOST acting on their own :hostId. Remove it if you want zero checks here.
 */
export async function requireOwnHost(
  req: FastifyRequest<{ Params: { hostId: string } }>,
  _reply: FastifyReply,
) {
  const userId = req.headers['x-user-id'];
  const role = String(req.headers['x-user-role'] ?? '').toUpperCase();
  if (role !== 'HOST') throw forbidden('Only hosts can use this service');
  if (!userId || userId !== req.params.hostId) throw forbidden('hostId does not match the calling user');
}
