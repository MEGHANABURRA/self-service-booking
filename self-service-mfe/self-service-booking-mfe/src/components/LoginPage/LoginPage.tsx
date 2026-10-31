import { useState, type FormEvent } from 'react'
import Button from '../Button'
import { loginUser } from '../../redux/api/authApi'
import { useAppDispatch, useAppSelector } from '../../redux/hooks'
import '../../styles/gradientBackground.css'
import '../../styles/account.css'
import './LoginPage.css'

function LoginPage() {
  const dispatch = useAppDispatch()
  const { loginStatus, error } = useAppSelector((state) => state.auth)
  const [emailAddress, setEmailAddress] = useState('')
  const [password, setPassword] = useState('')

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = await dispatch(loginUser({
      userName: emailAddress.trim(),
      password,
    }))
    if (loginUser.fulfilled.match(result)) {
      const nextPath =
        window.sessionStorage.getItem('bookingRole') === 'interviewer'
          ? '/hostPreferences'
          : '/Booking'
      window.location.assign(nextPath)
    }
  }

  return (
    <main className="gradient-background account-page">
      <section className="account-content" aria-labelledby="login-title">
        <p className="account-eyebrow">INTERVIEW BOOKING</p>
        <h1 id="login-title">Welcome back</h1>
        <p className="account-intro">Log in to continue.</p>

        <form
          className="account-form"
          onSubmit={handleLogin}
        >
          <label>
            Email address
            <input
              type="email"
              name="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={emailAddress}
              onChange={(event) => setEmailAddress(event.target.value)}
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              placeholder="Enter your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          <Button variant="primary" type="submit" disabled={loginStatus === 'loading'}>
            Log in
          </Button>
          <p className={error ? 'auth-error' : 'account-message'} role={error ? 'alert' : 'status'}>
            {error}
          </p>
        </form>

        <div className="account-signup-prompt">
          <span>New user?</span>
          <Button
            variant="text"
            type="button"
            onClick={() => window.location.assign('/EnterDetails')}
          >
            Sign up
          </Button>
        </div>
      </section>
    </main>
  )
}

export default LoginPage