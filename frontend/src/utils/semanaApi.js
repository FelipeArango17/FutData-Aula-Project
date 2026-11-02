// Punto de extensión ÚNICO del módulo "entrenamientos" del backend (bloque 4B de Omar: excepciones por fecha).
//
// PENDIENTE 4B: contrato de Omar (GET /api/horario-semana?fecha=AAAA-MM-DD propuesto; excepciones CANCELADO/EXTRA).
// No hay rutas ni forma de respuesta acordadas, así que NO se inventan: mientras tanto la semana se arma solo con las franjas
// (GET /api/horario-club), sin excepciones, y cancelar y crear extras quedan ocultos en la pantalla.
// Cuando exista el contrato: (1) devolver aquí las excepciones de la semana con la forma `Excepcion` de utils/semana.js
// y (2) poner las capacidades que el backend ofrezca. Nada más cambia.

// Excepciones (cancelaciones y extras) de la semana que empieza en `lunes`
// eslint-disable-next-line no-unused-vars
export async function excepcionesDeSemanaApi(lunes) {
  return []
}

// Qué acciones sobre las fechas ofrece el backend hoy
export const capacidadesApi = () => ({ cancelar: false, extras: false })
