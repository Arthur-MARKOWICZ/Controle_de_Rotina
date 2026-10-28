type InlineAlertProps = {
  children: string
  tone?: 'error' | 'success' | 'info'
}

export function InlineAlert({ children, tone = 'error' }: InlineAlertProps) {
  const role = tone === 'error' ? 'alert' : 'status'
  return (
    <p className={`inline-alert inline-alert--${tone}`} role={role}>
      {children}
    </p>
  )
}
