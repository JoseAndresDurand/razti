# Estado del Proyecto: razti
- **Última actualización:** 2026-11-18
- **Hito activo:** `M2 [ACTIVO]: Vertical jugable en local` (20/11–24/11; iniciado el 18/11)
- **Sprint en curso:** `S02 [ACTIVO]` ★ (cierra M2) · rama `sprint/S02-vertical-local`
- **Fase:** `Fase 2 - S02 · especificación (TDD) y esqueleto WAN`
- **Perfil:** clojure-game

## Tareas completadas
- [x] Diagnóstico de entorno y auditoría de puertos/procesos
- [x] `AGENTS.md` (con `## Proyecto` y `## Gates`), `CLAUDE.md`, `docs/STATE.md`, `docs/LEDGER.md` creados
- [x] `deps.edn`, `tests.edn`, `shadow-cljs.edn`, `package.json`, `build.clj` con versiones exactas (más de 7 días, sin rangos ni git deps)
- [x] `.gitignore` y `.env.example` verificados (`.env-prueba` ignorado, `.env.example` no)
- [x] Topología de carpetas (`src/{cljc,clj,cljs}/razti/…`, `test/…`, `resources/public/assets`, `docs/adr`)
- [x] Repositorio git inicializado (`main`, sin commits)
- [x] Dependencias instaladas por el líder (precarga por alias + `pnpm install --ignore-scripts`)
- [x] Gates `lint`, `format`, `test` en verde sin código; `test-cljs-build` compila con 0 warnings
- [x] `.claude/settings.json` del proyecto aplicado por el líder (`apply-settings.sh project`)

### S01 · build (2026-11-18)
- [x] `config`, `grid`, `specs`, `engine`, `lobby` en `src/cljc/razti/shared/` (puros: grep de pureza limpio)
- [x] Escenarios BDD 1–7 + reglas decididas (T1–T7, D6a–c, D8, R6, R8, R11) + test de convenciones del mapa: 24 pruebas, 112 aserciones, en verde en JVM y Node
- [x] `dev/assets.clj` + alias `:assets`: `map-campus.jpeg` (800×800) y `player-sprites.png` (120×120, alfa)
- [x] `INITIAL-GRID` y `SPAWNS` en `campus.cljc`, trazado provisional aprobado por el líder con `target/grid-preview.png` (D2)
- [x] `.github/workflows/ci.yml` con acciones fijadas por SHA (pendiente de su primera ejecución en GitHub)
- [x] Auditoría QA + seguridad (`docs/audits/S01-2026-11-18-{qa,sec}.md`): sin P0/P1; 7 P2 reparados; 8 P3 al backlog
- [x] `itera-ship` (2026-11-18T00:19:21-05:00): gates del M1 en verde (`test`, `test-cljs-build`, `test-cljs`, `lint`, `format`); `build` y `uber` no aplican hasta S02/S03 (TDD §8, decisión del líder); sin P0; sin secretos en el diff
- [x] Commit, push, PR y merge (líder): PR #1 (docs) y PR #2 (código); CI en verde
- [x] Retro (`docs/retro/S01.md`) y propuesta a `knowledge/` aprobada y escrita
- [x] Borrador de la compuerta `docs/milestones/M1-compuerta.md` (veredicto pendiente)
- Archivos creados: `src/cljc/razti/shared/{config,campus,grid,specs,engine,lobby}.cljc`, `test/cljc/razti/shared/{fixtures,engine_test,lobby_test,replay_test,campus_test}.cljc`, `dev/assets.clj`, `resources/public/assets/{map-campus.jpeg,player-sprites.png}`, `.github/workflows/ci.yml`. Modificados: `deps.edn`, `docs/tdd/{M1,00-core}.md`, `docs/STATE.md`, `docs/LEDGER.md`.

## Advertencias del diagnóstico
- `ss -tlnH` solo muestra los puertos 1080 y 3128 en todas las interfaces: son el proxy del sandbox de Claude Code, no del proyecto. Sin cambios tras el scaffold.
- Sin demonios de desarrollo habilitados (systemctl de sistema y de usuario).
- Firmado de commits: `commit.gpgsign=true`, `gpg.format=ssh`, `user.signingkey` configurado (completo).
- Identidad de git del repo pendiente: el líder la configura en su terminal sin `--global`.
- El gate `test` avisa "No tests were found" (esperado hasta el M1). `test-cljs`, `build` y `uber` quedan para el primer sprint.
- Desde el sandbox no se resuelven dependencias de Maven: cada cambio de `deps.edn` requiere que el líder precargue los alias (ver `CLAUDE.md`).

## Bloqueos activos
- [x] MVP Scope Brief v1.1 APROBADO (2026-11-17): fuente `1-files/MVP-SCOPE-BRIEF-RAZTI-v1.1.md`.
- [x] Licencias decididas (2026-11-17): código MIT (`LICENSE`), assets CC BY 4.0 (`resources/public/assets/LICENSE`).
- [x] Conflictos del brief #1–#4 y #6–#8 decididos (2026-11-17, ver `docs/01-SCOPE.md`); #5 pendiente antes del S02.
- [x] Repo público en GitHub: `JoseAndresDurand/razti` (2026-11-17); `main` y `sprint/S01-motor` publicadas.

## Progreso del build S02 (pausa 2026-11-18)
- [x] Paso 1: `config.cljc` (salas, colores) y `specs.cljc` (SEC-03/04) + `specs_test`; gates en verde.
- [x] Paso 2, esqueleto WAN: hechos `src/clj/razti/server/{core,ws}.clj` (solo eco), `resources/public/{index.html,css/style.css}`, `src/cljs/razti/client/{core,ws}.cljs` (barra de estado), `test/clj/razti/server/{ws_client,smoke,core_test}.clj`, alias `:run`/`:dev`/`:smoke`. `clojure -M:test`: 36 pruebas, 210 aserciones, 0 fallos (2026-11-18).
- [x] `.devcontainer/{devcontainer.json,post-create.sh}`; gates `test` (36/210), `test-cljs` (31/176), `test-cljs-build`, `lint`, `format`, `build` (release, 0 warnings) en verde (2026-11-18).
- [ ] CHECKPOINT 1 (WAN): el líder prueba en Codespaces desde otra red. Después, pasos 3–9 de `docs/tdd/M2.md`.

## Siguiente paso inmediato
- Retomar con `/iterastack:itera-build --sprint S02` (continúa en el paso 2: devcontainer y gates).
