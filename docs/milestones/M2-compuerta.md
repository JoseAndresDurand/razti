# Compuerta de validación — Hito M2: Vertical jugable en local

- **Producto:** RAZTI · **Proyecto:** razti · **Alias:** C02
- **Etapa del brief:** M2 · Vertical jugable en local (20/11–24/11; ejecutado el 18/11) · **Sprint de cierre:** S02 ★
- **Fecha de la compuerta:** 2026-11-18 · **Versión evaluada:** `m2` (commit `56fcb63`, `main`) · **Entorno:** local, Codespaces + Cloudflare Quick Tunnel y CI de GitHub
- **Documento de referencia:** `docs/01-SCOPE.md` (Hito M2, "Compuertas de validación") y MVP Scope Brief v1.1 §9 (con el cambio de C30 del 2026-11-18)

> Borrador generado por `/itera-retro`. **El veredicto lo dicta el líder**: este documento junta la evidencia, no decide.
> Después del veredicto: `/itera-scope --veredicto M2 go|pivot|no-go`.

## Hipótesis del hito
- El WebSocket funciona por la red real (Codespaces / túnel) y una partida completa de 4 jugadores es jugable en local sobre el motor del M1.

## Métricas frente a umbrales
| # | Métrica | Valor medido | Periodo / tamaño de muestra | Umbral Go | Umbral No-Go | Fuente de la evidencia | Lectura |
|---|---|---|---|---|---|---|---|
| 1 | Esqueleto WAN responde desde otra red | Eco por WebSocket ≈ 331 ms desde datos móviles, por Cloudflare Quick Tunnel desde el Codespace. `/health` por la URL del túnel: sin dato (no se reportó) | 2026-11-18 · 1 dispositivo | `/health` y eco por WebSocket en la URL pública | Sin No-Go automático → Pivot (decisión del 2026-11-17) | Checkpoint 1 (líder) | supera Go en el eco; `/health` sin dato por WAN (en verde en local y en la CI) |
| 2 | Arranque sin warnings y solo en `127.0.0.1:3000` | Log solo con la línea `RAZTI escuchando…`; `ss -tlnH` → `[::ffff:127.0.0.1]:3000` | 2026-11-18 | Sin warnings; solo `127.0.0.1` | ídem | Checkpoint 2 (líder) y `core_test/escenario-S02-1-binding-por-defecto` | supera Go |
| 3 | Partida de 4 bots | `bots_test` en verde: B1 gana, B2–B4 eliminados, sin hilos colgados | 49 pruebas / 495 aserciones | Verde | ídem | Gate `test` (local y CI) | supera Go |
| 4 | Clon limpio sigue el README | Job `clean-clone` en verde (instala, compila, arranca, `/health` y eco WS) | PR #5 | Verde | ídem | CI de GitHub (confirmado por el líder) | supera Go |
| 5 | Partida manual de 4 pestañas | Llega a un ganador | 2026-11-18 · 4 sesiones | Llega a un ganador | ídem | Checkpoint 3 (líder) | supera Go |

## Entregables del hito
| Entregable (del SCOPE) | Estado | Evidencia |
|---|---|---|
| Esqueleto WAN + `.devcontainer` (C30) | entregado | Checkpoint 1; `.devcontainer/` |
| Servidor (http-kit, Sente, reitit, salas, reloj de 20 Hz, seguridad §7) | entregado | `src/clj/razti/server/`; `core_test`, `rooms_test`, `sessions_test` |
| Cliente básico (lobby, canvas, sprites, bombas/explosiones/soft-blocks por código) | entregado | `src/cljs/razti/client/`; checkpoint 3 |
| Overlay de depuración de la grilla (C28) | entregado | Tecla G en `clojure -M:dev` |
| `bots_test.clj` | entregado | Métrica 3 |
| `README.md` (C29) | entregado | Seguido por los jobs `clean-clone` y `docker` |
| `Dockerfile` (usuario sin privilegios) | entregado | Job `docker` en verde (`id -u` ≠ 0 y smoke) |
| Job de clon limpio en la CI | entregado | Métrica 4 |

## Criterios BDD del hito
| Escenario | Prueba automática | Resultado |
|---|---|---|
| S02-1 — Salud y binding | `core_test/escenario-S02-1-*` | verde |
| S02-2 — Origen y CSRF | `core_test/escenario-S02-2-origen-y-csrf` | verde |
| S02-3 — Eco por WebSocket | `core_test/escenario-S02-3-eco` | verde |
| S02-4 — 4 bots completan una partida | `bots_test/escenario-S02-4-cuatro-bots` | verde |
| S02-5 — Límites del servidor | `rooms_test/escenario-S02-5-limites` | verde |
| Escenarios 1–7 del M1 | `test/cljc/razti/shared/*` (JVM y Node) | verde |

## Riesgos y aprendizajes que pesan en la decisión
- **Camino WAN cambiado** (C30, 2026-11-18): Codespaces + Cloudflare Quick Tunnel; el reenvío de puertos de Codespaces no sirve para el juego. Va al brief v1.2.
- **Movimiento celda por celda:** el líder pidió fluidez; es justo el contenido del M3 (C12).
- **Trazado de la grilla «pasable»:** ajuste fino pendiente.
- **Backlog P3** (8): tamaño máximo de mensaje WS, `Secure` en la cookie, checksums de instaladores, digest de imágenes, log del tick, arranque parcial, foco al cambiar de vista, `pr-str` del URI.
- Sin escaneo de Claude Security del hito todavía.

## Opciones
- **Go:** activar el M3 (S03 · Netcode y WAN): predicción, interpolación, HUD, e2e con Playwright y la partida WAN con 4 navegadores en ≥ 2 redes.
- **Pivot:** ajustar el M3 (p. ej. priorizar la fluidez y la partida WAN sobre el e2e) con una versión 1.2 del brief.
- **No-Go:** congelar el producto en la vertical local jugable.

## Veredicto del líder
| Veredicto | Motivo | Fecha | Líder |
|---|---|---|---|
| | | | |
