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

### 8.7 Levantar todo con un solo comando

Backend y n8n en contenedores, desde la raíz del repositorio. Es la forma más corta de tenerlos juntos; las secciones 8.4 y 8.6 siguen valiendo para trabajar cada uno por separado (`./mvnw spring-boot:run`, `cd n8n && docker compose up -d`).

**Requisitos:** Docker Desktop encendido y Docker Compose **2.20 o superior** (`docker compose version`; el archivo usa `include`).

**1. Crea los dos archivos `.env`** (están ignorados por Git; los secretos nunca van en el repositorio):

```bash
cp backend/.env.example backend/.env          # macOS, Linux, Git Bash
cp n8n/.env.example n8n/.env
```

```powershell
Copy-Item backend\.env.example backend\.env   # PowerShell
Copy-Item n8n\.env.example n8n\.env
```

**2. Complétalos:**

- `n8n/.env`: `N8N_ENCRYPTION_KEY`, `N8N_WEBHOOK_TOKEN` y `SMTP_FROM` (ver [`n8n/README.md`](n8n/README.md), paso 1). Un valor aleatorio se genera con `openssl rand -hex 32` (PowerShell: ver el comentario de `n8n/.env.example`).
- `backend/.env`: `DB_URL`, `DB_USER` y `DB_PASSWORD` de Supabase y `CORS_ORIGINS`. En producción agrega el dominio exacto de Vercel, por ejemplo `https://TU_DOMINIO.vercel.app`, separado por coma del origen local si también lo necesitas. **`N8N_WEBHOOK_TOKEN` debe ser el MISMO valor en los dos archivos**; si difieren, n8n rechaza al backend con `401`.

- Perfil de Spring: el compose arranca el backend con **`local`** por defecto, que funciona sin Supabase ni base de datos (guardar comprobantes o enviar correos falla con un mensaje claro). Para probar el perfil de producción: `SPRING_PROFILES_ACTIVE=prod docker compose up -d`. Ese perfil **exige las ocho variables** (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGINS`, `N8N_BASE_URL`, `N8N_WEBHOOK_TOKEN`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`) y si falta alguna el contenedor se detiene diciendo cuál. La imagen sola (`docker run`, sin el compose) sigue usando `prod`.

> **Hay dos `N8N_BASE_URL` y no son lo mismo:**
>
> | Dónde | Valor | Quién la usa |
> | :--- | :--- | :--- |
> | `n8n/.env` | `http://localhost:5678` | Es cómo **n8n se presenta** (editor y URL de los webhooks). La usa tu **navegador**. |
> | Backend dentro de Docker | `http://n8n:5678` | Es la que usará el **cliente HTTP del backend**. `docker-compose.yml` la fija así, por encima de `backend/.env`: dentro de la red de Docker, `n8n` es el nombre del servicio. |
>
> En `backend/.env` deja `http://localhost:5678`: es el valor correcto cuando corres el backend **fuera** de Docker (`./mvnw spring-boot:run`), y con este compose se sobrescribe solo. Pero **dentro del contenedor `localhost:5678` NO funciona**: `localhost` es el propio backend, no n8n, y la llamada falla con `Connection refused`. Eso pasa si ejecutas la imagen sin este compose (por ejemplo `docker run --env-file backend/.env`, como en `backend/README.md`) o si alguien quita la sobrescritura: en ese caso pon `N8N_BASE_URL=http://n8n:5678` y conecta el contenedor a la red de n8n.

**3. Levanta, revisa y apaga:**

```bash
docker compose up -d --build     # construye el backend y arranca backend + n8n
docker compose ps                # ambos deben quedar "Up" y, pasados unos segundos, "(healthy)"
docker compose logs -f backend   # registros del backend (Ctrl+C para salir)
docker compose down              # detiene y elimina los contenedores
```

> **No uses `docker compose down -v`.** La `-v` borra el volumen `futdata_n8n_data`, con los flujos y la credencial SMTP de n8n. Con `down` a secas el volumen se conserva.

Los comandos son los mismos en macOS, Linux, Git Bash y PowerShell.

| Servicio | Puerto | URL |
| :--- | :--- | :--- |
| Backend | 8080 | http://localhost:8080/swagger-ui.html |
| n8n | 5678 | http://localhost:5678 |

**Cómo verificar:**

- http://localhost:8080/api/health responde. Con el perfil `local` y sin base de datos devuelve `503` con `"baseDeDatos": "DOWN"`: es lo normal, la aplicación está viva (por eso el healthcheck del contenedor mira el puerto y no este endpoint).
- http://localhost:5678 abre el editor de n8n (la primera vez pide crear la cuenta de propietario); http://localhost:5678/healthz responde `{"status":"ok"}`.

**Lo que sigue siendo manual** (vive en el volumen de n8n, no en el compose): crear la credencial **"SMTP FutData"** e importar y publicar el flujo. Pasos 3, 4 y 5 de [`n8n/README.md`](n8n/README.md); allí los comandos `docker compose exec n8n ...` funcionan igual desde la raíz.

**Si ya tienes n8n corriendo desde `n8n/`:** el contenedor se llama igual (`futdata-n8n`), así que antes detenlo con `cd n8n && docker compose down` (sin `-v`; tus datos quedan en el volumen) y levanta todo desde la raíz. En sentido contrario, igual: `docker compose down` en la raíz antes de usar `cd n8n && docker compose up -d`.

| Síntoma | Causa probable | Qué hacer |
| :--- | :--- | :--- |
| `env file .../backend/.env not found` (o `n8n/.env`) | No copiaste el `.env.example` | Paso 1 |
| `Define N8N_ENCRYPTION_KEY en n8n/.env` | Falta un valor en `n8n/.env` | Paso 2 |
| El backend se reinicia o se detiene | Con el perfil `prod` falta una variable | `docker compose logs backend` dice cuál |
| Error al leer `include` en `docker-compose.yml` | Compose anterior a 2.20 | Actualiza Docker Desktop |
| `port is already allocated` | Algo usa el 8080 o el 5678 | Detén ese proceso o contenedor |

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