# ADR-0001: Un sprint por hito, de duración variable

- **Estado:** Aceptado
- **Fecha:** 2026-11-17
- **Decide:** líder del proyecto (MVP Scope Brief v1.1, §10)

## Contexto

El workflow IteraStack fija la regla "sprint = 1 semana": los hitos se dividen en sprints semanales y el sprint ★ cierra el hito.

RAZTI es un MVP académico con un apetito de **2 semanas y 3 hitos** (brief §1, §9). Los hitos duran 3, 5 y 6 días corridos (M1 del 17 al 19/11, M2 del 20 al 24/11, M3 del 25 al 30/11). Si se aplicara la regla semanal, los sprints no coincidirían con los hitos: el M1 ocuparía media semana, el M2 quedaría partido entre las dos semanas y las compuertas caerían a mitad de un sprint.

## Decisión

Cada hito tiene **un solo sprint**, que dura lo mismo que el hito y lo cierra (★):

| Sprint | Hito | Días | Fechas |
| :--- | :--- | :--- | :--- |
| S01 | M1 · Motor isomórfico | 3 | 17/11–19/11 |
| S02 | M2 · Vertical jugable en local | 5 | 20/11–24/11 |
| S03 | M3 · Netcode y WAN | 6 | 25/11–30/11 |

Lo demás del workflow no cambia: un solo sprint `[ACTIVO]`, una rama `sprint/S0x-…` por sprint, auditoría y gates al cierre, y la compuerta del hito con el veredicto del líder.

## Alternativas consideradas

- **Sprints semanales (regla del workflow):** dos sprints de 7 días que no coinciden con los hitos. Se descarta: las compuertas del §9 quedarían a mitad de un sprint.
- **Dividir el M3 en dos sprints** (netcode / e2e + WAN): se descarta porque 6 días caben en un sprint y se mantiene la simetría hito = sprint. Se puede retomar con `/itera-scope --replan` si el M3 se retrasa.

## Consecuencias

- Los sprints cortos (3 días) dejan poco margen: un retraso en el S01 empuja directamente el inicio del M2.
- Las métricas del workflow que suponen sprints semanales (velocidad, reportes semanales) deben leerse por sprint, no por semana.
- Si el brief cambia de hitos o de apetito, este ADR se revisa junto con `docs/01-SCOPE.md`.
