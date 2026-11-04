// "Hola, María" con el nombre real de la persona autenticada; sin nombre (sesión sin verificar o sin datos) solo "Hola".
export function saludoDe(usuario) {
  const nombre = String(usuario?.nombre ?? '').trim()
  return nombre ? `Hola, ${nombre}` : 'Hola'
}

// "Resumen del club · noviembre 2026" a partir de una fecha AAAA-MM-DD
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
export const mesYAnio = (fecha) => `${MESES[Number(fecha.slice(5, 7)) - 1]} ${fecha.slice(0, 4)}`
