import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type {
  AvailabilityDay,
  HostPreferenceField,
  HostPreferencesErrors,
  HostPreferencesState,
} from '../interface'

const initialState: HostPreferencesState = {
  values: {
    maxInterviewsPerDay: '',
    gapDuration: '',
    availability: {
      monday: { startTime: '', endTime: '' },
      tuesday: { startTime: '', endTime: '' },
      wednesday: { startTime: '', endTime: '' },
      thursday: { startTime: '', endTime: '' },
      friday: { startTime: '', endTime: '' },
      saturday: { startTime: '', endTime: '' },
      sunday: { startTime: '', endTime: '' },
    },
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
    updateHostAvailability(
      state,
      action: PayloadAction<{
        day: AvailabilityDay
        field: 'startTime' | 'endTime'
        value: string
      }>,
    ) {
      const { day, field, value } = action.payload
      state.values.availability[day][field] = value
      const dayErrors = state.errors.availability?.[day]
      if (dayErrors) {
        delete dayErrors[field]
        if (Object.keys(dayErrors).length === 0) {
          delete state.errors.availability?.[day]
        }
        if (Object.keys(state.errors.availability ?? {}).length === 0) {
          delete state.errors.availability
        }
      }
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
  updateHostAvailability,
  updateHostPreference,
} = hostPreferencesSlice.actions

export default hostPreferencesSlice.reducer