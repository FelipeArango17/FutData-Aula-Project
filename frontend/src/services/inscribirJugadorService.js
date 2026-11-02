// Inscribir un jugador de principio a fin (el asistente de 4 pasos): crea el deportista, guarda a su acudiente y lo vincula,
// registra los documentos y guarda la foto. Por ahora orquesta los servicios en memoria; está pensado para cambiarlo luego por UN
// solo POST al backend (con el deportista, el acudiente, los documentos y la foto) sin tocar la pantalla.
//
// inscribirJugador({ deportista, acudiente, documentos: [{ tipo, archivo }], foto }, actor) -> resultado
//   deportista  los valores del formulario (nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, direccion,
//               grupoEntrenamiento, posicion). La categoría NO se envía: se calcula aquí con la fecha de nacimiento.
//   acudiente   { nombre, apellido, correo, telefono, parentesco }
//   documentos  los archivos adjuntos (cualquiera de los 7 tipos); foto: File de la foto de perfil o null
//   resultado   { deportista, categoria, acudiente, vinculo, documentos, foto }  (foto: true si se guardó)
//
// Errores: Error con `codigo` y `paso` (1 a 3, el paso del asistente donde se corrige) ->
//   'SIN_PERMISO' | 'VALIDACION' (con `errores`: { campo: mensaje }, los del paso) | 'DOCUMENTO_DUPLICADO' (paso 1, `errores.documento`) |
//   'CATEGORIA_NO_CORRESPONDE_EDAD' / 'CATEGORIA_NO_VIGENTE' / 'CATEGORIA_NO_EXISTE' (paso 1, `errores.fechaNacimiento`) |
//   'INSCRIPCION_PARCIAL' (el deportista YA se creó pero falló el acudiente, un documento o la foto; trae `deportista` para ir a su ficha
//   y terminar desde allí). Las validaciones se hacen TODAS antes de crear nada, así que casi siempre un error no deja nada guardado.
//
// PENDIENTE (backend): crear la cuenta de acceso del acudiente y enviarle un correo con un enlace para crear su contraseña, igual que a
// los entrenadores; subir los archivos a Supabase Storage; y hacer todo en una sola transacción. Hoy la cuenta no se crea.
import { crearDeportista, deportistasPorApi, listarCategorias, vincularAcudiente as vincularCorreo } from './deportistasService.js'
import { guardarAcudiente, vincularAcudiente } from './acudientesService.js'
import { subirDocumento, validarDatosDocumento } from './documentosService.js'
import { guardarFoto } from './fotosService.js'
import { validarPaso } from './asistenteInscripcion.js'
import { idCategoriaAlGuardar } from '../utils/categorias.js'

const error = (codigo, mensaje, extra) => Object.assign(new Error(mensaje), { codigo }, extra)

// Errores de la crearDeportista que se corrigen en el paso 1, con el campo que los muestra
const ERROR_DE_PASO_1 = {
  DOCUMENTO_DUPLICADO: 'documento',
  CATEGORIA_NO_CORRESPONDE_EDAD: 'fechaNacimiento',
  CATEGORIA_NO_VIGENTE: 'fechaNacimiento',
  CATEGORIA_NO_EXISTE: 'fechaNacimiento',
}

export async function inscribirJugador({ deportista, acudiente, documentos = [], foto = null }, actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para inscribir jugadores.')

  // 1. Todo se valida antes de crear nada
  const porTipo = Object.fromEntries(documentos.map((d) => [d.tipo, d.archivo]))
  const datos = { jugador: deportista, acudiente, documentos: porTipo, foto }
  for (const paso of [1, 2]) {
    const errores = validarPaso(paso, datos)
    if (Object.keys(errores).length > 0) throw error('VALIDACION', 'Hay datos que corregir.', { errores, paso })
  }
  const erroresDocs = {}
  for (const { tipo, archivo } of documentos) {
    const e = validarDatosDocumento({ tipo, archivo })
    if (e.tipo) erroresDocs[`documento-${tipo}`] = e.tipo
    else if (e.archivo) erroresDocs[`documento-${tipo}`] = e.archivo
  }
  const erroresPaso3 = { ...validarPaso(3, datos), ...erroresDocs }
  if (Object.keys(erroresPaso3).length > 0) throw error('VALIDACION', 'Hay datos que corregir.', { errores: erroresPaso3, paso: 3 })

  // 2. Deportista, con la categoría que da su fecha de nacimiento
  const categorias = await listarCategorias()
  const idCategoria = idCategoriaAlGuardar(deportista.fechaNacimiento, null, categorias)
  let creado
  try {
    creado = await crearDeportista({ ...deportista, idCategoria }, actor)
  } catch (e) {
    const campo = ERROR_DE_PASO_1[e.codigo]
    if (campo) throw error(e.codigo, e.message, { errores: { [campo]: e.message }, paso: 1 })
    if (e.codigo === 'VALIDACION') throw error('VALIDACION', e.message, { errores: e.errores, paso: 1 })
    throw e
  }

  // Con la API de deportistas el backend solo guarda al deportista y su foto (todavía no hay endpoints de acudientes ni de
  // documentos): se guarda eso y se avisa que el resto no se pudo hacer (INSCRIPCION_PARCIAL), en vez de perder esos datos en silencio.
  if (deportistasPorApi()) {
    if (foto) await guardarFoto(creado.id, foto, actor).catch(() => {})
    throw error('INSCRIPCION_PARCIAL', 'El jugador se registró, pero no se pudo terminar la inscripción. Complétala desde su ficha.', { deportista: creado, causa: 'NO_DISPONIBLE' })
  }

  // 3. Acudiente, vínculo, documentos y foto: si algo falla, el deportista ya existe
  try {
    const { acudiente: guardado } = await guardarAcudiente(acudiente, actor)
    const vinculo = await vincularAcudiente(creado.id, guardado.id, acudiente.parentesco, actor)
    await vincularCorreo(creado.id, guardado.correo, actor)
    const subidos = []
    for (const { tipo, archivo } of documentos) subidos.push(await subirDocumento(creado.id, { tipo, archivo }, actor))
    if (foto) await guardarFoto(creado.id, foto, actor)
    const categoria = categorias.find((c) => c.id === creado.idCategoria)?.nombre ?? null
    return { deportista: { ...creado, acudiente: guardado.correo }, categoria, acudiente: guardado, vinculo, documentos: subidos, foto: Boolean(foto) }
  } catch (e) {
    throw error('INSCRIPCION_PARCIAL', 'El jugador se registró, pero no se pudo terminar la inscripción. Complétala desde su ficha.', { deportista: creado, causa: e.codigo })
  }
}
