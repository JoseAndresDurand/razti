## Proyecto
- Tipo: producto
- Perfil: clojure-game
- Alias en la base de conocimiento: PENDIENTE(líder)

## Gates
<!-- Los ejecuta scripts/gates.sh del plugin: un comando simple por línea, sin operadores de shell. Cambiarlos requiere aprobación del líder. -->
- test: clojure -M:test
- test-cljs-build: pnpm exec shadow-cljs compile test
- test-cljs: node target/test/node-tests.js
- lint: clj-kondo --lint src test
- format: clojure -M:cljfmt check
- build: pnpm exec shadow-cljs release app
- uber: clojure -T:build uber

# AGENTS.md: gobernanza de RAZTI

RAZTI es un juego multijugador 2D en tiempo real (arena de bombas) para 4 jugadores en navegadores y redes distintas. Lógica isomórfica en `.cljc`, servidor autoritativo en Clojure (JVM 21) y cliente ClojureScript (shadow-cljs + Canvas 2D). Es un producto académico de programación funcional pura.

## 1. Alcance (GH-600)
- El alcance vigente es el de `docs/01-SCOPE.md` (generado por `/itera-scope` desde el MVP Scope Brief v1.1). Solo se construye lo del hito `[ACTIVO]`; lo demás se rechaza.
- Ningún archivo de código se genera fuera del stack, las reglas y los No-Gos declarados en el brief.
- **No-Gos del MVP:** editor de mapas o herramientas de diseño de grillas; Supabase, PostgreSQL, JWT o persistencia en disco (todo vive en RAM); power-ups; `agent` de Clojure para el estado de partida (se exige `atom` + `swap!`); hosting separado del cliente (Vercel u otro CDN); VPS propio, dominio y proxy TLS.

## 2. Arquitectura
- `src/cljc/razti/shared/`: reglas **puras** (motor, lobby, grilla, esquemas Malli, configuración). Sin efectos secundarios, sin aleatoriedad, sin reloj, sin I/O. Los temporizadores se miden en ticks dentro del estado.
- `src/clj/razti/server/`: http-kit + ring + reitit, Sente (Transit), un `atom` para el lobby y un `atom` por sala; reloj de 20 Hz por sala que llama `(swap! room tick-game)`. Las entradas se encolan entre ticks.
- `src/cljs/razti/client/`: cliente Sente, entrada con número de secuencia, predicción del movimiento propio, interpolación de los demás y render en Canvas 2D a 60 FPS.
- Malli valida en la **frontera** (todo evento entrante); las invariantes del estado se validan solo en tests y en desarrollo.
- Un solo origen: el servidor del juego sirve el cliente estático y el WebSocket.

## 3. Seguridad
- Todo servidor (http-kit, shadow-cljs, nREPL) escucha **solo en `127.0.0.1`**. La dirección del juego va por `RAZTI_HOST` (por defecto `127.0.0.1`; solo dentro del contenedor de Docker vale la dirección de todas las interfaces).
- Sin demonios residentes: prohibido `systemctl enable` en la máquina de desarrollo.
- Nunca leer, imprimir ni versionar `.env*` (salvo `.env.example`).
- WebSocket: chequeo de `Origin` contra la lista permitida y CSRF de Sente activo. Límite de 30 entradas por segundo por cliente. Nickname `[A-Za-z0-9_-]{1,12}`.
- Cadena de suministro: versiones exactas en `deps.edn` y `package.json`, sin rangos ni git deps; `pnpm install --ignore-scripts`.
- Contenedor: JRE (no JDK) y usuario sin privilegios.

## 4. Calidad
- Sin reflection warnings (`(set! *warn-on-reflection* true)` en los namespaces del servidor).
- Cada escenario BDD de `docs/01-SCOPE.md` tiene su prueba; las pruebas `.cljc` corren en JVM (kaocha) y en Node (shadow-cljs `:test`).
- IDs deterministas con contador en el estado (`:room/next-id`). Los códigos de sala del servidor son el único punto con aleatoriedad.

## 5. Definición de "hecho"
Una tarea está terminada solo cuando todos los gates de `## Gates` aplicables al hito pasan con 0 fallos, 0 errores y 0 warnings, cada escenario BDD tocado tiene su prueba, y `docs/STATE.md` y `docs/LEDGER.md` están actualizados.

## 6. Git
- Commit, push, PR y merge los hace el líder. Identidad del repo (configuración local): `José Andrés Durand <jandres@unsa.edu.pe>`.
