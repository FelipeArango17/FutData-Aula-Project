import Inicio from './acudiente/Inicio.jsx'
import AcuJugadores from './acudiente/Jugadores.jsx'
import AcuFicha from './acudiente/Ficha.jsx'
import AcuPagos from './acudiente/Pagos.jsx'
import AcuDocumentos from './acudiente/Documentos.jsx'
import AcuHorarios from './acudiente/Horarios.jsx'
import AcuNotificaciones from './acudiente/Notificaciones.jsx'
import EntInicio from './entrenador/Inicio.jsx'
import EntAsistencia from './entrenador/Asistencia.jsx'
import EntHorarios from './entrenador/Horarios.jsx'
import EntNuevo from './entrenador/NuevoEntrenamiento.jsx'
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
import AdmHorarios from './administrador/Horarios.jsx'
import AdmTorneos from './administrador/Torneos.jsx'
import AdmUsuarios from './administrador/Usuarios.jsx'
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
  '/entrenador/asistencia': EntAsistencia,
  '/entrenador/horarios': EntHorarios,
  '/entrenador/torneos': EntTorneos,
  '/tesorero/inicio': TesInicio,
  '/tesorero/pagos': TesPagos,
  '/tesorero/reportes': TesReportes,
  '/tesorero/avisos': TesAvisos,
  '/administrador/dashboard': AdmDashboard,
  '/administrador/deportistas': AdmDeportistas,
  '/administrador/documentos': AdmDocumentos,
  '/administrador/usuarios': AdmUsuarios,
  '/administrador/asistencia': EntAsistencia, // mismo control de asistencia que el entrenador (Figma AD10)
  '/administrador/horarios': AdmHorarios,
  '/administrador/torneos': AdmTorneos,
  '/administrador/pagos': AdmPagos,
  '/administrador/reportes': AdmReportes,
  '/administrador/respaldo': AdmRespaldo,
}

// Pantallas que no están en el menú (se llega desde un botón).
export const extraScreens = {
  acudiente: [{ path: '/acudiente/jugadores/:id', Screen: AcuFicha }],
  entrenador: [{ path: '/entrenador/horarios/nuevo', Screen: EntNuevo }],
  tesorero: [{ path: '/tesorero/pagos/registrar', Screen: TesRegistrar }],
  administrador: [
    { path: '/administrador/deportistas/inscribir', Screen: AdmInscribir },
    { path: '/administrador/pagos/registrar', Screen: AdmRegistrarPago },
  ],
}
