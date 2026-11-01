// Lógica pura del asistente "Inscribir jugador" (4 pasos): estado, validación por paso y un reductor para que los datos se
// conserven al ir y volver. Sin red ni React. Reutiliza las validaciones de los servicios (no duplica reglas):
//   paso 1 -> validarDatosDeportista (deportistasService), paso 2 -> validarDatosAcudiente (acudientesService),
//   paso 3 -> validarArchivo (utils/archivos.js), paso 4 -> sin campos (solo confirma).
import { DEPORTISTA_VACIO, aplicarCambio, validarDatosDeportista } from './deportistasService.js'
import { ACUDIENTE_VACIO, validarDatosAcudiente } from './acudientesService.js'
import { IDS_TIPOS_DOCUMENTO } from '../utils/documentosClub.js'
import { TIPOS_DOCUMENTO, TIPOS_FOTO, validarArchivo } from '../utils/archivos.js'

export const PASOS = ['Jugador', 'Acudiente', 'Documentos', 'Confirmar']
export const ULTIMO_PASO = PASOS.length
export const CLAVE_FOTO = 'foto' // clave del error de la foto en el paso 3; los documentos usan `documento-${tipo}`

export const ESTADO_INICIAL = {
  paso: 1,
  jugador: DEPORTISTA_VACIO,
  acudiente: ACUDIENTE_VACIO,
  documentos: {}, // { [idTipo]: File } los archivos quedan aquí hasta confirmar
  foto: null, // File de la foto de perfil (la "Foto tipo documento")
  errores: {},
  intentos: 0, // sube con cada intento fallido de continuar: la pantalla manda el foco al primer error
}

// Errores del paso, { campo: mensaje } (vacío si todo está bien). El paso 3 revisa los archivos ya elegidos.
export function validarPaso(paso, { jugador, acudiente, documentos, foto }) {
  if (paso === 1) return validarDatosDeportista(jugador)
  if (paso === 2) return validarDatosAcudiente(acudiente)
  if (paso === 3) {
    const errores = {}
    for (const tipo of IDS_TIPOS_DOCUMENTO) {
      const motivo = documentos[tipo] ? validarArchivo(documentos[tipo], { tipos: TIPOS_DOCUMENTO }) : null
      if (motivo) errores[`documento-${tipo}`] = motivo
    }
    const motivoFoto = foto ? validarArchivo(foto, { tipos: TIPOS_FOTO }) : null
    if (motivoFoto) errores[CLAVE_FOTO] = motivoFoto
    return errores
  }
  return {}
}

// Primer paso (1 a 3) con errores, o null si todos están bien
export function primerPasoConErrores(datos) {
  return [1, 2, 3].find((paso) => Object.keys(validarPaso(paso, datos)).length > 0) ?? null
}

// Reductor del asistente. Acciones:
//   { tipo: 'jugador', campo, valor } | { tipo: 'acudiente', campo, valor }  -> cambia un campo y quita su error
//   { tipo: 'documento', idTipo, archivo } (archivo null lo quita) | { tipo: 'foto', archivo }
//   { tipo: 'continuar' }  -> valida el paso: con errores se queda y los guarda; sin errores avanza
//   { tipo: 'atras' }      -> retrocede un paso sin perder nada
//   { tipo: 'ir', paso }   -> salta a un paso ANTERIOR o al mismo (los "Editar" del resumen); no se salta pasos hacia adelante
//   { tipo: 'errorDelServidor', paso, errores } -> vuelve al paso del error con sus mensajes
export function reducirAsistente(estado, accion) {
  switch (accion.tipo) {
    case 'jugador': {
      const { [accion.campo]: _quitado, ...errores } = estado.errores // eslint-disable-line no-unused-vars
      return { ...estado, jugador: aplicarCambio(estado.jugador, accion.campo, accion.valor), errores }
    }
    case 'acudiente': {
      const { [accion.campo]: _quitado, ...errores } = estado.errores // eslint-disable-line no-unused-vars
      return { ...estado, acudiente: { ...estado.acudiente, [accion.campo]: accion.valor }, errores }
    }
    case 'documento': {
      // Un archivo inválido (tipo, vacío o de más de 5 MB) no se guarda: se muestra su error en esa fila al instante
      const clave = `documento-${accion.idTipo}`
      const documentos = { ...estado.documentos }
      const { [clave]: _quitado, ...errores } = estado.errores // eslint-disable-line no-unused-vars
      const motivo = accion.archivo ? validarArchivo(accion.archivo, { tipos: TIPOS_DOCUMENTO }) : null
      if (accion.archivo && !motivo) documentos[accion.idTipo] = accion.archivo
      else delete documentos[accion.idTipo]
      return { ...estado, documentos, errores: motivo ? { ...errores, [clave]: motivo } : errores }
    }
    case 'foto': {
      const { [CLAVE_FOTO]: _quitado, ...errores } = estado.errores // eslint-disable-line no-unused-vars
      return { ...estado, foto: accion.archivo ?? null, errores }
    }
    case 'continuar': {
      const errores = validarPaso(estado.paso, estado)
      if (Object.keys(errores).length > 0) return { ...estado, errores, intentos: estado.intentos + 1 }
      return { ...estado, errores: {}, paso: Math.min(estado.paso + 1, ULTIMO_PASO) }
    }
    case 'atras':
      return { ...estado, errores: {}, paso: Math.max(estado.paso - 1, 1) }
    case 'ir':
      return accion.paso <= estado.paso ? { ...estado, errores: {}, paso: Math.max(accion.paso, 1) } : estado
    case 'errorDelServidor':
      return { ...estado, paso: accion.paso, errores: accion.errores, intentos: estado.intentos + 1 }
    default:
      return estado
  }
}
