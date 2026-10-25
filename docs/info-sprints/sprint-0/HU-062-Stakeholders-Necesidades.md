# HU-062 · Mapear stakeholders y levantar necesidades

**Sprint:** Sprint 0  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club  
**Responsables:** Omar Támara, Juan Sebastián Múnera Bedoya, Felipe Arango Espinosa  

---

## Task 1: Identificar actores y roles del club

Mapeo de los actores organizacionales del club y su correspondencia con los 4 roles técnicos definidos en la arquitectura de FutData:

| Stakeholder Organizacional | Nivel Organizacional | Rol en FutData | Responsabilidad e Interacción Principal |
| :--- | :--- | :--- | :--- |
| **Administrador / Secretaría** | Directivo / Administrativo | `ADMIN` | Gestión integral del club, inscripción de deportistas, validación documental, control de usuarios/roles y respaldos. |
| **Coordinador Deportivo** | Táctico / Deportivo | `ADMIN` | Administración de entrenadores, asignación de horarios, creación de categorías y registro en torneos. |
| **Tesorero** | Administrativo / Financiero | `TESORERO` | Control de pagos y mensualidades, registro de pagos manuales, estados de cuenta y reportes financieros en PDF/Excel. |
| **Entrenadores** | Operativo | `ENTRENADOR` | Consulta de grupos asignados, programación de sesiones y toma digital de asistencia diaria desde el celular. |
| **Padres de familia / Acudientes y Jugadores** | Usuario final (Externo) | `ACUDIENTE` | Inscripción en línea, carga de documentos y ficha médica, pago de mensualidades por Wompi y recepción de notificaciones. |
| **Equipo Técnico de Desarrollo** | Técnico (Proyecto) | *N/A (Soporte)* | Construcción modular del sistema, mantenimiento, despliegue en Docker y aseguramiento de disponibilidad. |

---

## Task 2: Levantar necesidades con Design Thinking

### 1. Fase Empatizar (Dolores y frustraciones actuales)
* **Administración y Secretaría:** Uso excesivo de papel, pérdida de documentos físicos (fichas médicas y permisos), duplicidad de tareas al transcribir hojas de inscripción y falta de trazabilidad sobre modificaciones.
* **Tesorería y Dirección:** Demoras en la conciliación de mensualidades, desconocimiento en tiempo real de la cartera morosa y dificultad para consolidar balances mensuales confiables.
* **Coordinación y Entrenadores:** Listas de asistencia en papel que se deterioran o extravían en cancha, cruces de horarios de entrenamiento y comunicación informal por canales dispersos.
* **Padres de Familia y Deportistas:** Desconocimiento de fechas de corte o saldos pendientes, trámites presenciales lentos para matricular al menor y falta de comprobantes digitales de pago.

### 2. Fase Definir (Insights / Necesidades clave)
* **Centralización documental y deportiva:** Reemplazar los archivos físicos dispersos por un expediente digital único por deportista con almacenamiento seguro en Supabase Storage.
* **Automatización financiera:** Implementar un flujo de cobro preventivo (alertas automáticas vía n8n) y pagos en línea (Wompi) con actualización inmediata del estado de cuenta.
* **Movilidad y accesibilidad:** Proveer una plataforma responsive (React + Tailwind) operable desde celulares en cancha, con módulo de accesibilidad visual (WCAG 2.1 AA).

---

## Task 3: Registro de necesidades (N001 - NNF005)
*El registro formal de las necesidades N001 a N008 y NNF001 a NNF005 se encuentra consolidado en el numeral **1.6. Necesidades** del documento oficial del PPI