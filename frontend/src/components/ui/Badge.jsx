import Icon from '../Icon.jsx'

// Estado siempre con texto + ícono (nunca solo color). tone: success | warning | danger | neutral
const tones = {
  success: { classes: 'bg-success text-success', icon: 'check' },
  warning: { classes: 'bg-warning text-warning', icon: 'clock' },
  danger: { classes: 'bg-danger text-danger', icon: 'alert' },
  neutral: { classes: 'border bg-app text-secondary', icon: 'info' },
}

export default function Badge({ tone = 'neutral', children }) {
  const { classes, icon } = tones[tone]
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-small font-semibold ${classes}`}>
      <Icon name={icon} className="h-3.5 w-3.5 shrink-0" />
      <span className="break-words">{children}</span>
    </span>
  )
}
