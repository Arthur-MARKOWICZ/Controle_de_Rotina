const PREFIXO = 'controle-rotina:onboarding-dismissed:'

export function chaveOnboardingDismissed(usuarioId: string): string {
  return `${PREFIXO}${usuarioId}`
}

export function onboardingFoiDispensado(usuarioId: string): boolean {
  try {
    return localStorage.getItem(chaveOnboardingDismissed(usuarioId)) === '1'
  } catch {
    return false
  }
}

export function dispensarOnboarding(usuarioId: string): void {
  try {
    localStorage.setItem(chaveOnboardingDismissed(usuarioId), '1')
  } catch {
    /* storage indisponível: ignore */
  }
}
