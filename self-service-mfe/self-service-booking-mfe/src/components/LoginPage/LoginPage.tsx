import Button from '../Button'
import '../../styles/gradientBackground.css'
import '../../styles/account.css'
import './LoginPage.css'

function LoginPage() {
  return (
    <main className="gradient-background account-page">
      <section className="account-content" aria-labelledby="login-title">
        <p className="account-eyebrow">INTERVIEW BOOKING</p>
        <h1 id="login-title">Welcome back</h1>
        <p className="account-intro">Log in to continue.</p>

        <form
          className="account-form"
          onSubmit={(event) => {
            event.preventDefault()
            const nextPath =
              window.sessionStorage.getItem('bookingRole') === 'interviewer'
                ? '/hostPreferences'
                : '/Booking'
            window.location.assign(nextPath)
          }}
        >
          <label>
            Email address
            <input
              type="email"
              name="email"
              autoComplete="email"
              placeholder="name@example.com"
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
              required
            />
          </label>
          <Button variant="primary" type="submit">Log in</Button>
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