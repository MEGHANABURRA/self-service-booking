import { useState } from 'react'
import Button from '../Button'
import '../../styles/gradientBackground.css'
import './BookingComponent.css'

function BookingComponent() {
  const [selectedSlot, setSelectedSlot] = useState('')
  const [continueMessage, setContinueMessage] = useState('')
  const availableSlots = [
    '10:00 - 11:00',
    '11:00 - 12:00',
    '12:00 - 13:00',
    '13:00 - 14:00',
  ]

  return (
    <main className="gradient-background rendered-items-page">
      <section className="time-slot-picker" aria-labelledby="time-slot-title">
        <h1 id="time-slot-title">Choose a time</h1>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            setContinueMessage(`Ready to continue with ${selectedSlot}`)
          }}
        >
          <fieldset className="time-slot-options">
            <legend>Available time slots</legend>
            {availableSlots.map((slot) => (
              <label className="time-slot-option" key={slot}>
                <input
                  type="radio"
                  name="time-slot"
                  value={slot}
                  checked={selectedSlot === slot}
                  onChange={() => {
                    setSelectedSlot(slot)
                    setContinueMessage('')
                  }}
                />
                <span>{slot}</span>
              </label>
            ))}
          </fieldset>
          <Button variant="booking" type="submit" disabled={!selectedSlot}>
            Continue
          </Button>
          <p className="time-slot-message" role="status">{continueMessage}</p>
        </form>
      </section>
    </main>
  )
}

export default BookingComponent