const RUTAS_PROPIAS = ['/restablecer', '/recuperar']

// Qué hacer con la redirección del enlace del correo en la ruta actual. Pura, para poder probarla sin DOM.
//   activo    la página se cargó desde un enlace del correo (invitación, recuperación o vencido)
//   consumida ya se decidió una vez (la redirección ocurre SOLO en la carga inicial)
// Devuelve { consumida, destino }: destino es la ruta a la que ir (o null). Si la entrada ya es /restablecer o /recuperar
// no redirige, pero igual marca la redirección como consumida.
export function decidirRedireccionEnlace({ activo, consumida, pathname }) {
  if (!activo || consumida) return { consumida, destino: null }
  return { consumida: true, destino: RUTAS_PROPIAS.includes(pathname) ? null : '/restablecer' }
}

