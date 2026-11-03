// Traducción entre el usuario del backend (UsuarioRequestDTO / UsuarioResponseDTO de /api/usuarios) y el que usan las pantallas.
// Funciones puras, sin red. Ver backend/README.md, sección "Usuarios y roles".
//
//   Usuario (pantalla) = { id, nombre, apellido, correo, rol, activo, creadoPor, creadoEn }
//   - La API manda el rol en minúsculas (UsuarioMapper) y acepta el del cuerpo en cualquier caso (el servicio lo pasa a
//     mayúsculas); aquí se envía en MAYÚSCULAS, como el rol de Spring Security, y se lee siempre en minúsculas.
//   - La API NO guarda quién creó la cuenta ni un historial de cambios: creadoPor es siempre null.
import { erroresPorCampo } from './erroresApi.js'

export const MENSAJE_CORREO_DUPLICADO = 'Ya existe un usuario con ese correo.'

const minusculas = (rol) => String(rol ?? '').trim().toLowerCase()
const mayusculas = (rol) => String(rol ?? '').trim().toUpperCase()

export function adaptarUsuario(dto) {
  return {
    id: dto.id,
    nombre: dto.nombre,
    apellido: dto.apellido,
    correo: dto.correo,
    rol: minusculas(dto.rol),
    activo: dto.activo !== false,
    creadoPor: null,
    creadoEn: dto.creadoEn ?? null,
  }
}

// Cuerpo de POST /usuarios: los cuatro campos, limpios y con el rol en mayúsculas.
export function cuerpoUsuario(datos) {
  return {
    nombre: String(datos.nombre ?? '').trim(),
    apellido: String(datos.apellido ?? '').trim(),
    correo: String(datos.correo ?? '').trim(),
    rol: mayusculas(datos.rol),
  }
}

// Cuerpo de PUT /usuarios/{id}/rol: la API lee el rol del mismo DTO.
export const cuerpoRol = (rol) => ({ rol: mayusculas(rol) })

// Convierte el error de apiRequest en uno que las pantallas ya entienden (`codigo`, `message`, `errores`):
//   400/409 con campos -> 'VALIDACION' y errores { campo: texto } (correo duplicado con texto propio)
//   409 sin campos -> 'CONFLICTO'; 404 -> 'USUARIO_NO_EXISTE'; 403 -> 'SIN_PERMISO'; lo demás conserva su código (RED, etc.).
// `message` es siempre el mensaje del backend.
export function errorDeUsuarios(error) {
  const estado = error?.estado
  if ((estado === 400 || estado === 409) && Array.isArray(error.campos) && error.campos.length > 0) {
    error.errores = erroresPorCampo(error, { duplicados: { correo: MENSAJE_CORREO_DUPLICADO } })
    error.codigo = 'VALIDACION'
  } else if (estado === 409) error.codigo = 'CONFLICTO'
  else if (estado === 404) error.codigo = 'USUARIO_NO_EXISTE'
  else if (estado === 403) error.codigo = 'SIN_PERMISO'
  return error
}
