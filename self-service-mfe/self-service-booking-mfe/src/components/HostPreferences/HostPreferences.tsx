import { useAppDispatch, useAppSelector } from '../../redux/hooks'
import {
  setHostPreferencesErrors,
  setHostPreferencesMessage,
  updateHostAvailability,
  updateHostPreference,
} from '../../redux/slices/hostPreferencesSlice'
import { availabilityHoursValidation } from '../../validations/availabilityHoursValidation'
import { interviewGapValidation } from '../../validations/interviewGapValidation'
import { maxInterviewsPerDayValidation } from '../../validations/maxInterviewsPerDayValidation'
import Button from '../Button'
import '../../styles/gradientBackground.css'
import '../../styles/account.css'
import './HostPreferences.css'

const weekdays = [
  { key: 'monday', label: 'Monday' },
  { key: 'tuesday', label: 'Tuesday' },
  { key: 'wednesday', label: 'Wednesday' },
  { key: 'thursday', label: 'Thursday' },
  { key: 'friday', label: 'Friday' },
  { key: 'saturday', label: 'Saturday' },
  { key: 'sunday', label: 'Sunday' },
] as const

function HostPreferences() {
  const dispatch = useAppDispatch()
  const { values, errors, message } = useAppSelector(
    (state) => state.hostPreferences,
  )
  const maxInterviewsPerDayError =
    errors.maxInterviewsPerDay ??
    (Number(values.maxInterviewsPerDay) > 10
      ? maxInterviewsPerDayValidation(values.maxInterviewsPerDay)
      : undefined)

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = {
      maxInterviewsPerDay: maxInterviewsPerDayValidation(
        values.maxInterviewsPerDay,
      ),
      gapDuration: interviewGapValidation(values.gapDuration),
      availability: Object.fromEntries(
        weekdays.flatMap(({ key }) => {
          const errors = availabilityHoursValidation(values.availability[key])
          return errors ? [[key, errors]] : []
        }),
      ),
    }

    dispatch(setHostPreferencesErrors(nextErrors))
    const hasAvailabilityErrors = Object.keys(nextErrors.availability).length > 0
    if (
      nextErrors.maxInterviewsPerDay ||
      nextErrors.gapDuration ||
      hasAvailabilityErrors
    ) {
      dispatch(setHostPreferencesMessage('Please correct the highlighted fields.'))
      return
    }

    dispatch(setHostPreferencesMessage('Preferences saved.'))
  }

  return (
    <main className="gradient-background account-page">
      <section
        className="account-content host-preferences-content"
        aria-labelledby="host-preferences-title"
      >
        <p className="account-eyebrow">INTERVIEWER SETUP</p>
        <h1 id="host-preferences-title">Host preferences</h1>
        <p className="account-intro">Set your daily interview capacity and break time.</p>

        <form className="account-form" noValidate onSubmit={handleSubmit}>
          <label htmlFor="max-interviews-per-day">
            Maximum interviews per day
            <input
              id="max-interviews-per-day"
              name="maxInterviewsPerDay"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder="Enter a number"
              value={values.maxInterviewsPerDay}
              aria-invalid={Boolean(maxInterviewsPerDayError)}
              aria-describedby={
                maxInterviewsPerDayError ? 'max-interviews-error' : undefined
              }
              onChange={(event) => {
                const digitsOnly = event.target.value.replace(/\D/g, '')
                dispatch(updateHostPreference({
                  field: 'maxInterviewsPerDay',
                  value: digitsOnly,
                }))
              }}
              required
            />
            {maxInterviewsPerDayError && (
              <span className="field-error" id="max-interviews-error">
                {maxInterviewsPerDayError}
              </span>
            )}
          </label>

          <label htmlFor="gap-duration">
            Gap between interviews
            <select
              id="gap-duration"
              name="gapDuration"
              value={values.gapDuration}
              aria-invalid={Boolean(errors.gapDuration)}
              aria-describedby={errors.gapDuration ? 'gap-duration-error' : undefined}
              onChange={(event) => dispatch(updateHostPreference({
                field: 'gapDuration',
                value: event.target.value,
              }))}
              required
            >
              <option value="" disabled>Select a gap duration</option>
              <option value="5">5 min</option>
              <option value="10">10 min</option>
              <option value="15">15 min</option>
              <option value="25">25 min</option>
              <option value="35">35 min</option>
              <option value="45">45 min</option>
              <option value="60">60 min</option>
              <option value="60+">More than 60 min</option>
            </select>
            {errors.gapDuration && (
              <span className="field-error" id="gap-duration-error">
                {errors.gapDuration}
              </span>
            )}
          </label>

          {weekdays.map(({ key, label }) => (
            <fieldset className="availability-day" key={key}>
              <legend>{label}</legend>
              <div className="availability-times">
                <label htmlFor={`${key}-start-time`}>
                  Start time
                  <input
                    id={`${key}-start-time`}
                    name={`${key}StartTime`}
                    type="time"
                    value={values.availability[key].startTime.slice(0, 5)}
                    required
                    aria-invalid={Boolean(errors.availability?.[key]?.startTime)}
                    aria-describedby={
                      errors.availability?.[key]?.startTime
                        ? `${key}-start-time-error`
                        : undefined
                    }
                    onChange={(event) => dispatch(updateHostAvailability({
                      day: key,
                      field: 'startTime',
                      value: event.target.value ? `${event.target.value}Z` : '',
                    }))}
                  />
                  {errors.availability?.[key]?.startTime && (
                    <span className="field-error" id={`${key}-start-time-error`}>
                      {errors.availability[key]?.startTime}
                    </span>
                  )}
                </label>
                <label htmlFor={`${key}-end-time`}>
                  End time
                  <input
                    id={`${key}-end-time`}
                    name={`${key}EndTime`}
                    type="time"
                    value={values.availability[key].endTime.slice(0, 5)}
                    required
                    aria-invalid={Boolean(errors.availability?.[key]?.endTime)}
                    aria-describedby={
                      errors.availability?.[key]?.endTime
                        ? `${key}-availability-error`
                        : undefined
                    }
                    onChange={(event) => dispatch(updateHostAvailability({
                      day: key,
                      field: 'endTime',
                      value: event.target.value ? `${event.target.value}Z` : '',
                    }))}
                  />
                  {errors.availability?.[key]?.endTime && (
                    <span className="field-error" id={`${key}-availability-error`}>
                      {errors.availability[key]?.endTime}
                    </span>
                  )}
                </label>
              </div>
            </fieldset>
          ))}

          <Button variant="primary" type="submit">Save preferences</Button>
        </form>
        <p className="account-message" role="status">{message}</p>
      </section>
    </main>
  )
}

export default HostPreferences