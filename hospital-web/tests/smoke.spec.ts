import { describe, expect, it } from 'vitest'

describe('frontend scaffold', () => {
  it('runs Vitest in jsdom', () => {
    expect(typeof document).toBe('object')
    expect(sessionStorage).toBeDefined()
  })
})
