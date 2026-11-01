// Pantalla de espera mientras se comprueba la sesión: así el panel no aparece un instante antes de redirigir al login.
export default function EsperaSesion() {
  return (
    <div role="status" aria-live="polite" className="flex min-h-app items-center justify-center bg-app p-4">
      <p className="break-words text-center text-body text-secondary">Verificando tu sesión…</p>
    </div>
  )
}
