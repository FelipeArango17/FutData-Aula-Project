import { Link } from 'react-router-dom'

const base =
  'inline-flex min-h-11 items-center justify-center rounded-md px-4 py-2 text-center text-body-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'
const variants = {
  primary: 'bg-brand-primary text-on-dark hover:bg-brand-primary-hover',
  secondary: 'border bg-surface text-primary hover:bg-app',
}

// Con `to` es un enlace; sin `to` es un botón (sin acción mientras no haya backend).
export default function Button({ variant = 'primary', to, className = '', children, ...rest }) {
  const classes = `${base} ${variants[variant]} ${className}`
  if (to) return <Link to={to} className={classes} {...rest}>{children}</Link>
  return <button type="button" className={classes} {...rest}>{children}</button>
}
