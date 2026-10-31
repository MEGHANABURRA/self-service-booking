import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { fetchCallingCodes, fetchCountries } from '../api/signupApi'
import type {
  CallingCodeOption,
  SignupErrors,
  SignupField,
  SignupState,
} from '../interface'

const initialState: SignupState = {
  formValues: {
    firstName: '',
    lastName: '',
    emailAddress: '',
    country: '',
    phoneNumber: '',
  },
  selectedCallingCode: null,
  errors: {},
  message: '',
  countries: { data: [], status: 'idle', error: null },
  callingCodes: { data: [], status: 'idle', error: null },
}

const signupSlice = createSlice({
  name: 'signup',
  initialState,
  reducers: {
    updateSignupField(
      state,
      action: PayloadAction<{ field: SignupField; value: string }>,
    ) {
      const { field, value } = action.payload
      state.formValues[field] = value
      delete state.errors[field]
      state.message = ''
    },
    setSignupFieldError(
      state,
      action: PayloadAction<{ field: SignupField; error?: string }>,
    ) {
      state.errors[action.payload.field] = action.payload.error
    },
    setSignupErrors(state, action: PayloadAction<SignupErrors>) {
      state.errors = action.payload
    },
    setSelectedCallingCode(state, action: PayloadAction<CallingCodeOption>) {
      state.selectedCallingCode = action.payload
    },
    setSignupMessage(state, action: PayloadAction<string>) {
      state.message = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchCallingCodes.pending, (state) => {
        state.callingCodes.status = 'loading'
        state.callingCodes.error = null
      })
      .addCase(fetchCallingCodes.fulfilled, (state, action) => {
        state.callingCodes.data = action.payload
        state.callingCodes.status = 'succeeded'
      })
      .addCase(fetchCallingCodes.rejected, (state, action) => {
        state.callingCodes.status = 'failed'
        state.callingCodes.error =
          action.payload ?? action.error.message ?? 'Request failed.'
      })
      .addCase(fetchCountries.pending, (state) => {
        state.countries.status = 'loading'
        state.countries.error = null
      })
      .addCase(fetchCountries.fulfilled, (state, action) => {
        state.countries.data = action.payload
        state.countries.status = 'succeeded'
      })
      .addCase(fetchCountries.rejected, (state, action) => {
        state.countries.status = 'failed'
        state.countries.error =
          action.payload ?? action.error.message ?? 'Request failed.'
      })
  },
})

export const {
  setSelectedCallingCode,
  setSignupErrors,
  setSignupFieldError,
  setSignupMessage,
  updateSignupField,
} = signupSlice.actions

export default signupSlice.reducer