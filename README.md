This is a self booking service which automates the process of slot scheduling between candidate and interviewer.

## Booking email notifications

The notification consumer listens for JSON booking events on Kafka and sends an HTML email to both the host and
candidate when a booking is confirmed, rescheduled, or canceled. Each recipient gets a role-specific template.
Slot times are rendered in each recipient's timezone.
Failed deliveries are retried three times with a one-second interval; messages that still fail are published to the
source topic's `.DLT` topic. Email sending is at-least-once, so duplicates are possible if delivery succeeds but Kafka
cannot acknowledge the message. Create the `.DLT` topic with at least as many partitions as the source topic.

Configure the Kafka broker with `KAFKA_BOOTSTRAP_SERVERS` and the SMTP server with `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, and `SMTP_PASSWORD`. `BOOKING_NOTIFICATIONS_TOPIC` selects the source topic
(default: `booking-events`), and `BOOKING_NOTIFICATIONS_FROM` sets the sender address. SMTP authentication and
STARTTLS can be enabled with `SMTP_AUTH`, `SMTP_STARTTLS_ENABLE`, and `SMTP_STARTTLS_REQUIRED`. Defaults are intended
for local development.

Non-English recipient locales are translated dynamically through Google Cloud Translation. Create a Google Cloud
project, enable the Cloud Translation API, configure a restricted API key as `GOOGLE_TRANSLATE_API_KEY`, and ensure
the project has billing/API access enabled. Google may charge for usage; check its current pricing and free usage
allowance before enabling this in production. Translations of the fixed email phrases are cached in memory by target
language. Recipient names, email addresses, booking data, and interview URLs are inserted after translation and are
not sent to Google. If translation is unavailable or fails, the email uses English text and the application logs a
warning. English locales do not call the translation API.

Each message must contain:

```json
{
  "eventId": "event-123",
  "eventType": "BOOKING_CONFIRMED",
  "bookingId": "booking-456",
  "hostName": "Host",
  "hostEmail": "host@example.com",
  "attendeeName": "Attendee",
  "attendeeEmail": "attendee@example.com",
  "startsAt": "2026-11-02T05:15:00Z",
  "endsAt": "2026-11-02T05:45:00Z",
  "previousStartsAt": null,
  "previousEndsAt": null,
  "hostTimeZone": "Asia/Kolkata",
  "attendeeTimeZone": "Europe/London",
  "interviewUrl": "https://meet.example.com/booking-456",
  "hostLocale": "en-GB",
  "attendeeLocale": "hi-IN"
}
```

`eventType` must be `BOOKING_CONFIRMED`, `BOOKING_RESCHEDULED`, or `BOOKING_CANCELED`. Reschedule events must also
include non-null `previousStartsAt` and `previousEndsAt`. Times are ISO-8601 instants; timezone values must be valid
Java time-zone IDs such as `Asia/Kolkata` or `Europe/London`. `interviewUrl` must be an absolute HTTP or HTTPS URL and
is included as a clickable link in each host and candidate email. `hostLocale` and `attendeeLocale` are optional BCP 47
language tags that independently select each recipient's email language; missing values default to English. Any
language supported by Google Cloud Translation can be requested. These locale values also control the date/time
display format, while the separate timezone fields determine the displayed local time.
