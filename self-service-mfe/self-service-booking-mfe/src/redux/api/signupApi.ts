import { createAsyncThunk } from '@reduxjs/toolkit'
import type {
  CallingCodeApiEntry,
  CallingCodeOption,
  CountryApiEntry,
  CountryOption,
  SignupState,
} from '../interface'

type SignupThunkConfig = {
  rejectValue: string
  state: { signup: SignupState }
}

export const fetchCallingCodes = createAsyncThunk<
  CallingCodeOption[],
  void,
  SignupThunkConfig
>(
  'signup/fetchCallingCodes',
  async (_, { signal, rejectWithValue }) => {
    const response = await fetch(
      import.meta.env.VITE_COUNTRY_CALLING_CODES_API_URL,
      { signal },
    )

    if (!response.ok) {
      return rejectWithValue('Country calling codes could not be loaded.')
    }

    const result = (await response.json()) as { data?: CallingCodeApiEntry[] }
    const options = (result.data ?? [])
      .filter(
        (country) =>
          country.code &&
          country.name &&
          country.dial_code &&
          /^\+\d+$/.test(country.dial_code),
      )
      .map((country) => ({
        countryCode: country.code!,
        countryName: country.name!,
        callingCode: country.dial_code!,
      }))

    options.sort((first, second) =>
      first.countryName.localeCompare(second.countryName),
    )
    return options
  },
  {
    condition: (_, { getState }) =>
      getState().signup.callingCodes.status === 'idle',
  },
)

export const fetchCountries = createAsyncThunk<
  CountryOption[],
  void,
  SignupThunkConfig
>(
  'signup/fetchCountries',
  async (_, { signal, rejectWithValue }) => {
    const response = await fetch(
      import.meta.env.VITE_COUNTRIES_API_URL,
      { signal },
    )

    if (!response.ok) {
      return rejectWithValue('Countries could not be loaded.')
    }

    const countryData = (await response.json()) as CountryApiEntry[]
    const options = countryData.flatMap((country) => {
      const countryName = country.name?.common

      if (!country.cca2 || !countryName) {
        return []
      }

      return [{ countryCode: country.cca2, countryName }]
    })

    options.sort((first, second) =>
      first.countryName.localeCompare(second.countryName),
    )
    return options
  },
  {
    condition: (_, { getState }) => getState().signup.countries.status === 'idle',
  },
)