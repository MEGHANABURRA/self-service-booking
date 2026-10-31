import { describe, expect, it } from 'vitest';
import { buildApp } from '../src/app';

const HOST = '11111111-1111-4111-8111-111111111111';
const url = `/api/v1/hosts/${HOST}/bookings`;

describe('identity guard', () => {
  it('rejects non-HOST roles', async () => {
    const res = await buildApp().inject({ method: 'GET', url, headers: { 'x-user-id': HOST, 'x-user-role': 'CANDIDATE' } });
    expect(res.statusCode).toBe(403);
  });

  it('rejects a host acting on someone else', async () => {
    const res = await buildApp().inject({
      method: 'GET', url, headers: { 'x-user-id': '22222222-2222-4222-8222-222222222222', 'x-user-role': 'HOST' },
    });
    expect(res.statusCode).toBe(403);
  });

  it('rejects missing headers', async () => {
    expect((await buildApp().inject({ method: 'GET', url })).statusCode).toBe(403);
  });

  it('validates bodies (bad slot duration -> 400)', async () => {
    const res = await buildApp().inject({
      method: 'POST', url: `/api/v1/hosts/${HOST}/availability`,
      headers: { 'x-user-id': HOST, 'x-user-role': 'HOST' },
      payload: { days: [{ day: 'MON', startTime: '10:00', endTime: '18:00' }], slotDurationMins: 20, maxBookingsPerDay: 3 },
    });
    expect(res.statusCode).toBe(400);
  });
});

describe('body validation', () => {
  const post = (payload: unknown) =>
    buildApp().inject({
      method: 'POST', url: `/api/v1/hosts/${HOST}/availability`,
      headers: { 'x-user-id': HOST, 'x-user-role': 'HOST' }, payload: payload as object,
    });
  const ok = { slotDurationMins: 30, maxBookingsPerDay: 3 };

  it('rejects a day whose start is not before its end', async () => {
    expect((await post({ ...ok, days: [{ day: 'MON', startTime: '18:00', endTime: '10:00' }] })).statusCode).toBe(400);
  });
  it('rejects duplicate days', async () => {
    const d = { day: 'MON', startTime: '10:00', endTime: '12:00' };
    expect((await post({ ...ok, days: [d, d] })).statusCode).toBe(400);
  });
  it('rejects unknown day codes', async () => {
    expect((await post({ ...ok, days: [{ day: 'MONDAY', startTime: '10:00', endTime: '12:00' }] })).statusCode).toBe(400);
  });
});
