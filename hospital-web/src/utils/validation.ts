export function isValidPhone(value: string) {
  return /^1[3-9]\d{9}$/.test(value)
}

export function isValidIdCard(value: string) {
  return value === '' || /^(\d{17}[\dXx])?$/.test(value)
}
