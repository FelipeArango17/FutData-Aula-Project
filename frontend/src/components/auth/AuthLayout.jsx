import Logo from '../Logo.jsx'
import Icon from '../Icon.jsx'

const beneficios = [
  'Pagos en línea con PSE, tarjetas y Nequi',
  'Asistencia y horarios siempre al día',
  'Alertas por correo para acudientes',
]

// Diseño de las pantallas públicas (fuera del AppShell: sin menú).
// variante "dividido" (inicio de sesión): panel de marca a la izquierda desde md; en móvil solo el logo arriba.
// variante "centrado" (código 2FA, recuperación e inscripción en línea): tarjeta centrada.
// ancho (solo en "centrado"): "angosto" (por defecto) o "amplio", para formularios largos como la inscripción.
export default function AuthLayout({ variante = 'dividido', ancho = 'angosto', children }) {
  if (variante === 'centrado') {
    return (
      <main className="flex min-h-app items-center justify-center bg-app p-4 pb-safe pt-safe">
        <div className={`w-full ${ancho === 'amplio' ? 'max-w-2xl' : 'max-w-md'}`}>{children}</div>
      </main>
    )
  }

  return (
    <div className="flex min-h-app flex-col md:flex-row">
      <aside className="hidden min-w-0 flex-col justify-center gap-6 bg-sidebar p-8 text-on-dark md:flex md:basis-2/5 lg:p-12">
        <Logo />
        <p className="break-words text-h2">Gestión integral para clubes de fútbol formativo</p>
        <p className="break-words text-body">
          Inscripciones, pagos en línea, asistencia, documentos y torneos de Prado FC en una sola plataforma.
        </p>
        <ul className="flex flex-col gap-3">
          {beneficios.map((texto) => (
            <li key={texto} className="flex items-start gap-2 text-body">
              <span aria-hidden="true" className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-accent text-primary">
                <Icon name="check" className="h-3.5 w-3.5" />
              </span>
              <span className="min-w-0 break-words">{texto}</span>
            </li>
          ))}
        </ul>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col justify-center gap-8 bg-app p-4 pb-safe pt-safe md:p-8">
        <div className="mx-auto flex w-full max-w-md flex-col gap-8">
          <p className="flex min-w-0 items-center gap-2 md:hidden">
            <span aria-hidden="true" className="flex min-h-10 min-w-10 shrink-0 items-center justify-center rounded-md bg-sidebar text-label text-on-dark">
              FD
            </span>
            <span className="break-words text-h3">FutData</span>
          </p>
          {children}
        </div>
      </main>
    </div>
  )
}
