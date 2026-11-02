// Conversión entre el DTO de /api/deportistas (backend/README.md, "Deportistas") y la forma que usan las pantallas. Sin React ni red,
// para poder probarlo. Los ids del backend son UUID (texto): nunca se convierten a número.

// El formulario usa el nombre largo; la API usa el código (sin CC: es de adultos).
export const CODIGO_TIPO_DOCUMENTO = {
  'Tarjeta de identidad': 'TI',
  'Registro civil': 'RC',
  'Cédula de extranjería': 'CE',
  Pasaporte: 'PASAPORTE',
}
export const ETIQUETA_TIPO_DOCUMENTO = Object.fromEntries(Object.entries(CODIGO_TIPO_DOCUMENTO).map(([etiqueta, codigo]) => [codigo, etiqueta]))

export const ETIQUETA_ESTADO_INSCRIPCION = { COMPLETA: 'Completa', PENDIENTE: 'Pendiente' }

// DeportistaResponseDTO -> Deportista de las pantallas. Para el ENTRENADOR el backend no manda `documento` ni `estadoInscripcion`
// (ni la clave) y manda telefono y direccion en null: aquí `documento` y `estadoInscripcion` siguen ausentes (las pantallas no
// los dibujan) y el teléfono y la dirección quedan vacíos. `idCategoria` es el UUID de la categoría calculada (o null);
// `grupoEntrenamiento` sale de `grupo`.
export function adaptarDeportista(dto) {
  return {
    id: dto.id,
    nombre: dto.nombre,
    apellido: dto.apellido,
    tipoDocumento: ETIQUETA_TIPO_DOCUMENTO[dto.tipoDocumento] ?? dto.tipoDocumento,
    ...(dto.documento !== undefined && dto.documento !== null ? { documento: dto.documento } : {}),
    fechaNacimiento: dto.fechaNacimiento,
    edad: dto.edad,
    telefono: dto.telefono ?? '',
    direccion: dto.direccion ?? '',
    posicion: dto.posicion ?? null,
    foto: dto.foto ?? null,
    categoria: dto.categoria ?? null,
    idCategoria: dto.idCategoria ?? null,
    grupoEntrenamiento: dto.grupo ?? null,
    activo: dto.activo !== false,
    ...(dto.estadoInscripcion ? { estadoInscripcion: dto.estadoInscripcion } : {}),
  }
}

const recortar = (valor) => String(valor ?? '').trim()

// Valores del formulario -> DeportistaRequestDTO. `activo` solo va en el PUT (en el POST el backend lo ignora).
// La categoría NO se envía: la calcula el backend con la fecha de nacimiento.
export function cuerpoDeportista(valores, { activo } = {}) {
  const tipo = recortar(valores.tipoDocumento)
  return {
    nombre: recortar(valores.nombre),
    apellido: recortar(valores.apellido),
    tipoDocumento: CODIGO_TIPO_DOCUMENTO[tipo] ?? tipo,
    documento: recortar(valores.documento),
    fechaNacimiento: recortar(valores.fechaNacimiento),
    telefono: recortar(valores.telefono),
    direccion: recortar(valores.direccion),
    posicion: recortar(valores.posicion) || null,
    grupo: recortar(valores.grupoEntrenamiento) || null,
    ...(activo === undefined ? {} : { activo }),
  }
}

// Un deportista leído como valores de formulario (para comparar con lo que se va a guardar)
export const comoFormulario = (d) => ({
  nombre: d.nombre, apellido: d.apellido, tipoDocumento: d.tipoDocumento, documento: d.documento, fechaNacimiento: d.fechaNacimiento,
  telefono: d.telefono, direccion: d.direccion, posicion: d.posicion, grupoEntrenamiento: d.grupoEntrenamiento,
})

// Parámetros de GET /api/deportistas. estado: 'todos' | 'activos' | 'inactivos'; categoria: NOMBRE de la categoría.
export function parametrosLista({ pagina = 0, tamano, sort, estado = 'todos', categoria } = {}) {
  return {
    page: pagina,
    size: tamano,
    sort,
    activo: estado === 'activos' ? true : estado === 'inactivos' ? false : undefined,
    categoria: categoria || undefined,
  }
}

// Mismo cálculo que paginar() (utils/paginacion.js) para una página que ya viene recortada del servidor. `pagina` empieza en 1.
export function paginaDelServidor({ items, total, pagina, tamano }) {
  const totalPaginas = Math.max(1, Math.ceil(total / tamano))
  return { items, pagina, totalPaginas, desde: items.length === 0 ? 0 : (pagina - 1) * tamano + 1, hasta: (pagina - 1) * tamano + items.length, total }
}

const sinAcentos = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

// Búsqueda por nombre o documento. Si el rol no recibe el documento (entrenador), busca solo por nombre.
export function filtrarPorTexto(lista, texto) {
  const buscado = sinAcentos(texto)
  if (!buscado) return lista
  return lista.filter((d) => sinAcentos(`${d.nombre} ${d.apellido}`).includes(buscado) || sinAcentos(d.documento).includes(buscado))
}

// ¿Esta lista trae el documento? (el entrenador no lo recibe)
export const traeDocumento = (lista) => lista.some((d) => d.documento !== undefined)

// El backend manda `mensaje` como "campo: texto; campo: texto", pero algunos textos llevan "; " dentro (por ejemplo "por año de
// nacimiento tiene 17 años en 2026; el club solo inscribe de 4 a 16 años"). Solo se corta donde sigue un campo conocido de `campos`.
const capitalizar = (t) => `${t.charAt(0).toUpperCase()}${t.slice(1).replace(/\.$/, '')}.`
function textosPorCampo(mensaje, campos) {
  const texto = String(mensaje ?? '')
  const inicio = new RegExp(`(?:^|; )(?=(?:${campos.join('|')}): )`)
  const resultado = {}
  for (const parte of texto.split(inicio)) {
    const separador = parte.indexOf(': ')
    if (separador > 0 && campos.includes(parte.slice(0, separador))) resultado[parte.slice(0, separador)] = capitalizar(parte.slice(separador + 2))
  }
  return Object.fromEntries(campos.map((campo) => [campo, resultado[campo] ?? texto]))
}

// Campo del backend -> campo del formulario
const CAMPO_FORMULARIO = { grupo: 'grupoEntrenamiento' }

// Error de apiClient -> Error con el `codigo` que ya entienden las pantallas, conservando estado, mensaje y campos del backend:
//   400/409 con `campos` -> 'VALIDACION' (con `errores`: { campo: texto }); un 409 de documento -> 'DOCUMENTO_DUPLICADO'
//   403 -> 'SIN_PERMISO'; 404 -> 'DEPORTISTA_NO_EXISTE' (o 'CATEGORIA_NO_EXISTE' si se pide una categoría); lo demás queda igual.
// El texto de cada error es el del backend (nunca revela de quién es un documento repetido).
export function errorDeDeportistas(error, { recurso = 'deportista' } = {}) {
  if (!error || error.estado === undefined) return error
  const campos = Array.isArray(error.campos) ? error.campos : []
  if ((error.estado === 400 || error.estado === 409) && campos.length > 0) {
    const porCampo = textosPorCampo(error.mensaje ?? error.message, campos)
    const errores = Object.fromEntries(Object.entries(porCampo).map(([campo, texto]) => [CAMPO_FORMULARIO[campo] ?? campo, texto]))
    if (error.estado === 409 && errores.documento) return Object.assign(error, { codigo: 'DOCUMENTO_DUPLICADO', errores, message: errores.documento })
    return Object.assign(error, { codigo: 'VALIDACION', errores })
  }
  if (error.estado === 403) return Object.assign(error, { codigo: 'SIN_PERMISO' })
  if (error.estado === 404) return Object.assign(error, { codigo: recurso === 'categoria' ? 'CATEGORIA_NO_EXISTE' : 'DEPORTISTA_NO_EXISTE' })
  return error
}
