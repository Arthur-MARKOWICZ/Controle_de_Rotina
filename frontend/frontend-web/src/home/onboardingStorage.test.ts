import { beforeEach, describe, expect, it, vi } from 'vitest'

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

  it('valor diferente de 1 não conta como dispensado', () => {
    localStorage.setItem(chaveOnboardingDismissed('u-1'), '0')
    expect(onboardingFoiDispensado('u-1')).toBe(false)
  })

  it('dispensar de novo não altera o valor', () => {
    dispensarOnboarding('u-1')
    dispensarOnboarding('u-1')
    expect(localStorage.getItem(chaveOnboardingDismissed('u-1'))).toBe('1')
  })

  it('getItem lançando trata como não dispensado', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('quota')
    })
    expect(onboardingFoiDispensado('u-1')).toBe(false)
    spy.mockRestore()
  })

  it('setItem lançando não quebra dispensar', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    expect(() => dispensarOnboarding('u-1')).not.toThrow()
    spy.mockRestore()
    expect(onboardingFoiDispensado('u-1')).toBe(false)
  })

  it('chave inclui o usuarioId', () => {
    expect(chaveOnboardingDismissed('u-9')).toBe('controle-rotina:onboarding-dismissed:u-9')
  })

  it('usuarioId vazio ainda isola a chave', () => {
    dispensarOnboarding('')
    expect(onboardingFoiDispensado('')).toBe(true)
    expect(onboardingFoiDispensado('u-1')).toBe(false)
  })
})
