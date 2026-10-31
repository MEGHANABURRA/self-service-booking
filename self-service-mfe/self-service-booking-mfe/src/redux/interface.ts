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
  password: string
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

export interface RegisterRequest {
  name: string
  userName: string
  password: string
  timeZone: string
  userRole: 'HOST' | 'INTERVIEWEE'
}

export interface LoginRequest {
  userName: string
  password: string
}

export interface AuthState {
  registrationStatus: 'idle' | 'loading' | 'succeeded' | 'failed'
  loginStatus: 'idle' | 'loading' | 'succeeded' | 'failed'
  error: string | null
}

export interface HostPreferencesValues {
  maxInterviewsPerDay: string
  gapDuration: string
  availability: Record<AvailabilityDay, AvailabilityHours>
}

export type AvailabilityDay =
  | 'monday'
  | 'tuesday'
  | 'wednesday'
  | 'thursday'
  | 'friday'
  | 'saturday'
  | 'sunday'

export interface AvailabilityHours {
  startTime: string
  endTime: string
}

export type HostPreferenceField = 'maxInterviewsPerDay' | 'gapDuration'
export type AvailabilityDayErrors = Partial<
  Record<keyof AvailabilityHours, string | undefined>
>
export interface HostPreferencesErrors
  extends Partial<Record<HostPreferenceField, string | undefined>> {
  availability?: Partial<Record<AvailabilityDay, AvailabilityDayErrors>>
}

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