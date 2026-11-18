# Compuerta de validación — Hito M1: Motor isomórfico

- **Producto:** RAZTI · **Proyecto:** razti · **Alias:** C02 (propuesto)
- **Etapa del brief:** M1 · Motor isomórfico (17/11–19/11) · **Sprint de cierre:** S01 ★
- **Fecha de la compuerta:** 2026-11-18 · **Versión evaluada:** `m1` (commit `8f9dcb2`, `main`) · **Entorno:** local (gates) + CI de GitHub
- **Documento de referencia:** `docs/01-SCOPE.md` (Hito M1, "Compuertas de validación") y MVP Scope Brief v1.1 §9

> Borrador generado por `/itera-retro`. **El veredicto lo dicta el líder**: este documento junta la evidencia, no decide.
> Después del veredicto: `/itera-scope --veredicto M1 go|pivot|no-go`.

## Hipótesis del hito
- Las reglas del juego y del lobby pueden escribirse como funciones puras en `.cljc`, idénticas en JVM y Node, y deterministas (mismas entradas → mismo estado).

## Métricas frente a umbrales
Las métricas del M1 son deterministas (comandos). Valores medidos con `gates.sh` sobre `main@8f9dcb2` el 2026-11-18.

| # | Métrica | Valor medido | Periodo / tamaño de muestra | Umbral Go | Umbral No-Go | Fuente de la evidencia | Lectura |
|---|---|---|---|---|---|---|---|
| 1 | Fallos y errores en los 7 escenarios BDD y en el test del mapa (JVM) | 0 fallos, 0 errores | 30 pruebas, 163 aserciones | 0 fallos y 0 errores | Sin No-Go automático → Pivot (decisión del 2026-11-17) | Gate `test` (`clojure -M:test`) | supera Go |
| 2 | Lo mismo en Node | 0 fallos, 0 errores (build con 0 warnings) | 30 pruebas, 163 aserciones | 0 fallos y 0 errores | ídem | Gates `test-cljs-build` + `test-cljs` | supera Go |
| 3 | Lint | 0 errores, 0 warnings | `src` + `test` | 0 | ídem | Gate `lint` (`clj-kondo`) | supera Go |
| 4 | Formato | todos los archivos correctos | `src` + `test` | 0 archivos sin formato | ídem | Gate `format` (`cljfmt check`) | supera Go |
| 5 | Determinismo (replay) | 100/100 casos iguales, con estado válido en Malli | 100 secuencias de 0–60 ticks | igualdad | ídem | `replay_test/escenario-7-replay` (JVM y Node) | supera Go |
| 6 | Los mismos gates en la CI de GitHub | en verde (confirmado por el líder el 2026-11-18) | PR #2 | job `ci / gates` en verde | ídem | Pestaña Checks del PR #2 | supera Go |

## Entregables del hito
| Entregable (del SCOPE) | Estado | Evidencia |
|---|---|---|
| `deps.edn`, `shadow-cljs.edn` (build `:test`) | entregado | scaffold + alias `:assets` |
| `config`, `campus`, `grid`, `specs`, `engine`, `lobby` (`.cljc`) | entregado | `src/cljc/razti/shared/`; grep de pureza limpio |
| `INITIAL-GRID` + `SPAWNS` con trazado aprobado | entregado (provisional, D2) | aprobado por el líder el 2026-11-18 con `target/grid-preview.png` |
| Escenarios BDD 1–7 + test de convenciones del mapa | entregado | `test/cljc/razti/shared/*_test.cljc` |
| CI de GitHub Actions | entregado | `.github/workflows/ci.yml` (ejecución: métrica 6) |
| `dev/assets.clj` + vista previa (conflictos #2, #3) | entregado | `map-campus.jpeg` 800×800, `player-sprites.png` 120×120 RGBA |
| `LICENSE` MIT + licencia de assets CC BY 4.0 | entregado | PR #1 |
| ADR-0001 | entregado | `docs/adr/0001-sprints-por-hito.md` |

## Criterios BDD del hito
| Escenario | Prueba automática | Resultado |
|---|---|---|
| 1 — Movimiento válido e inválido | `engine_test/escenario-1-movimiento` | verde (JVM + Node) |
| 2 — Detonación, destrucción de soft-block y eliminación | `engine_test/escenario-2-detonacion` | verde |
| 3 — Sala llena | `lobby_test/escenario-3-sala-llena` | verde |
| 4 — Reacción en cadena (R7) | `engine_test/escenario-4-cadena` | verde |
| 5 — Salida de la bomba propia (R9) y límite (R3) | `engine_test/escenario-5-salida-bomba` + `r3-limite-en-otra-celda` | verde |
| 6 — Empate | `engine_test/escenario-6-empate` | verde |
| 7 — Replay (test.check) | `replay_test/escenario-7-replay` | verde (100 casos) |

## Riesgos y aprendizajes que pesan en la decisión
- El trazado de la grilla es **provisional**: el ajuste fino depende del overlay del S02 y puede cambiar el equilibrio del mapa (zona suroeste muy bloqueada).
- Gates `build` y `uber` sin verificar hasta el S02/S03 (no hay cliente ni servidor); `uber` además necesita un ajuste para el sandbox.
- El riesgo principal del producto (WebSocket por red real) sigue sin probar: el S02 empieza por el esqueleto WAN.
- Pendientes que bloquean el S02: conflicto #5 (máximo de salas, inactividad), colores de jugador, Codespaces disponible.
- 8 P3 en el backlog; SEC-03 y SEC-04 (frontera) conviene resolverlos en el S02.

## Opciones
- **Go:** activar el M2 (S02 · Vertical jugable en local), empezando por el esqueleto WAN en Codespaces.
- **Pivot:** ajustar el plan del M2 (p. ej. rehacer el trazado antes del servidor, o cambiar el modo de prueba WAN) con una versión 1.2 del brief.
- **No-Go:** congelar el producto; el motor queda como entregable académico autocontenido.

## Veredicto del líder
| Veredicto | Motivo | Fecha | Líder |
|---|---|---|---|
| | | | |
