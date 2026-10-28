import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import Card from '../../components/ui/Card.jsx'

// PENDIENTE: recuperación de contraseña (Supabase Auth). Por ahora es solo un marcador.
export default function Recuperar() {
  const titulo = useRef(null)
  useEffect(() => titulo.current?.focus(), [])

  return (
    <AuthLayout variante="centrado">
      <Card className="flex flex-col gap-4 p-6">
        <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Recuperar contraseña</h1>
        <p className="break-words text-body text-secondary">Próximamente.</p>
        <Link to="/login" className={claseEnlace}>Volver al inicio de sesión</Link>
      </Card>
    </AuthLayout>
  )
}
