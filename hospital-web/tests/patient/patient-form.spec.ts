import { describe, expect, it } from 'vitest'
import { isValidPhone, isValidIdCard } from '@/utils/validation'

describe('patient validation', () => {
  it('validates phone and optional id card', () => {
    expect(isValidPhone('13800000000')).toBe(true)
    expect(isValidPhone('12800000000')).toBe(false)
    expect(isValidIdCard('')).toBe(true)
    expect(isValidIdCard('11010519491231002X')).toBe(true)
    expect(isValidIdCard('123')).toBe(false)
  })
})
