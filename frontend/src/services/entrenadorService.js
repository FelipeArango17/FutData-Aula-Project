// Datos reales del entrenador con sesión: su perfil (categorías), los jugadores de esas categorías y sus franjas del horario.
// Solo habla con la API; sin el módulo encendido las pantallas no llaman a nada (ver las funciones *PorApi).
//   Módulos: "inicioentrenador" (pantalla Inicio) y "misjugadores" (pantalla Mis jugadores) en VITE_API_MODULOS. En las pruebas
//   (MODE === 'test') son siempre false; las pruebas de la API simulan usarApi.js.
//   GET /api/usuarios/yo           -> entrenador: { idEntrenador, categorias: [{ idCategoria, nombre }] } (null si la cuenta no tiene entrenador)
//   GET /api/deportistas           -> ?categoria=<nombre>&activo=true&size=100&page=n (el entrenador lo puede leer completo)
//   GET /api/horario-club          -> franjas fijas de todo el club (se filtran aquí por las categorías del entrenador)
// El backend aún no limita a /api/deportistas por las categorías del entrenador: por eso se pide una categoría a la vez.
import { apiListaPaginada, apiRequest } from './apiClient.js'
import { usarApiDe } from './usarApi.js'
import { adaptarDeportista } from '../utils/deportistasApi.js'
import { adaptarFranja } from '../utils/franjasApi.js'
import { adaptarPerfilEntrenador, agruparPorCategoria, sesionesDelEntrenador } from '../utils/entrenadorApi.js'
import { hoy } from '../utils/hoy.js'

export const misJugadoresPorApi = () => usarApiDe('misjugadores')
export const inicioEntrenadorPorApi = () => usarApiDe('inicioentrenador')

const TAMANO_PAGINA = 100
const MAX_PAGINAS = 20 // tope de seguridad: 2000 jugadores por categoría

// Entrenador de la cuenta con sesión, o null si la cuenta no está vinculada a un registro de entrenador.
export async function perfilEntrenador() {
  return adaptarPerfilEntrenador(await apiRequest('/usuarios/yo'))
}

// Todos los jugadores activos de una categoría (recorre las páginas de 100).
async function jugadoresDeCategoria(nombre) {
  const jugadores = []
  for (let pagina = 0; pagina < MAX_PAGINAS; pagina += 1) {
    const resultado = await apiListaPaginada('/deportistas', { page: pagina, size: TAMANO_PAGINA, categoria: nombre, activo: true })
    jugadores.push(...resultado.items.map(adaptarDeportista))
    const tamano = resultado.tamano || TAMANO_PAGINA
    if (resultado.items.length === 0 || (pagina + 1) * tamano >= resultado.total) break
  }
  return jugadores
}

// { categorias: [{ id, nombre, jugadores }], jugadores } para las categorías dadas.
async function jugadoresDe(categorias) {
  const listas = await Promise.all(categorias.map((c) => jugadoresDeCategoria(c.nombre)))
  return agruparPorCategoria(categorias, listas)
}

// Pantalla "Mis jugadores": { perfil, categorias, jugadores }. perfil null = cuenta sin entrenador vinculado.
export async function cargarMisJugadores() {
  const perfil = await perfilEntrenador()
  if (!perfil) return { perfil: null, categorias: [], jugadores: [] }
  return { perfil, ...(await jugadoresDe(perfil.categorias)) }
}

// Pantalla "Inicio": el perfil es obligatorio; jugadores y horario fallan por separado ({ error }) para no perder el otro bloque.
export async function cargarInicioEntrenador({ fecha = hoy() } = {}) {
  const perfil = await perfilEntrenador()
  if (!perfil || perfil.categorias.length === 0) return { perfil, jugadores: { categorias: [], jugadores: [] }, horario: { hoy: [], semana: [] } }
  const [jugadores, franjas] = await Promise.allSettled([
    jugadoresDe(perfil.categorias),
    apiRequest('/horario-club').then((lista) => lista.map(adaptarFranja)),
  ])
  return {
    perfil,
    jugadores: jugadores.status === 'fulfilled' ? jugadores.value : { error: jugadores.reason },
    horario: franjas.status === 'fulfilled' ? sesionesDelEntrenador(franjas.value, perfil.categorias, fecha) : { error: franjas.reason },
  }
}
