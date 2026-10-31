import { configureStore } from '@reduxjs/toolkit'
import hostPreferencesReducer from './slices/hostPreferencesSlice'
import signupReducer from './slices/signupSlice'

export const store = configureStore({
  reducer: {
    signup: signupReducer,
    hostPreferences: hostPreferencesReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch