# Convención de ramas

**Sprint:** Sprint 1  
**Proyecto:** FutData - Asociación Deportiva Prado Fútbol Club

## Objetivo

Definir una convención común para crear, nombrar, actualizar y cerrar ramas durante el desarrollo de FutData. El objetivo es mantener un historial claro y facilitar la revisión e integración de las tareas del sprint.

## Ramas principales

| Rama | Propósito | Base para nuevas ramas | Destino de los cambios |
| :--- | :--- | :--- | :--- |
| `main` | Código estable de producción y releases | No aplica | Recibe releases desde `develop` mediante Pull Request |
| `develop` | Integración del trabajo terminado del equipo | `main` o la versión vigente del proyecto | Recibe Pull Requests de las ramas de trabajo |

La rama `main` está protegida. No se debe hacer `push` directo; los cambios deben llegar mediante un Pull Request aprobado. La rama `develop` es la rama de integración diaria del equipo.

## Ramas de trabajo

Cada tarea debe desarrollarse en una rama propia creada a partir de `develop`. El nombre debe seguir este formato:

```text
<tipo>/<descripcion-corta>
```

### Tipos permitidos

| Tipo | Uso |
| :--- | :--- |
| `feature/` | Nueva funcionalidad o historia de usuario |
| `fix/` | Corrección de un error encontrado durante el desarrollo o las pruebas |
| `hotfix/` | Corrección urgente sobre una versión publicada |
| `docs/` | Documentación |
| `test/` | Creación o actualización de pruebas |
| `refactor/` | Reorganización del código sin cambiar su comportamiento |
| `chore/` | Configuración, dependencias o tareas de mantenimiento |

## Reglas para los nombres

- Usar minúsculas.
- Separar palabras con guiones (`-`), no con espacios ni guiones bajos.
- Usar una descripción breve y específica.
- Evitar nombres personales, fechas o términos genéricos como `cambios` o `pruebas`.
- Cuando exista un identificador de tarea, incluirlo en la descripción.
- No reutilizar una rama para tareas diferentes.

### Ejemplos

```text
feature/hu-001-registro-jugadores
feature/hu-002-control-asistencia
fix/validar-documentos-obligatorios
docs/convencion-ramas
test/servicio-inscripciones
chore/actualizar-dependencias
```

## Flujo de trabajo

### 1. Actualizar `develop`

Antes de crear una rama, comprobar que el árbol de trabajo esté limpio y traer la versión más reciente:

```bash
git switch develop
git pull --ff-only
```

Si existen cambios locales sin guardar, deben terminarse, confirmarse o guardarse temporalmente antes de actualizar la rama.

### 2. Crear la rama de la tarea

```bash
git switch -c feature/hu-001-registro-jugadores
```

La rama debe crearse desde `develop`, nunca desde otra rama de trabajo.

### 3. Trabajar y confirmar cambios

Realizar commits pequeños que representen cambios coherentes. Los commits deben seguir la convención definida en el repositorio:

```text
tipo(alcance): descripcion corta AB#<id>
```

Ejemplo:

```bash
git add .
git commit -m "feat(frontend): formulario de registro AB#301"
```

### 4. Mantener la rama actualizada

Antes de abrir el Pull Request, incorporar los cambios recientes de `develop`:

```bash
git switch develop
git pull --ff-only
git switch feature/hu-001-registro-jugadores
git merge develop
```

Resolver y probar cualquier conflicto antes de publicar el Pull Request.

### 5. Publicar y abrir el Pull Request

```bash
git push -u origin feature/hu-001-registro-jugadores
gh pr create --base develop --title "feat(frontend): formulario de registro AB#301"
```

El Pull Request debe tener como destino `develop`, incluir el identificador de la tarea de Azure Boards y describir:

- qué se implementó;
- cómo se verificó;
- qué riesgos o decisiones relevantes existen;
- capturas de pantalla, cuando el cambio afecte la interfaz.

### 6. Integrar y limpiar

Después de la revisión y del merge del Pull Request, eliminar la rama de trabajo local y remota cuando ya no sea necesaria:

```bash
git switch develop
git pull --ff-only
git branch -d feature/hu-001-registro-jugadores
git push origin --delete feature/hu-001-registro-jugadores
```

No se deben eliminar ramas que todavía tengan cambios pendientes o un Pull Request abierto.

## Reglas de revisión

- Todo cambio destinado a `main` debe entrar mediante Pull Request.
- Nadie debe aprobar su propio Pull Request.
- El autor del Pull Request realiza el merge después de obtener la aprobación correspondiente.
- Antes del merge, el autor debe comprobar que las pruebas y validaciones del cambio pasen correctamente.
- Los conflictos deben resolverse en la rama de trabajo, no directamente sobre `develop` o `main`.

## Resumen del flujo

```text
develop
   │
   └── feature/<tarea> ──► Pull Request ──► develop
                                              │
                                              └── release ──► main
```

