# TDD — RAZTI · Núcleo (transversal)

## §1 Metadatos y control del hito

### 1.1 Identificación
- **Producto:** RAZTI · Multiplayer 2D Tactical Arena (producto académico).
- **Repositorio:** `github.com/JoseAndresDurand/razti` (público). Código MIT; assets CC BY 4.0.
- **Perfil IteraStack:** `clojure-game`. Gates en `AGENTS.md` → `## Gates`.
- **Fuente:** MVP Scope Brief v1.1 (aprobado 2026-11-17) vía `docs/01-SCOPE.md`.

### 1.2 Hito activo
- **M1 · Motor isomórfico** `[ACTIVO]` (17/11–19/11), sprint **S01** ★.
- **Objetivo técnico:** reglas puras del juego y del lobby en `.cljc`, idénticas en JVM y Node, deterministas.
- **Métrica de la compuerta:** gates `test`, `test-cljs`, `lint`, `format` con 0 fallos y 0 errores en los 7 escenarios BDD y en el test de convenciones del mapa (brief §9).

### 1.3 Frontera de alcance del M1
**SÍ:** `src/cljc/razti/shared/{config,campus,grid,specs,engine,lobby}.cljc`; pruebas `.cljc` (BDD 1–7, reglas decididas, replay, convenciones del mapa); `dev/assets.clj` (mapa 800×800, hoja de sprites, vista previa de la grilla); alias `:assets` en `deps.edn`; `.github/workflows/ci.yml`.

**NO (congelado hasta su sprint):** cualquier archivo en `src/clj` o `src/cljs`; http-kit, Sente, reitit en código; `index.html`, CSS, canvas, overlay en el juego; `README.md` de juego, `Dockerfile`, `.devcontainer`; Playwright. **No-Gos globales** (`01-SCOPE.md`): editor de mapas, persistencia/JWT/BD, power-ups, `agent`, hosting separado, VPS/dominio/TLS, 60 ticks/s, modificar el diseño del mapa.

### 1.4 Dependencias permitidas
Solo las de `deps.edn` y `package.json` actuales (versiones exactas). En el M1 el código de `src/cljc` usa **únicamente** `clojure.core`, `clojure.set` y `malli.core` (en `specs.cljc`); las pruebas, `clojure.test` y `clojure.test.check`. `dev/assets.clj` usa solo el JDK (`javax.imageio`, `java.awt`). Ninguna dependencia nueva sin aprobación del líder.

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

### 1.6 Datos bloqueantes
- **Trazado de `INITIAL-GRID`/`SPAWNS`:** lo propone Claude en el S01 y el líder lo aprueba (provisional) en el checkpoint 2 con `target/grid-preview.png`.
- **Conflicto #5** (máximo de salas, minutos de inactividad): bloquea el S02, no el S01.
- **Colores de jugador** (rojo, verde, amarillo; el azul base es `#2F6FD6`): bloquean el S02.

### 1.7 Prevención transversal (`knowledge/errores-frecuentes.md`)
- **QA-TDD-DRIFT:** al cambiar el estado de un sprint, actualizar en el mismo cambio `01-SCOPE.md`, `STATE.md`, `tdd/INDEX.md` y la cabecera de la sección del sprint.
- **QA-E2E-MISSING:** cada escenario BDD y cada regla decidida tienen su prueba; ninguno se omite "porque no aplica en este entorno".

### 1.8 Historial
| Fecha | Cambio |
| :--- | :--- |
| 2026-11-17 | TDD inicial: núcleo + M1/S01. Resoluciones D1–D8, R11 y T1–T10 |
| 2026-11-17 | El líder aprueba las resoluciones T1–T10 |
| 2026-11-18 | Build S01: `empty-room?` cuenta jugadores conectados (con T7 los desconectados siguen en el mapa durante la partida). Trazado provisional de `INITIAL-GRID`/`SPAWNS` aprobado por el líder (D2) |
| 2026-11-18 | Auditoría S01 (QA + seguridad, sin P0/P1). QA-01: `new-room` deja `:room/next-id 1` (el creador usa el 0). SEC-01/02: `specs.cljc` con fin de texto estricto y límites en todos los esquemas (§5 actualizado en el código; ver `docs/audits/S01-2026-11-18-*.md`) |

## §3 Tokens visuales
No aplica al M1 (sin UI). Constantes de geometría en `config.cljc`: canvas 800×800, celda 40 px, grilla 20×20. Los colores de jugador y del HUD se especifican en el S02 (ver §1.6).

## §6 Rendimiento
- `tick-game` corre 20 veces por segundo por sala: complejidad O(jugadores + bombas × radio), sin validación Malli en el camino caliente (C9). Malli solo en la frontera (S02) y en pruebas.
- Estructuras persistentes de Clojure; la grilla es un vector de vectores (acceso `get-in grid [y x]`).
- El render a 60 FPS y la latencia WAN se miden en el M3.

## §7 Seguridad y entorno
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
| `build` | `pnpm exec shadow-cljs release app` | No (S02/S03) |
| `uber` | `clojure -T:build uber` | No (S02/S03) |

**Pureza:** `grep -rnE "\(atom|swap!|reset!|println|random-uuid|rand-int|rand-nth|currentTimeMillis|js/Date" src/cljc` → sin coincidencias.
