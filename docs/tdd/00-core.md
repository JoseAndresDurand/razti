# TDD — RAZTI · Núcleo (transversal)

## §1 Metadatos y control del hito

### 1.1 Identificación
- **Producto:** RAZTI · Multiplayer 2D Tactical Arena (producto académico).
- **Repositorio:** `github.com/JoseAndresDurand/razti` (público). Código MIT; assets CC BY 4.0.
- **Perfil IteraStack:** `clojure-game`. Gates en `AGENTS.md` → `## Gates`.
- **Fuente:** MVP Scope Brief v1.1 (aprobado 2026-11-17) vía `docs/01-SCOPE.md`.

### 1.2 Hito activo
- **M2 · Vertical jugable en local** `[ACTIVO]` (20/11–24/11, iniciado el 18/11), sprint **S02** ★. (M1 `[GO]` el 2026-11-18.)
- **Objetivo técnico:** servidor autoritativo (http-kit, Sente, reitit, salas, reloj de 20 Hz) y cliente básico jugable, validando primero el WebSocket por la red real (Codespaces).
- **Métrica de la compuerta** (brief §9): esqueleto con `/health` y eco WS desde otra red; servidor sin warnings y solo en `127.0.0.1:3000`; `bots_test.clj` en verde; job de clon limpio en verde; partida manual con 4 pestañas hasta un ganador.

### 1.3 Frontera de alcance

**M2 (activo) — SÍ:** `src/clj/razti/server/{core,ws,rooms,loop}.clj`; `src/cljs/razti/client/{core,ws,input,render}.cljs`; `resources/public/{index.html,css/style.css}`; pruebas `test/clj/razti/server/*`; `README.md`, `Dockerfile`, `.dockerignore`, `.devcontainer/`; jobs de CI de clon limpio y Docker; ajustes de `config.cljc`, `specs.cljc`, `deps.edn` y `build.clj` listados en `M2.md`.
**M2 — NO:** predicción, reconciliación e interpolación (C12), HUD completo, Playwright/e2e, sprites de bomba y explosión (S03); todo lo de §12 del brief.

**M1 (`[GO]`, histórico)**
**SÍ:** `src/cljc/razti/shared/{config,campus,grid,specs,engine,lobby}.cljc`; pruebas `.cljc` (BDD 1–7, reglas decididas, replay, convenciones del mapa); `dev/assets.clj` (mapa 800×800, hoja de sprites, vista previa de la grilla); alias `:assets` en `deps.edn`; `.github/workflows/ci.yml`.

**NO (congelado hasta su sprint):** cualquier archivo en `src/clj` o `src/cljs`; http-kit, Sente, reitit en código; `index.html`, CSS, canvas, overlay en el juego; `README.md` de juego, `Dockerfile`, `.devcontainer`; Playwright. **No-Gos globales** (`01-SCOPE.md`): editor de mapas, persistencia/JWT/BD, power-ups, `agent`, hosting separado, VPS/dominio/TLS, 60 ticks/s, modificar el diseño del mapa.

### 1.4 Dependencias permitidas
Solo las de `deps.edn` y `package.json` actuales (versiones exactas). **M2:** el servidor usa `http-kit`, `ring-core` (sesión, params, recursos), `reitit-ring`, `sente` (con su packer Transit) y `core.async` (canal de Sente); el cliente, `sente` y `transit-cljs`; las pruebas de servidor, `java.net.http` del JDK. **Sin** `ring-anti-forgery`, `ring-defaults`, `org.java-websocket` ni dependencias npm de runtime. En el M1 el código de `src/cljc` usa **únicamente** `clojure.core`, `clojure.set` y `malli.core` (en `specs.cljc`); las pruebas, `clojure.test` y `clojure.test.check`. `dev/assets.clj` usa solo el JDK (`javax.imageio`, `java.awt`). Ninguna dependencia nueva sin aprobación del líder.

### 1.5 Resoluciones (decisiones del brief y del TDD)

**Del líder (2026-11-17, `01-SCOPE.md`):**
- **D1** Código de ejemplo `"UNSA2"`; alfabeto sin 0/O/1/I.
- **D2** Trazado provisional de `INITIAL-GRID`/`SPAWNS` aprobado con la vista previa PNG en el S01; ajuste fino con el overlay en el S02.
- **D3** `dev/assets.clj` en el S01.
- **D6a** Si el dueño sale, el dueño pasa al jugador conectado con menor `:player/join-order`; sala sin jugadores → vacía (el servidor la elimina).
- **D6b** Solo el dueño reinicia.
- **D6c** Nickname repetido en la sala → `{:error :name-taken}`.
- **D7** Fixtures: escenario 4 con [3 2] = 0; escenario 5 con ≥ 4 ticks entre el movimiento y el intento de volver.
- **D8** Una bomba por celda.
- **R11** Desconexión en `:playing` → eliminado en el tick en que se procesa.

**Del TDD (huecos técnicos que el brief no fija; el líder los aprueba con este TDD):**
- **T1 Cooldown:** se descuenta al **inicio** de cada tick (paso 0, antes de R10.1). Un movimiento en el tick *t* deja `cooldown 4`; el siguiente movimiento es posible en *t+4* (1 celda cada 200 ms, R2). El cooldown solo se fija si el movimiento ocurre; un intento bloqueado no lo consume pero sí cambia `:player/dir`.
- **T2 Blasts y bombas:** un blast **no se detiene** en una bomba: la alcanza (R7) y sigue.
- **T3 Cálculo del blast:** las celdas de todos los blasts de un tick se calculan sobre la grilla **anterior** a destruir soft-blocks; los soft-blocks alcanzados se destruyen al final de la fase de detonación. Si la grilla cambia, `:room/grid-version` sube 1.
- **T4 IDs:** un único contador `:room/next-id` para bombas (`"b-<n>"`) y blasts (`"bl-<n>"`); un blast por bomba detonada; la cadena se resuelve en el orden de `:room/bombs`.
- **T5 Victoria:** se evalúa solo en `:playing`: 1 vivo → `:room/winner` = su id; 0 vivos → `:draw`; en ambos casos `:room/status :finished`.
- **T6 Entradas ignoradas:** de un jugador muerto o inexistente, o con la sala fuera de `:playing` (`enqueue-input` no encola).
- **T7 Desconexión:** `leave-room` en `:playing` encola `{:action :leave}` y marca `:player/connected? false`; el paso R10.1 lo elimina. `restart` quita a los desconectados.
- **T8 Reinicio:** `restart` (solo el dueño, solo en `:finished`) vuelve directo a `:playing` con la grilla inicial; exige ≥ 2 jugadores conectados.
- **T9 Estado extendido** (aditivo al brief §6): `:room/initial-grid`, `:room/spawns`, `:player/join-order`, `:player/connected?`.
- **T10 Apariciones:** al iniciar, el jugador con el *i*-ésimo `:player/join-order` va a `(nth spawns i)`.

- **T11 CSRF:** el servidor genera el token (32 bytes de `SecureRandom`, base64url) en `[:session :csrf-token]` y lo inyecta en `index.html` (`<meta name="csrf-token">`); Sente lo valida con su `:csrf-token-fn` por defecto. Sin `ring-anti-forgery`.
- **T12 Identidad:** `:uid` aleatorio (UUID) en la sesión (cookie `razti-session`, `HttpOnly`, `SameSite=Strict`, almacén en memoria). El `:player-id` de toda entrada es ese uid, nunca un dato del cliente.
- **T13 Reloj:** `ScheduledThreadPoolExecutor` (2 hilos daemon); una tarea de tasa fija de 50 ms por sala, solo en `:playing`; al llegar a `:finished` envía el último snapshot y se cancela.
- **T14 Snapshots:** `[:razti/snapshot]` por tick sin grilla; la grilla va incluida solo en el tick en que cambia `:room/grid-version` (C11).
- **T15 Inactividad:** actividad = cualquier evento válido de un jugador de la sala; barrido cada 10 s; se elimina una sala sin jugadores conectados o con ≥ 5 min sin actividad, avisando `[:razti/error {:reason :room-closed}]`.
- **T16 Límite de entradas:** ventana deslizante de 1 s por uid, máximo 30 eventos; el exceso se descarta sin respuesta.
- **T17 Una sala por uid:** crear o unirse estando en otra sala → `{:error :already-in-room}`.
- **T18 Desconexión:** `:chsk/uidport-close` → `lobby/leave-room` (R11).
- **T19 Cabeceras:** CSP `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'` (con `-Drazti.dev=true`, `connect-src` añade `ws://127.0.0.1:9630 ws://localhost:9630` para la recarga de shadow-cljs); `X-Content-Type-Options: nosniff`; `Referrer-Policy: same-origin` (actualizado 2026-11-18: Sente usa el Referer cuando falta `Origin`).
- **T20 Colores de jugador** por `:player/join-order`: azul `#2F6FD6`, rojo `#D64545`, verde `#3FA34D`, amarillo `#E0B92E`; cambio de color por rango de tono (200°–250°).
- **T21 Esqueleto WAN:** barra de estado con el tipo de conexión y el eco en ms; la compuerta exige «WebSocket», no Ajax.
- **T22 `uber` en el sandbox:** `-Djava.io.tmpdir=.cpcache` en el alias `:build` (actualizado 2026-11-18: `.cpcache` existe antes de arrancar la JVM; con `target/tmp` la JVM avisaba que no existía).
- **SEC-03/SEC-04 (backlog del S01):** `ClientEvent` cerrado y `valid-client-event?` que nunca lanza.

### 1.6 Datos bloqueantes
- **Trazado de `INITIAL-GRID`/`SPAWNS`:** lo propone Claude en el S01 y el líder lo aprueba (provisional) en el checkpoint 2 con `target/grid-preview.png`.
- Conflicto #5 y colores de jugador: resueltos (3 salas, 5 min; colores T20 aprobados).
- **Etiquetas exactas de imágenes** (Docker de build y runtime, devcontainer): se resuelven en el build del S02; si no hay red, las aporta el líder.
- **Checkpoint WAN (S02):** superado el 2026-11-18 con Cloudflare Quick Tunnel desde el Codespace (WebSocket, eco ≈ 331 ms desde datos móviles). El reenvío de puertos de Codespaces queda descartado para el juego (reescribe `Origin`/`Host` y envía frames sin máscara).

### 1.7 Prevención transversal (`knowledge/errores-frecuentes.md`)
- **QA-TDD-DRIFT:** al cambiar el estado de un sprint, actualizar en el mismo cambio `01-SCOPE.md`, `STATE.md`, `tdd/INDEX.md` y la cabecera de la sección del sprint.
- **QA-E2E-MISSING:** cada escenario BDD y cada regla decidida tienen su prueba; ninguno se omite "porque no aplica en este entorno".

### 1.8 Historial
| Fecha | Cambio |
| :--- | :--- |
| 2026-11-17 | TDD inicial: núcleo + M1/S01. Resoluciones D1–D8, R11 y T1–T10 |
| 2026-11-17 | El líder aprueba las resoluciones T1–T10 |
| 2026-11-18 | Build S01: `empty-room?` cuenta jugadores conectados (con T7 los desconectados siguen en el mapa durante la partida). Trazado provisional de `INITIAL-GRID`/`SPAWNS` aprobado por el líder (D2) |
| 2026-11-18 | TDD del S02 (`M2.md`): núcleo actualizado al M2; resoluciones T11–T22 y SEC-03/04 |
| 2026-11-18 | El líder aprueba T11–T22 (incluidos los colores de T20) |
| 2026-11-18 | Auditoría S02 (QA + seguridad, sin P0/P1; 10 P2 reparados). Nuevos: `razti.server.sessions` (almacén de sesiones con TTL de 2 h y tope de 2000, purgado en el barrido; SEC-01); `config/max-events-per-second-global` 400 además de T16 y el límite aplicado antes de validar, con purga del limiter (SEC-02/03); el reloj se retira solo cuando su sala no existe o no está en `:playing` y `remove-room!` cancela el reloj que quita (QA-01/02); `join!` devuelve `:room-not-found` si la sala desaparece (QA-03); `stop!` espera al executor (QA-05); el cliente vuelve al lobby si `sync` no encuentra sala (QA-06) |
| 2026-11-18 | Build S02 paso 8: T22 con `.cpcache`; `Dockerfile` con `node:22.23.5-bookworm-slim`, `clojure:temurin-21-tools-deps-1.12.6.1673-bookworm-slim` y `eclipse-temurin:21.0.13_9-jre-noble` (etiquetas exactas, sin digest); jobs de CI `clean-clone` y `docker`; README con el camino WAN de la opción A |
| 2026-11-18 | Checkpoint 3: evento nuevo `[:razti/sync {}]` (cerrado) → `{:ok {:room vista-o-nil}}`; el cliente lo pide al abrir la conexión para recuperar su sala tras recargar u abrir otra pestaña de la misma sesión (antes quedaba atrapado: «Ya estás en una sala» sin forma de salir) |
| 2026-11-18 | Build S02 paso 4: `log` pasa a `razti.server.log` y `send-room!` a `rooms` (evita el ciclo ws↔loop); sin `:executor` el reloj no arranca (pruebas del router). Sente 1.22 entrega los eventos del servidor sin envolver en `:chsk/recv` (`:wrap-recv-evs? false`): cliente y bots tratan como push todo lo que no es `:chsk/*` |
| 2026-11-18 | Decisión del líder (opción A): camino WAN = Codespaces + Cloudflare Quick Tunnel (`cloudflared` 2026.10.7, `RAZTI_ALLOWED_ORIGINS` con la URL del túnel); sin aceptar el origen reescrito por Codespaces. README, devcontainer y CI se ajustan en el paso 8 |
| 2026-11-18 | Checkpoint 1 en Codespaces: el proxy de puertos reenvía frames WebSocket sin máscara y http-kit los rechaza (`unmasked client to server frame`); Ajax recibe 403. Cambios: `Referrer-Policy: same-origin` (T19) y registro del motivo de cada rechazo de Sente (sin tokens). Siguiente: diagnóstico del 403 y prueba con Cloudflare Quick Tunnel |
| 2026-11-18 | Build S02 paso 2: el CSP de desarrollo (`-Drazti.dev=true`) añade también `'unsafe-eval'` en `script-src` (la carga en caliente de shadow-cljs lo necesita; en `:run` y en release no). El binding de S02-1 se prueba conectando a las IPs no-loopback del equipo; sin ninguna (sandbox), se verifica con `ss -tlnH`. El devcontainer usa `mcr.microsoft.com/devcontainers/java:3.1.9-21-bookworm` + feature `node:1` (22) y `.devcontainer/post-create.sh` |
| 2026-11-18 | Auditoría S01 (QA + seguridad, sin P0/P1). QA-01: `new-room` deja `:room/next-id 1` (el creador usa el 0). SEC-01/02: `specs.cljc` con fin de texto estricto y límites en todos los esquemas (§5 actualizado en el código; ver `docs/audits/S01-2026-11-18-*.md`) |

## §3 Tokens visuales
**M2:** fondo `#14161A`, texto `#F2F2F2`, foco `#FFB000` (contorno 3 px); colores de jugador en T20 y `config/player-colors`; soft-block `#8B5A2B`/`#5C3A1A`; blast `#FFB000`/`#FFF2A8`. No aplica al M1 (sin UI). Constantes de geometría en `config.cljc`: canvas 800×800, celda 40 px, grilla 20×20. Los colores de jugador y del HUD se especifican en el S02 (ver §1.6).

## §6 Rendimiento
- `tick-game` corre 20 veces por segundo por sala: complejidad O(jugadores + bombas × radio), sin validación Malli en el camino caliente (C9). Malli solo en la frontera (S02) y en pruebas.
- Estructuras persistentes de Clojure; la grilla es un vector de vectores (acceso `get-in grid [y x]`).
- El render a 60 FPS y la latencia WAN se miden en el M3.

## §7 Seguridad y entorno
- **M2:** `allowed-origins` de Sente desde `env-config` (local + Codespaces + `RAZTI_ALLOWED_ORIGINS`); CSRF de Sente activo (T11); cookie de sesión `HttpOnly` + `SameSite=Strict`; cabeceras T19; validación Malli de todo evento antes del router; límite T16; máximo 3 salas; dentro del contenedor (y solo ahí) `RAZTI_HOST` es la dirección de todas las interfaces y el host publica `127.0.0.1:3000`; imagen final JRE con usuario sin privilegios.
- Servidores (desde el S02) solo en `127.0.0.1` vía `RAZTI_HOST`; nunca una dirección de todas las interfaces en el código.
- `src/cljc` puro: sin I/O, reloj, aleatoriedad, `atom` ni `println` (verificación con grep en §8).
- Nunca leer ni versionar `.env*` (salvo `.env.example`).
- Versiones exactas en `deps.edn` y `package.json`; `pnpm install --frozen-lockfile --ignore-scripts` en la CI.
- **CI:** `permissions: contents: read`; acciones de terceros fijadas por **SHA completo** con la versión en comentario; sin secretos.
- `dev/assets.clj` lee los originales desde una ruta que recibe por argumento (por defecto `../1-files/img`) y escribe solo en `resources/public/assets/` y `target/`.

## §8 Gates (comandos)
Los ejecuta `bash ~/iterastack/playbook/plugin-iterastack/scripts/gates.sh --cwd . run <gate>`. Cada uno termina con código 0, sin errores ni warnings:

| Gate | Comando | En el M1 |
| :--- | :--- | :--- |
| `test` | `clojure -M:test` | Sí |
| `test-cljs-build` | `pnpm exec shadow-cljs compile test` | Sí (0 warnings) |
| `test-cljs` | `node target/test/node-tests.js` | Sí |
| `lint` | `clj-kondo --lint src test` | Sí |
| `format` | `clojure -M:cljfmt check` | Sí |
| `build` | `pnpm exec shadow-cljs release app` | No en el M1 · **Sí desde el S02** |
| `uber` | `clojure -T:build uber` | No en el M1 · **Sí desde el S02** |

**Pureza:** `grep -rnE "\(atom|swap!|reset!|println|random-uuid|rand-int|rand-nth|currentTimeMillis|js/Date" src/cljc` → sin coincidencias.
