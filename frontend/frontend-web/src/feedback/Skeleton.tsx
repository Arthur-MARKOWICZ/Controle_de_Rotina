type SkeletonVariant = 'plano-blocos' | 'tarefas-lista' | 'capacidade-grade'

const VARIANT_ROWS: Record<SkeletonVariant, number> = {
  'plano-blocos': 3,
  'tarefas-lista': 4,
  'capacidade-grade': 7,
}

type SkeletonProps = {
  variant: SkeletonVariant
}

export function Skeleton({ variant }: SkeletonProps) {
  const rows = VARIANT_ROWS[variant]
  return (
    <div
      className={`skeleton skeleton--${variant}`}
      aria-busy="true"
      aria-live="polite"
      role="status"
    >
      <span className="visually-hidden">Carregando…</span>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton__item" />
      ))}
    </div>
  )
}
