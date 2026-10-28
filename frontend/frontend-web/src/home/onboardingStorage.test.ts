import { beforeEach, describe, expect, it } from 'vitest'

import {
  chaveOnboardingDismissed,
  dispensarOnboarding,
  onboardingFoiDispensado,
} from './onboardingStorage'

describe('onboardingStorage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('marca e lê dismiss por usuarioId', () => {
    expect(onboardingFoiDispensado('u-1')).toBe(false)
    dispensarOnboarding('u-1')
    expect(onboardingFoiDispensado('u-1')).toBe(true)
    expect(localStorage.getItem(chaveOnboardingDismissed('u-1'))).toBe('1')
    expect(onboardingFoiDispensado('u-2')).toBe(false)
  })
})
