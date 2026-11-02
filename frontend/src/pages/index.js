import Inicio from './acudiente/Inicio.jsx'
import AcuJugadores from './acudiente/Jugadores.jsx'
import AcuFicha from './acudiente/Ficha.jsx'
import AcuPagos from './acudiente/Pagos.jsx'
import AcuDocumentos from './acudiente/Documentos.jsx'
import AcuHorarios from './acudiente/Horarios.jsx'
import AcuNotificaciones from './acudiente/Notificaciones.jsx'
import EntInicio from './entrenador/Inicio.jsx'
import EntAsistencia from './entrenador/Asistencia.jsx'
import ListaPorGrupo from './deportistas/ListaPorGrupo.jsx'
import RedirigirAInscribir from './RedirigirAInscribir.jsx'
import RedirigirAHorarios from './RedirigirAHorarios.jsx'

import FichaDeportista from './deportistas/FichaDeportista.jsx'
import Horarios from './horarios/Horarios.jsx'
import EntTorneos from './entrenador/Torneos.jsx'
import TesInicio from './tesorero/Inicio.jsx'
import TesPagos from './tesorero/Pagos.jsx'
import TesRegistrar from './tesorero/RegistrarPago.jsx'
import TesReportes from './tesorero/Reportes.jsx'
import TesAvisos from './tesorero/Avisos.jsx'
import AdmDashboard from './administrador/Dashboard.jsx'
import AdmDeportistas from './administrador/Deportistas.jsx'
import AdmInscribir from './administrador/Inscribir.jsx'
import AdmDocumentos from './administrador/Documentos.jsx'
import AdmTorneos from './administrador/Torneos.jsx'
import AdmUsuarios from './administrador/Usuarios.jsx'
import AdmUsuarioNuevo from './administrador/UsuarioNuevo.jsx'
import AdmUsuarioDetalle from './administrador/UsuarioDetalle.jsx'
import AdmEntrenadores from './administrador/Entrenadores.jsx'
import AdmEntrenadorFormulario from './administrador/EntrenadorFormulario.jsx'
import AdmRespaldo from './administrador/Respaldo.jsx'
import AdmReportes from './administrador/Reportes.jsx'
import AdmPagos from './administrador/Pagos.jsx'
import AdmRegistrarPago from './administrador/RegistrarPago.jsx'

// Pantalla de cada opción del menú. Lo que no está aquí muestra la pantalla provisional.
export const screens = {
  '/acudiente/inicio': Inicio,
  '/acudiente/jugadores': AcuJugadores,
  '/acudiente/pagos': AcuPagos,
  '/acudiente/documentos': AcuDocumentos,
  '/acudiente/horarios': AcuHorarios,
  '/acudiente/notificaciones': AcuNotificaciones,
  '/entrenador/inicio': EntInicio,
  '/entrenador/grupos': ListaPorGrupo, // misma pantalla para entrenador y administrador (HU-011)
  '/entrenador/asistencia': EntAsistencia,
  '/entrenador/horarios': Horarios, // misma pantalla para acudiente, entrenador y administrador
  '/entrenador/torneos': EntTorneos,
  '/tesorero/inicio': TesInicio,
  '/tesorero/pagos': TesPagos,
  '/tesorero/reportes': TesReportes,
  '/tesorero/avisos': TesAvisos,
  '/administrador/dashboard': AdmDashboard,
  '/administrador/deportistas': AdmDeportistas,
  '/administrador/documentos': AdmDocumentos,
  '/administrador/usuarios': AdmUsuarios,
  '/administrador/grupos': ListaPorGrupo,
  '/administrador/asistencia': EntAsistencia, // mismo control de asistencia que el entrenador (Figma AD10)
  '/administrador/horarios': Horarios,
  '/administrador/torneos': AdmTorneos,
  '/administrador/pagos': AdmPagos,
  '/administrador/reportes': AdmReportes,
  '/administrador/respaldo': AdmRespaldo,
}

// Pantallas que no están en el menú (se llega desde un botón).
export const extraScreens = {
  acudiente: [{ path: '/acudiente/jugadores/:id', Screen: AcuFicha }],
  tesorero: [{ path: '/tesorero/pagos/registrar', Screen: TesRegistrar }],
  administrador: [
    { path: '/administrador/horario-club', Screen: RedirigirAHorarios }, // enlaces viejos: ahora es una sola pantalla de horarios
    // Rutas fijas de usuarios antes de /:id, para que :id no capture "nuevo" ni "entrenadores"
    { path: '/administrador/usuarios/nuevo', Screen: AdmUsuarioNuevo },
    { path: '/administrador/usuarios/entrenadores', Screen: AdmEntrenadores },
    { path: '/administrador/usuarios/entrenadores/nuevo', Screen: AdmEntrenadorFormulario },
    { path: '/administrador/usuarios/entrenadores/:id/editar', Screen: AdmEntrenadorFormulario },
    { path: '/administrador/usuarios/:id', Screen: AdmUsuarioDetalle },
    { path: '/administrador/deportistas/inscribir', Screen: AdmInscribir },
    { path: '/administrador/deportistas/nuevo', Screen: RedirigirAInscribir }, // enlaces viejos: ya hay un solo flujo
    { path: '/administrador/deportistas/:id', Screen: FichaDeportista }, // después de /nuevo e /inscribir (rutas fijas)
    { path: '/administrador/pagos/registrar', Screen: AdmRegistrarPago },
  ],
}
