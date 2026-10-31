export function phoneNumberValidation(
  value: string,
  callingCode: string,
): string | undefined {
  if (!callingCode) {
    return 'Choose a country calling code.'
  }

  const number = value.trim()
  if (!number) {
    return 'Enter your phone number.'
  }

  if (!/^[\d\s()+.-]+$/.test(number)) {
    return 'Use digits and standard phone number separators only.'
  }

  const phoneDigits = number.replace(/\D/g, '')
  const callingCodeDigits = callingCode.replace(/\D/g, '')
  if (phoneDigits.length < 4 || phoneDigits.length + callingCodeDigits.length > 15) {
    return 'Enter a phone number with a valid international length.'
  }
}