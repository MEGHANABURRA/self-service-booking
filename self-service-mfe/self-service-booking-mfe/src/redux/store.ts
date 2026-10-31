import { configureStore } from '@reduxjs/toolkit'
import authReducer from './slices/authSlice'
import hostPreferencesReducer from './slices/hostPreferencesSlice'
import signupReducer from './slices/signupSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    signup: signupReducer,
    hostPreferences: hostPreferencesReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch