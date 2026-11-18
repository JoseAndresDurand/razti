# Estado del Proyecto: razti
- **Última actualización:** 2026-11-17
- **Hito activo:** `M1 [ACTIVO]: Motor isomórfico` (17/11–19/11)
- **Sprint en curso:** `S01 [ACTIVO]` ★ (cierra M1) · rama `sprint/S01-motor`
- **Fase:** `Fase 1 - S01 · especificación (TDD) y construcción del motor`
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
- [ ] Repo público en GitHub (`razti`, cuenta personal): lo crea el líder.

## Siguiente paso inmediato
- `/iterastack:itera-tdd-unify --sprint S01` (no existe `docs/tdd/`), y después `/iterastack:itera-build --sprint S01`.
