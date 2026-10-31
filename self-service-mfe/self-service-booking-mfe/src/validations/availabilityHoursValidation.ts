import type {
  AvailabilityDayErrors,
  AvailabilityHours,
} from '../redux/interface'

function getMinutes(value: string): number | undefined {
  const match = /^([01]\d|2[0-3]):([0-5]\d)Z$/.exec(value)
  if (!match) {
    return undefined
  }

  return Number(match[1]) * 60 + Number(match[2])
}

export function availabilityHoursValidation({
  startTime,
  endTime,
}: AvailabilityHours): AvailabilityDayErrors | undefined {
  const errors: AvailabilityDayErrors = {}
  if (!startTime) {
    errors.startTime = 'Select a slot.'
  }
  if (!endTime) {
    errors.endTime = 'Select a slot.'
  }
  if (Object.keys(errors).length > 0) {
    return errors
  }

  const startMinutes = getMinutes(startTime)
  const endMinutes = getMinutes(endTime)
  if (startMinutes === undefined || endMinutes === undefined) {
    return { endTime: 'Enter a valid start and end time.' }
  }

  if (endMinutes <= startMinutes) {
    return { endTime: 'End time must be after start time.' }
  }
}
