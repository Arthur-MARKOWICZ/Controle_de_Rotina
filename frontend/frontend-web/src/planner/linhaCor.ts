/** Cores de linha do plano do dia (tokens `--linha-1…4` em tokens.css). */
export const LINHAS = [
  'var(--linha-1)',
  'var(--linha-2)',
  'var(--linha-3)',
  'var(--linha-4)',
] as const

type Bloco = {
  origem?: 'TAREFA' | 'HABITO'
  projetoNome?: string | null
}

/**
 * Cor do trecho da linha do plano: cada projeto pega sempre a mesma "linha de metrô";
 * hábito é cinza; tarefa sem projeto usa a cor de ação.
 */
export function corDaLinha({ origem = 'TAREFA', projetoNome }: Bloco): string {
  if (origem === 'HABITO') {
    return 'var(--linha-habito)'
  }
  const chave = projetoNome?.trim().toLocaleLowerCase('pt-BR')
  if (!chave) {
    return 'var(--accent)'
  }
  let hash = 0
  for (const caractere of chave) {
    hash = (hash * 31 + caractere.codePointAt(0)!) >>> 0
  }
  return LINHAS[hash % LINHAS.length]
}
