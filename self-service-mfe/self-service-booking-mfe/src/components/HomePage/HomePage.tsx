import { useState } from 'react'
import Button from '../Button'
import '../../styles/gradientBackground.css'
import './HomePage.css'

const roles = [
  {
    value: 'interviewer',
    label: 'Interviewer',
    image:
      'https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=1200&q=85',
  },
  {
    value: 'interviewee',
    label: 'Interviewee',
    image:
      'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=1200&q=85',
  },
]

function HomePage() {
  const [selectedRole, setSelectedRole] = useState('')

  function handleNext() {
    window.sessionStorage.setItem('bookingRole', selectedRole)
    window.location.assign('/LoginPage')
  }

  return (
    <main className="gradient-background home-page">
      <section className="home-content" aria-labelledby="home-title">
        <p className="home-eyebrow">INTERVIEW BOOKING</p>
        <h1 id="home-title">Choose your role</h1>

        <fieldset className="role-options">
          <legend className="visually-hidden">Choose your role</legend>
          {roles.map((role) => (
            <label
              className={`role-card${selectedRole === role.value ? ' is-selected' : ''}`}
              key={role.value}
            >
              <input
                type="radio"
                name="role"
                value={role.value}
                checked={selectedRole === role.value}
                onChange={() => setSelectedRole(role.value)}
              />
              <img src={role.image} alt="" />
              <span className="role-card-shade" />
              <span className="role-card-label">{role.label}</span>
              <span className="role-card-checkbox" aria-hidden="true" />
            </label>
          ))}
        </fieldset>

        <Button
          className="home-next-button"
          variant="primary"
          type="button"
          disabled={!selectedRole}
          onClick={handleNext}
        >
          Next
        </Button>
      </section>
    </main>
  )
}

export default HomePage