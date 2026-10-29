# FutData

Plataforma web para la gestión integral de un club de fútbol formativo: jugadores, categorías, entrenadores, asistencias, pagos, agenda de entrenamientos y panel administrativo.

Proyecto de aula para la Asociación Deportiva Prado Fútbol Club. Equipo: Juan Sebastián (Scrum Master), Felipe y Omar. El seguimiento de tareas está en Azure DevOps (proyecto FutData).

> **Dónde ver el trabajo más reciente:** el trabajo diario vive en la rama `develop`. La rama `main` solo se actualiza en cada release, así que puede verse desactualizada. Para ver lo último en GitHub, cambia a `develop` en el selector de ramas.

---

## 1. Estructura del repositorio

Monorepo: todo el proyecto vive en un solo repositorio.

| Carpeta | Contenido |
|---|---|
| `frontend/` | Aplicación React (Vite + Tailwind CSS) |
| `backend/` | API REST en Java con Spring Boot |
| `docs/` | Documentación. `docs/design-system/` tiene los tokens y las equivalencias de Figma a Tailwind |
| `.claude/skills/` | Skills de Claude compartidas por el equipo |

## 2. Stack

| Parte | Tecnología |
|---|---|
| Frontend | React, Vite, Tailwind CSS 3, Axios. Se publica en Vercel |
| Backend | Java 21, Spring Boot 3, Maven. Se entrega como `.jar` en un contenedor Docker |
| Base de datos, archivos y autenticación | Supabase (PostgreSQL, Storage, Auth) |
| Automatización | n8n en un contenedor Docker (correos del 2FA, recordatorios) |
| Pagos | Wompi |

Arquitectura, casos de uso y requisitos: ver el documento PPI.

---

## 3. Flujo de ramas

```
main      ← producción. Protegida. Solo recibe releases desde develop.
develop   ← integración. Aquí se juntan las tareas terminadas.
feature/* ← una rama por tarea. Sale de develop y vuelve a develop.
```

**Reglas de `main`** (configuradas en GitHub, nadie puede saltárselas):

- No se puede hacer `push` directo a `main`.
- Todo cambio entra por Pull Request con **1 aprobación**.
- No se permite force push ni borrar la rama.
- **Nadie aprueba su propio PR.** Los PR de Felipe y Omar los aprueba Juan (Scrum Master). Los PR de Juan los aprueba Omar o Felipe.
- Quien abre el PR es quien hace el merge, una vez aprobado.

`develop` no está protegida: el equipo hace merge de las tareas terminadas sin aprobación obligatoria, pero cada quien prueba su trabajo antes de subirlo.

## 4. Convención de commits

```
tipo(alcance): descripción corta AB#<ID de la tarea en Azure>
```

- **tipo:** `feat` (funcionalidad nueva), `fix` (corrección), `docs`, `chore` (configuración), `refactor`, `test`, `style`.
- **alcance:** la parte afectada (`frontend`, `backend`, `repo`, `design-system`...).
- **AB#ID:** el número del work item de Azure Boards. Al escribirlo, Azure enlaza el commit y el PR con esa tarea (aparecen en su sección *Development*). El ID es el número de la **tarea**, no el del commit.

Ejemplos:

```
feat(frontend): pantalla de inicio de sesión AB#301
docs(design-system): equivalencias de Figma a Tailwind AB#287
chore(repo): estructura inicial del monorepo AB#288
```

---

## 5. Cómo trabajar una tarea, paso a paso

Ejemplo con una tarea ficticia `AB#999`. Cambia el nombre de la rama y el ID por los tuyos.

### Paso 1. Actualiza `develop` y crea tu rama

```bash
git switch develop          # ir a develop
git pull                    # traer lo último
git switch -c feature/nombre-corto-de-la-tarea
```

`git switch -c` crea la rama nueva y se pasa a ella. Si no haces `git pull` antes, tu rama nace desactualizada y luego aparecen conflictos.

### Paso 2. Trabaja y haz commits

```bash
git status                  # ver qué cambió
git add .                   # preparar los cambios
git commit -m "feat(frontend): descripcion AB#999"
```

Haz commits pequeños y frecuentes en lugar de uno gigante al final.

### Paso 3. Sube tu rama y abre el Pull Request hacia `develop`

```bash
git push -u origin feature/nombre-corto-de-la-tarea
gh pr create --base develop --title "feat(frontend): descripcion AB#999" --body "Qué se hizo y cómo probarlo"
```

`-u` deja enlazada la rama local con la remota. `--base develop` indica a dónde va el cambio. Incluye `AB#999` en el título para que Azure lo enlace.

### Paso 4. Aprobación y merge

Quien revisa (no el autor):

```bash
gh pr list                              # ver PR abiertos
gh pr view 5                            # ver el detalle del PR #5
gh pr review 5 --approve                # aprobar
```

Quien abrió el PR, después de la aprobación:

```bash
gh pr merge 5 --merge                   # unir a develop (conserva el historial)
```

### Paso 5. Limpieza (cada quien al terminar su tarea)

```bash
git switch develop
git pull                                                    # traer el merge
git push origin --delete feature/nombre-corto-de-la-tarea   # borrar la rama remota
git branch -d feature/nombre-corto-de-la-tarea              # borrar la rama local
git fetch --prune                                           # quitar referencias viejas
git branch -a                                               # comprobar: solo deben quedar develop y main
```

Una rama `feature/*` se borra porque ya cumplió su objetivo: el código quedó en `develop`.

---

## 6. Release: de `develop` a `main`

Se hace cuando hay un conjunto de tareas listas para producción. Pasa por el mismo control: PR con 1 aprobación.

```bash
git switch develop
git pull
gh pr create --base main --head develop --title "release: descripcion" --body "Tareas incluidas"
# otra persona aprueba:
gh pr review <número> --approve
# quien abrió el PR:
gh pr merge <número> --merge
```

> **Advertencia: nunca uses `--delete-branch` en este PR.** Ese flag borra la rama de origen al hacer el merge, y aquí la rama de origen es `develop`. Con `--delete-branch` se elimina `develop` y hay que recrearla. En el PR de una `feature/*` sí es seguro, en el release no.

Después del release:

```bash
git switch main && git pull
git switch develop && git pull
```

---

## 7. Práctica para el equipo (hacerla una vez)

Objetivo: recorrer el flujo completo sin tocar código del proyecto. Cada integrante hace la suya en `docs/practica/`.

```bash
git switch develop
git pull
git switch -c feature/practica-<tu-nombre>
mkdir -p docs/practica
echo "Práctica de flujo de ramas: <tu-nombre>" > docs/practica/<tu-nombre>.md
git add .
git commit -m "docs(practica): prueba del flujo de ramas de <tu-nombre>"
git push -u origin feature/practica-<tu-nombre>
gh pr create --base develop --title "docs(practica): prueba de <tu-nombre>" --body "Práctica del flujo"
```

Luego:

1. Otra persona del equipo revisa y aprueba el PR (`gh pr review <número> --approve`).
2. Tú haces el merge (`gh pr merge <número> --merge`).
3. Haz la limpieza del paso 5 y comprueba con `git branch -a` que tu rama ya no existe.
4. Comprueba que tu archivo aparece en `docs/practica/` estando en `develop`.

Si algo falla, copia el mensaje de error completo y compártelo con el equipo.

---

## 8. Instalación del entorno de desarrollo

### 8.1 Requisitos (según el PPI)

| Herramienta | Versión | Para qué |
|---|---|---|
| Git | reciente | Control de versiones |
| GitHub CLI (`gh`) | reciente | PR desde la terminal (`gh auth login` al instalar) |
| JDK | 21 (LTS) | Backend |
| Maven | 3.9+ (o el wrapper `mvnw` del proyecto) | Dependencias del backend |
| Node.js | LTS | Frontend |
| Docker Desktop | reciente | Backend y n8n en contenedores |
| Cliente PostgreSQL | pgAdmin o DBeaver | Consultar la base de datos |
| Editor | IntelliJ IDEA o Visual Studio Code | Desarrollo |

Equipo recomendado: 8 GB de RAM mínimo, 16 GB recomendado.

Verifica las instalaciones:

```bash
git --version
gh --version
java -version        # debe decir 21
mvn -version
node -v
docker --version
```

### 8.2 Clonar el repositorio

```bash
gh auth login
git clone https://github.com/FelipeArango17/FutData-Aula-Project.git
cd FutData-Aula-Project
git switch develop
```

Configura tu identidad en el repo (usa tu correo de GitHub):

```bash
git config user.name "Tu nombre"
git config user.email "tu-correo@ejemplo.com"
```

### 8.3 Frontend (React + Vite + Tailwind)

El código del frontend aún no está creado en el repositorio. Cuando exista, estos pasos se completan aquí con el comando para levantarlo (`npm install` y `npm run dev`).

Para el design system (ya disponible en `docs/design-system/`):

```bash
cd frontend
npm i -D tailwindcss@3 postcss autoprefixer
npx tailwindcss init -p
```

Después, sigue la sección 6 de `docs/design-system/equivalencias-tailwind.md`: reemplazar `tailwind.config.js`, copiar `tokens.css` a `src/styles/` e importarlo antes de `index.css`.

### 8.4 Backend (Spring Boot)

Proyecto Spring Boot 3.5 (Java 21, Maven con wrapper) en `backend/`, con JDBC sobre HikariCP hacia PostgreSQL de Supabase y la API documentada con Swagger. Guía completa (variables, capas, cómo agregar un módulo, formato de errores, Docker): [`backend/README.md`](backend/README.md).

```bash
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw.cmd spring-boot:run
```

- Arranca en http://localhost:8080 con el perfil `local`, **sin necesitar credenciales**: sin base de datos, `GET /api/health` responde `503` con `"baseDeDatos": "DOWN"`.
- Swagger UI: http://localhost:8080/swagger-ui.html.
- Para conectarse a Supabase, copia `backend/.env.example` como `backend/.env` y completa `DB_URL`, `DB_USER` y `DB_PASSWORD`. El backend lee ese archivo al arrancar.
- Pruebas: `./mvnw verify` (no necesitan base de datos).

> **La API todavía no tiene autenticación** (llega con HU-001, HU-002 y HU-005): no debe desplegarse en un entorno público hasta entonces.

**Nunca se suben llaves ni contraseñas al repositorio:** van en variables de entorno o en `backend/.env`, que está ignorado por Git.

### 8.5 Base de datos y autenticación (Supabase)

El proyecto de Supabase lo administra el equipo. Quien necesite acceso pide la invitación al responsable. La conexión desde el backend es por JDBC con SSL.

### 8.6 Automatización (n8n)

n8n se ejecuta en un contenedor Docker. La configuración, el flujo de notificaciones y los pasos para levantarlo están en [`n8n/README.md`](n8n/README.md). El contrato del webhook que usa el backend está en [`docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md`](docs/info-sprints/sprint-2/contrato-webhook-notificaciones.md).

---

## 9. Design system

Los tokens y componentes están definidos en Figma ("Mockups FutData") y se replican en código en `docs/design-system/`:

| Archivo | Para qué |
|---|---|
| `tokens.css` | Variables CSS de color, espaciado y radios, con los 3 temas |
| `tailwind.config.js` | Clases de Tailwind que apuntan a esas variables |
| `equivalencias-tailwind.md` | Tabla de Figma a clase, contraste WCAG, ejemplos e instalación |

El tema se cambia con `document.documentElement.dataset.theme = 'normal' | 'alto-contraste' | 'oscuro'`.

---

## 10. Solución de problemas comunes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `Everything up-to-date` al hacer `git push origin main` | Tus commits están en otra rama | `git branch` para ver dónde estás; los cambios se suben por PR, no a `main` |
| `push` rechazado a `main` | `main` está protegida | Es lo esperado. Crea una rama `feature/*` y abre un PR |
| No puedo aprobar mi PR | Nadie aprueba su propio PR | Pide a otro integrante que lo revise |
| Mi rama no aparece en Azure | Falta `AB#<ID>` en el commit o en el título del PR | Agrégalo en el título del PR, o enlázalo a mano desde la tarea (*Add link → GitHub Pull Request*) |
| Conflictos al hacer merge | La rama nació de un `develop` desactualizado | `git switch develop && git pull`, luego en tu rama `git merge develop` y resuelve los conflictos |