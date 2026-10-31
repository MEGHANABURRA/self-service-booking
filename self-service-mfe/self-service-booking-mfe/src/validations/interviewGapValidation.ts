const allowedGapDurations = ['5', '10', '15', '25', '35', '45', '60', '60+']

export function interviewGapValidation(value: string): string | undefined {
  if (!value) {
    return 'Select a gap duration.'
  }

  if (!allowedGapDurations.includes(value)) {
    return 'Select a valid gap duration.'
  }
}