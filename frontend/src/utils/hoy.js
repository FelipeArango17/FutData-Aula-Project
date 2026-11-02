// "Hoy" de la aplicación. En las pruebas (MODE === 'test') es siempre HOY_EJEMPLO, para que sean deterministas; en cualquier otro
// caso es la fecha real local. Llamar a hoy() en el momento de usarlo (no guardarlo al cargar el módulo).
import { hoyLocal } from './fechas.js'

// Día "de hoy" del mockup de Figma; con él los datos de ejemplo caen en la semana del 19 al 24 de octubre de 2026.
export const HOY_EJEMPLO = '2026-10-22'

export const hoy = () => (import.meta.env.MODE === 'test' ? HOY_EJEMPLO : hoyLocal())
