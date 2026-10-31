import { useAppDispatch, useAppSelector } from '../../redux/hooks'
import {
  setHostPreferencesErrors,
  setHostPreferencesMessage,
  updateHostPreference,
} from '../../redux/slices/hostPreferencesSlice'
import { interviewGapValidation } from '../../validations/interviewGapValidation'
import { maxInterviewsPerDayValidation } from '../../validations/maxInterviewsPerDayValidation'
import Button from '../Button'
import '../../styles/gradientBackground.css'
import '../../styles/account.css'
import './HostPreferences.css'

function HostPreferences() {
  const dispatch = useAppDispatch()
  const { values, errors, message } = useAppSelector(
    (state) => state.hostPreferences,
  )

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = {
      maxInterviewsPerDay: maxInterviewsPerDayValidation(
        values.maxInterviewsPerDay,
      ),
      gapDuration: interviewGapValidation(values.gapDuration),
    }

    dispatch(setHostPreferencesErrors(nextErrors))
    if (Object.values(nextErrors).some(Boolean)) {
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
              aria-invalid={Boolean(errors.maxInterviewsPerDay)}
              aria-describedby={
                errors.maxInterviewsPerDay ? 'max-interviews-error' : undefined
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
            {errors.maxInterviewsPerDay && (
              <span className="field-error" id="max-interviews-error">
                {errors.maxInterviewsPerDay}
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

          <Button variant="primary" type="submit">Save preferences</Button>
        </form>
        <p className="account-message" role="status">{message}</p>
      </section>
    </main>
  )
}

export default HostPreferences