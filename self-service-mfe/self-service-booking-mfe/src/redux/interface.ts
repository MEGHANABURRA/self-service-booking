export interface CallingCodeOption {
  countryCode: string
  countryName: string
  callingCode: string
}

export interface CountryOption {
  countryCode: string
  countryName: string
}

export interface SignupValues {
  firstName: string
  lastName: string
  emailAddress: string
  country: string
  phoneNumber: string
}

export type SignupField = keyof SignupValues
export type SignupErrors = Partial<Record<SignupField, string | undefined>>

export interface AsyncListState<T> {
  data: T[]
  status: 'idle' | 'loading' | 'succeeded' | 'failed'
  error: string | null
}

export interface SignupState {
  formValues: SignupValues
  selectedCallingCode: CallingCodeOption | null
  errors: SignupErrors
  message: string
  countries: AsyncListState<CountryOption>
  callingCodes: AsyncListState<CallingCodeOption>
}

export interface HostPreferencesValues {
  maxInterviewsPerDay: string
  gapDuration: string
}

export type HostPreferenceField = keyof HostPreferencesValues
export type HostPreferencesErrors = Partial<
  Record<HostPreferenceField, string | undefined>
>

export interface HostPreferencesState {
  values: HostPreferencesValues
  errors: HostPreferencesErrors
  message: string
}

export interface CallingCodeApiEntry {
  code?: string
  dial_code?: string
  name?: string
}

export interface CountryApiEntry {
  cca2?: string
  name?: { common?: string }
}