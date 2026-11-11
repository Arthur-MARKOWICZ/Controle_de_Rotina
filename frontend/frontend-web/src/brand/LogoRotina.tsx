/** Marca do produto: agenda com check (opção D). */
export function LogoRotina() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <rect x="3" y="6.5" width="26" height="22.5" rx="7" fill="var(--logo-body, #0f766e)" />
      <circle cx="10" cy="6.5" r="2.6" fill="var(--logo-body, #0f766e)" />
      <circle cx="22" cy="6.5" r="2.6" fill="var(--logo-cta, #ea580c)" />
      <path
        d="M10.5 18.2 14.4 22.1 21.8 13.6"
        stroke="var(--logo-check, #fff)"
        strokeWidth="2.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
