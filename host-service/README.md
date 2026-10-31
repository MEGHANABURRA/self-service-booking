# host-service

Hosts configure availability, see open slots and meetings, and cancel bookings. Auth is done by an upstream interceptor which forwards `x-user-id` / `x-user-role`.

## Run
```bash
cp .env.example .env
docker compose up -d                 # Postgres on :5433
npx prisma migrate dev --name init   # create tables
npx prisma db seed                   # dummy users / prefs / bookings
npm run dev                          # http://localhost:3000
npm test
```
Using the real DB instead: set `DATABASE_URL` and either `prisma migrate` or `prisma db pull` (table/column names are mapped with `@@map`/`@map`). `host_pref_details` needs the extra columns `start_time, end_time, slot_duration_mins` (and `id`).

## API (all under `/api/v1/hosts/:hostId`, headers `x-user-id`, `x-user-role: HOST`)
| Method | Path | Purpose |
|---|---|---|
| POST | `/availability[?replace=true]` | weekly schedule: `days: [{day:'MON', startTime:'10:00', endTime:'18:00'}, ...]` (UTC, own window per day) + shared `slotDurationMins` (30/45/60), `gracePeriodMins`, `maxBookingsPerDay` |
| GET | `/availability?date=` or `?from=&to=` | open slots for UTC dates (also shows host-local times) |
| GET | `/bookings?status=&from=&to=&page=&pageSize=` | meetings with candidate info |
| DELETE | `/bookings/:bookingId` | host cancels a meeting; slot re-opens |

Import `postman/host-service.postman_collection.json` and the `.local` environment.

All schedule times are **UTC** and repeat every week on the chosen weekdays. A window can't cross midnight UTC (start must be before end).
