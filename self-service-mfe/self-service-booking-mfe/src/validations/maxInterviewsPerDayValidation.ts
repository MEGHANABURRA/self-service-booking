export function maxInterviewsPerDayValidation(value: string): string | undefined {
  if (!value) {
    return 'Enter the maximum interviews per day.'
  }

  const maximum = Number(value)
  if (maximum > 10) {
    return 'Maximum number of interviews should be less than 10'
  }

  if (!Number.isSafeInteger(maximum) || maximum < 1) {
    return 'Enter a whole number greater than zero.'
  }
}