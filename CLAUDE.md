# CLAUDE.md — Operación rápida para Claude Code

Proyecto `razti`. Operas como Ingeniero Full-Stack de IteraStack (perfil `clojure-game`).

@AGENTS.md

## Comandos
- `clojure -M:dev`: servidor del juego en `127.0.0.1:3000` (JVM con ZGC). Lo ejecuta el líder en otra terminal; tú nunca lo lances directamente.
- `pnpm exec shadow-cljs watch app`: compilación en caliente del cliente. También la ejecuta el líder.
- Gates: `bash ~/iterastack/playbook/plugin-iterastack/scripts/gates.sh --cwd . list` y `… run <gate>` (ver `## Gates` en `AGENTS.md`).
- Instalación (la ejecuta el líder, fuera del sandbox): `pnpm install --ignore-scripts` y luego precargar **cada alias por separado**, porque los gates resuelven cada uno con sus propias versiones transitivas: `clojure -P -M:test && clojure -P -M:cljfmt && clojure -P -M:dev && clojure -P -T:build && pnpm exec shadow-cljs compile test`. Si se cambia `deps.edn`, repetir.

## Estructura
```
src/cljc/razti/shared/   reglas puras compartidas (config, campus, grid, engine, lobby, specs)
src/clj/razti/server/    servidor autoritativo (core, ws, rooms, loop)
src/cljs/razti/client/   cliente (core, ws, input, prediction, render)
test/cljc/razti/shared/  escenarios BDD (JVM + Node) y replay
test/clj/razti/server/   integración con bots
resources/public/        index.html, css, assets y js compilado (ignorado)
dev/                     scripts de desarrollo (assets.clj)
docs/                    01-SCOPE.md, tdd/, adr/, STATE.md, LEDGER.md
```

## Orden de lectura antes de escribir código
1. `docs/STATE.md`: fase y bloqueos activos.
2. `docs/01-SCOPE.md`: Hito `[ACTIVO]` y No-Gos. El alcance fuera del hito se rechaza.
3. `docs/tdd/INDEX.md` y la sección del sprint activo. Si no existe o está **[POR ESPECIFICAR]**, no construyas: indica qué falta.
4. En tareas de más de un archivo, presenta un plan breve antes de editar.

## Estándar de código
- `src/cljc` sin efectos secundarios: funciones puras de estado → estado.
- Estado de partida solo con `atom` + `swap!`; nunca `agent`.
- Malli en la frontera de red; sin reflection warnings.
- Al terminar una tarea: ejecuta los gates de `AGENTS.md` y actualiza `docs/STATE.md` y `docs/LEDGER.md`.

## Cierre del sprint (modo automático)
Tras `/itera-build`, cuando el líder confirma que su servidor (`clojure -M:dev`) y su `shadow-cljs watch app` están activos, encadena sin pedir confirmación entre pasos:
1. `/iterastack:itera-audit` (el `ux-auditor` navega el juego local).
2. Revisión en vivo con Playwright MCP sobre el servidor del líder (E12); lo nuevo se agrega al reporte de UX como «Revisión en vivo».
3. Aprobación automática: marca `[x] (auto)` los hallazgos **P0, P1 y P2** confirmados; los **P3** quedan `[ ] backlog (auto: P3)`.
4. `/iterastack:itera-fix`. Si quedan P0/P1, audita de nuevo el delta (máximo 2 ciclos).
5. `/iterastack:itera-ship` (corre los gates de `## Gates`).
6. Informe final: reparados, backlog, gates y los comandos de commit, push y PR.

Solo te detienes a consultar si: una reparación cambia el alcance o una decisión del líder, aparece un secreto o un P0 de seguridad, el bucle de fix no converge, un gate falla después del fix, o el diff cambia la sección `## Gates` de `AGENTS.md`. Commit, push, PR y merge siguen siendo del líder.
