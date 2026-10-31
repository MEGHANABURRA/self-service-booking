export function firstNameValidation(value: string): string | undefined {
  const name = value.trim()

  if (!name) {
    return 'Enter your first name.'
  }

  if (name.length > 80 || !/^[\p{L}\p{M}][\p{L}\p{M}\s'’.-]*$/u.test(name)) {
    return 'Enter a valid first name.'
  }
}