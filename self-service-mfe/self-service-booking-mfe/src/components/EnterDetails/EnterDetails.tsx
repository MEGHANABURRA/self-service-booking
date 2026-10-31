import { useEffect, type FormEvent } from 'react'
import Button from '../Button'
import SearchableDropdown from '../Dropdown/SearchableDropdown'
import { useAppDispatch, useAppSelector } from '../../redux/hooks'
import { fetchCallingCodes, fetchCountries } from '../../redux/api/signupApi'
import {
  setSelectedCallingCode,
  setSignupErrors,
  setSignupFieldError,
  setSignupMessage,
  updateSignupField,
} from '../../redux/slices/signupSlice'
import type { SignupErrors, SignupField, SignupValues } from '../../redux/interface'
import { countryValidation } from '../../validations/countryValidation'
import { emailAddressValidation } from '../../validations/emailAddressValidation'
import { firstNameValidation } from '../../validations/firstNameValidation'
import { lastNameValidation } from '../../validations/lastNameValidation'
import { phoneNumberValidation } from '../../validations/phoneNumberValidation'
import '../../styles/gradientBackground.css'
import '../../styles/account.css'
import './EnterDetails.css'

function validateSignupField(
  field: SignupField,
  values: SignupValues,
  callingCode: string,
): string | undefined {
  switch (field) {
    case 'firstName':
      return firstNameValidation(values.firstName)
    case 'lastName':
      return lastNameValidation(values.lastName)
    case 'emailAddress':
      return emailAddressValidation(values.emailAddress)
    case 'country':
      return countryValidation(values.country)
    case 'phoneNumber':
      return phoneNumberValidation(values.phoneNumber, callingCode)
  }
}

function EnterDetails() {
  const dispatch = useAppDispatch()
  const signupState = useAppSelector((state) => state.signup)
  const {
    formValues,
    errors,
    message,
    callingCodes,
    countries,
    selectedCallingCode,
  } = signupState
  const isLoadingCodes =
    callingCodes.status === 'idle' || callingCodes.status === 'loading'
  const codeLoadError = callingCodes.status === 'failed'
  const isLoadingCountries =
    countries.status === 'idle' || countries.status === 'loading'
  const countryLoadError = countries.status === 'failed'

  useEffect(() => {
    dispatch(fetchCallingCodes())
    dispatch(fetchCountries())
  }, [dispatch])

  function updateField(field: SignupField, value: string) {
    const updatedValues = { ...formValues, [field]: value }
    dispatch(updateSignupField({ field, value }))

    if (errors[field]) {
      dispatch(setSignupFieldError({
        field,
        error: validateSignupField(
          field,
          updatedValues,
          selectedCallingCode?.callingCode ?? '',
        ),
      }))
    }
  }

  function validateField(field: SignupField) {
    dispatch(setSignupFieldError({
      field,
      error: validateSignupField(
        field,
        formValues,
        selectedCallingCode?.callingCode ?? '',
      ),
    }))
  }

  function handleSignupSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors: SignupErrors = {
      firstName: firstNameValidation(formValues.firstName),
      lastName: lastNameValidation(formValues.lastName),
      emailAddress: emailAddressValidation(formValues.emailAddress),
      country: countryValidation(formValues.country),
      phoneNumber: phoneNumberValidation(
        formValues.phoneNumber,
        selectedCallingCode?.callingCode ?? '',
      ),
    }

    dispatch(setSignupErrors(nextErrors))
    if (Object.values(nextErrors).some(Boolean)) {
      dispatch(setSignupMessage('Please correct the highlighted fields.'))
      return
    }

    dispatch(setSignupMessage('Your details are ready for account creation.'))
    const nextPath =
      window.sessionStorage.getItem('bookingRole') === 'interviewer'
        ? '/hostPreferences'
        : '/Booking'
    window.location.assign(nextPath)
  }

  return (
    <main className="gradient-background account-page">
      <section className="account-content signup-content" aria-labelledby="signup-title">
        <p className="account-eyebrow">CREATE YOUR ACCOUNT</p>
        <h1 id="signup-title">Your details</h1>
        <p className="account-intro">Tell us a little about yourself.</p>

        <form
          className="account-form signup-form"
          noValidate
          onSubmit={handleSignupSubmit}
        >
          <label htmlFor="first-name">
            First name
            <input
              id="first-name"
              type="text"
              name="given-name"
              autoComplete="given-name"
              placeholder="Enter your first name"
              value={formValues.firstName}
              aria-invalid={Boolean(errors.firstName)}
              aria-describedby={errors.firstName ? 'first-name-error' : undefined}
              onChange={(event) => updateField('firstName', event.target.value)}
              onBlur={() => validateField('firstName')}
              required
            />
            {errors.firstName && (
              <span className="field-error" id="first-name-error">
                {errors.firstName}
              </span>
            )}
          </label>
          <label htmlFor="last-name">
            Last name
            <input
              id="last-name"
              type="text"
              name="family-name"
              autoComplete="family-name"
              placeholder="Enter your last name"
              value={formValues.lastName}
              aria-invalid={Boolean(errors.lastName)}
              aria-describedby={errors.lastName ? 'last-name-error' : undefined}
              onChange={(event) => updateField('lastName', event.target.value)}
              onBlur={() => validateField('lastName')}
              required
            />
            {errors.lastName && (
              <span className="field-error" id="last-name-error">
                {errors.lastName}
              </span>
            )}
          </label>
          <label className="signup-email-field" htmlFor="email-address">
            Email address
            <input
              id="email-address"
              type="email"
              name="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={formValues.emailAddress}
              aria-invalid={Boolean(errors.emailAddress)}
              aria-describedby={errors.emailAddress ? 'email-address-error' : undefined}
              onChange={(event) => updateField('emailAddress', event.target.value)}
              onBlur={() => validateField('emailAddress')}
              required
            />
            {errors.emailAddress && (
              <span className="field-error" id="email-address-error">
                {errors.emailAddress}
              </span>
            )}
          </label>
          <div className="signup-country-field">
            <label htmlFor="country">Country</label>
            <SearchableDropdown
              ariaLabel="Country"
              describedBy={errors.country ? 'country-error' : undefined}
              error={countryLoadError ? 'Countries could not be loaded.' : null}
              id="country"
              invalid={Boolean(errors.country)}
              loading={isLoadingCountries}
              options={countries.data.map((option) => ({
                value: option.countryName,
                label: option.countryName,
                searchText: option.countryCode,
              }))}
              placeholder="Select your country"
              searchPlaceholder="Search countries"
              value={formValues.country}
              onChange={(value) => updateField('country', value)}
            />
            <input type="hidden" name="country" value={formValues.country} />
            {errors.country && (
              <span className="field-error" id="country-error">
                {errors.country}
              </span>
            )}
          </div>
          <div className="signup-phone-field">
            <label htmlFor="phone-number">Phone number</label>
            <div className="phone-number-control">
              <SearchableDropdown
                ariaLabel="Country calling code"
                describedBy={errors.phoneNumber ? 'phone-number-error' : undefined}
                error={codeLoadError ? 'Country calling codes could not be loaded.' : null}
                invalid={Boolean(errors.phoneNumber)}
                loading={isLoadingCodes}
                options={callingCodes.data.map((option) => ({
                  value: option.countryCode,
                  label: option.callingCode,
                  detail: option.countryName,
                  searchText: option.callingCode,
                }))}
                placeholder="Code"
                searchPlaceholder="Search country or calling code"
                value={selectedCallingCode?.countryCode ?? ''}
                variant="compact"
                onChange={(countryCode) => {
                  const option = callingCodes.data.find(
                    (callingCode) => callingCode.countryCode === countryCode,
                  )
                  if (!option) {
                    return
                  }

                  dispatch(setSelectedCallingCode(option))
                  if (errors.phoneNumber) {
                    dispatch(setSignupFieldError({
                      field: 'phoneNumber',
                      error: phoneNumberValidation(
                        formValues.phoneNumber,
                        option.callingCode,
                      ),
                    }))
                  }
                }}
              />
              <input
                id="phone-number"
                type="tel"
                name="tel"
                autoComplete="tel-national"
                inputMode="tel"
                placeholder="e.g. 416 555 0123"
                value={formValues.phoneNumber}
                aria-invalid={Boolean(errors.phoneNumber)}
                aria-describedby={errors.phoneNumber ? 'phone-number-error' : undefined}
                onChange={(event) => updateField('phoneNumber', event.target.value)}
                onBlur={() => validateField('phoneNumber')}
                required
              />
            </div>
            <input
              type="hidden"
              name="callingCode"
              value={selectedCallingCode?.callingCode ?? ''}
            />
            {errors.phoneNumber && (
              <span className="field-error" id="phone-number-error">
                {errors.phoneNumber}
              </span>
            )}
          </div>
          <Button className="signup-submit" variant="primary" type="submit">
            Create account
          </Button>
        </form>
        <p className="account-message" role="status">{message}</p>
      </section>

    </main>
  )
}

export default EnterDetails