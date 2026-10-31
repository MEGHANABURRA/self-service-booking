import { createSlice } from '@reduxjs/toolkit'
import { loginUser, registerUser } from '../api/authApi'
import type { AuthState } from '../interface'

const initialState: AuthState = {
  registrationStatus: 'idle',
  loginStatus: 'idle',
  error: null,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(registerUser.pending, (state) => {
        state.registrationStatus = 'loading'
        state.error = null
      })
      .addCase(registerUser.fulfilled, (state) => {
        state.registrationStatus = 'succeeded'
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.registrationStatus = 'failed'
        state.error = action.payload ?? action.error.message ?? 'Registration failed.'
      })
      .addCase(loginUser.pending, (state) => {
        state.loginStatus = 'loading'
        state.error = null
      })
      .addCase(loginUser.fulfilled, (state) => {
        state.loginStatus = 'succeeded'
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.loginStatus = 'failed'
        state.error = action.payload ?? action.error.message ?? 'Login failed.'
      })
  },
})

export default authSlice.reducer
