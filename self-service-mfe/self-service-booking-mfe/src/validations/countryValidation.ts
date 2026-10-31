export function countryValidation(value: string): string | undefined {
  if (!value.trim()) {
    return 'Select your country.'
  }
}