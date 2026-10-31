export function emailAddressValidation(value: string): string | undefined {
  const email = value.trim()

  if (!email) {
    return 'Enter your email address.'
  }

  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    return 'Enter a valid email address.'
  }
}