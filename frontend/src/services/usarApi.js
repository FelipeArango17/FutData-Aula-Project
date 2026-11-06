// Interruptor por módulo: decide si un servicio habla con la API real o usa sus datos de ejemplo.
// VITE_API_MODULOS = lista separada por comas ("entrenadores,deportistas"). Sin la variable vale "entrenadores" (el único módulo
// conectado hoy); con la variable vacía ningún módulo usa la API. En las pruebas (MODE === 'test') siempre es false.
export const MODULOS_PREVISTOS = [
  'entrenadores', 'deportistas', 'documentos', 'inscripcion', 'horarioclub', 'entrenamientos',
  'pagos', 'grupos', 'asistencia', 'torneos', 'avisos', 'reportes', 'usuarios', 'inicioentrenador', 'misjugadores', 'dashboard', 'iniciotesorero', 'inicioacudiente', 'acudientes', 'respaldo', 'inscripcionpublica',
]

export const MODULOS_POR_DEFECTO = 'entrenadores'

export function modulosConApi(valor = import.meta.env.VITE_API_MODULOS) {
  return String(valor ?? MODULOS_POR_DEFECTO).split(',').map((m) => m.trim().toLowerCase()).filter(Boolean)
}

export function usarApiDe(modulo, { modo = import.meta.env.MODE, modulos = import.meta.env.VITE_API_MODULOS } = {}) {
  return modo !== 'test' && modulosConApi(modulos).includes(String(modulo).toLowerCase())
}
