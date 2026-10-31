import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type {
  HostPreferenceField,
  HostPreferencesErrors,
  HostPreferencesState,
} from '../interface'

const initialState: HostPreferencesState = {
  values: {
    maxInterviewsPerDay: '',
    gapDuration: '',
  },
  errors: {},
  message: '',
}

const hostPreferencesSlice = createSlice({
  name: 'hostPreferences',
  initialState,
  reducers: {
    updateHostPreference(
      state,
      action: PayloadAction<{ field: HostPreferenceField; value: string }>,
    ) {
      const { field, value } = action.payload
      state.values[field] = value
      delete state.errors[field]
      state.message = ''
    },
    setHostPreferencesErrors(
      state,
      action: PayloadAction<HostPreferencesErrors>,
    ) {
      state.errors = action.payload
    },
    setHostPreferencesMessage(state, action: PayloadAction<string>) {
      state.message = action.payload
    },
  },
})

export const {
  setHostPreferencesErrors,
  setHostPreferencesMessage,
  updateHostPreference,
} = hostPreferencesSlice.actions

export default hostPreferencesSlice.reducer