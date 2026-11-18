# TDD — RAZTI · Índice

**Regla de lectura:** `/itera-build` lee siempre `00-core.md` y **solo** la sección del sprint `[EN CURSO]` en el archivo de su hito. Una sección `[POR ESPECIFICAR]` no se construye: primero `/itera-tdd-unify --sprint S0x`.

**Fuentes:** `AGENTS.md` · `docs/01-SCOPE.md` (brief v1.1 + decisiones del 2026-11-17) · `docs/adr/0001-sprints-por-hito.md`. Sin `02-BRAND-BRIEF.md` ni `03-UI-INSPIRATION.md`: etapas E4 y E5 omitidas por el brief (§10).

## Mapa de secciones

| § | Contenido | Archivo |
| :--- | :--- | :--- |
| 1 | Metadatos, hito activo, frontera de alcance, dependencias permitidas, resoluciones, datos bloqueantes, historial | `00-core.md` |
| 2 | Árbol de archivos del sprint | `M<n>.md` → sección del sprint |
| 3 | Tokens visuales | `00-core.md` |
| 4 | Especificación módulo por módulo | `M<n>.md` → sección del sprint |
| 5 | Contratos de datos y esquemas Malli | `M<n>.md` → sección del sprint |
| 6 | Presupuesto de rendimiento | `00-core.md` |
| 7 | Seguridad y entorno | `00-core.md` |
| 8 | Gates (comandos) | `00-core.md` |
| 8 | Escenarios BDD del sprint | `M<n>.md` → sección del sprint |
| 9 | Checklist de implementación | `M<n>.md` → sección del sprint |

## Hitos y sprints

| Hito | Sprint | Contenido | Estado | Archivo |
| :--- | :--- | :--- | :--- | :--- |
| M1 · Motor isomórfico | S01 | config, campus, grid, specs, engine, lobby; BDD 1–7; test del mapa; `dev/assets.clj` + vista previa; CI | `[HECHO]` | `M1.md` |
| M2 · Vertical jugable en local | S02 | Esqueleto WAN, servidor, cliente básico, bots, README, Dockerfile, clon limpio | `[HECHO]` | `M2.md` |
| M3 · Netcode y WAN | S03 | Predicción, HUD, e2e, build/uber, Docker sin root, partida WAN | `[POR ESPECIFICAR]` | `M3.md` (no creado) |
