# Diagramas de clases · FutData (HU-066 / tarea 295)

Diagramas del backend (Spring Boot) con los patrones **Fachada**, **DAO** y **DTO**, alineados con el numeral 4.2 del PPI.

| Figura | Archivo | Contenido |
|---|---|---|
| 1 | `00-patrones-fig.png` | Estructura base: controlador, DTO, fachada, DAO, fuente de datos |
| 2 | `01-dominio-fig.png` | Modelo de dominio |
| 3 | `02-pagos-fig.png` | Pagos de mensualidades (CU-02) |
| 4 | `03-autenticacion-fig.png` | Autenticación 2FA por correo |
| 5 | `04-inscripcion-fig.png` | Inscripción (CU-01, CU-04) |
| 6 | `05-asistencia-fig.png` | Asistencia (CU-03) |

Los `.svg` son la versión vectorial. En `fuente/` están los `.dot` y el script generador.

## Regenerar
Requiere Graphviz y Python 3: `python3 fuente/generar_diagramas.py salida/`

## Colores por capa
Controlador (azul) · DTO (amarillo) · Fachada (verde) · Reglas de dominio (rojo) · Entidad (gris) · DAO (morado) · Servicios externos (turquesa).
