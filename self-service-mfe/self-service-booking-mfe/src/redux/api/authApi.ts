import { createAsyncThunk } from '@reduxjs/toolkit'
import type { LoginRequest, RegisterRequest } from '../interface'

interface AuthThunkConfig {
  rejectValue: string
}

async function postAuthRequest(
  url: string | undefined,
  body: RegisterRequest | LoginRequest,
  signal: AbortSignal,
): Promise<string | undefined> {
  if (!url) {
    return 'The authentication API URL is not configured.'
  }

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    })

    if (!response.ok) {
      const responseText = await response.text()
      return responseText || `Request failed with status ${response.status}.`
    }
  } catch (error) {
    return error instanceof Error ? error.message : 'The authentication request failed.'
  }
}

export const registerUser = createAsyncThunk<
  void,
  RegisterRequest,
  AuthThunkConfig
>(
  'auth/register',
  async (request, { signal, rejectWithValue }) => {
    const error = await postAuthRequest(
      import.meta.env.VITE_REGISTER_API_URL,
      request,
      signal,
    )
    if (error) {
      return rejectWithValue(error)
    }
  },
)

export const loginUser = createAsyncThunk<void, LoginRequest, AuthThunkConfig>(
  'auth/login',
  async (request, { signal, rejectWithValue }) => {
    const error = await postAuthRequest(
      import.meta.env.VITE_LOGIN_API_URL,
      request,
      signal,
    )
    if (error) {
      return rejectWithValue(error)
    }
  },
)
