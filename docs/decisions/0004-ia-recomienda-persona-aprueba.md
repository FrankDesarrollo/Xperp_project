# 0004. La IA recomienda y una persona aprueba

- **Estado:** Propuesto
- **Fecha:** 2026-11-20
- **Autores:** Frank (con Claude Code)
- **Módulos afectados:** `ai` (políticas, agentes, bitácora), `contracts` (`AiDecision.impact`), `db/migration` (`V170`). Pendiente en `frontend` y `simulation` (ver Consecuencias).

## Contexto

La visión del producto se ajustó. Xperp debe ser un ERP inteligente que optimice la gestión financiera, administrativa y operativa sin que la IA se convierta en un gasto permanente ni reemplace el criterio humano. Hay tres requisitos:

1. **La IA no corre todo el tiempo.** Se activa con eventos relevantes, cuando se cumple una condición, cuando el usuario la pide o en revisiones programadas. El propio sistema detecta las condiciones, como un producto a punto de agotarse, sin necesidad de un modelo activo.
2. **La IA no toma decisiones financieras ni ejecuta acciones críticas por su cuenta.** Recomienda, explica por qué, indica el impacto económico u operativo y pide la aprobación de una persona autorizada.
3. **Que sea rentable.** El LLM se usa solo donde aporta valor.

El punto 1 ya se cumplía: había disparadores por eventos (`AiEventListeners`), un ciclo diario, reglas determinísticas primero, el LLM solo en excepciones con un tope diario (`LlmRouter`) y el modo `mock`.

El punto 2 no se cumplía. `PolicyDefaults` dejaba que la IA ejecutara sola (nivel 2) las OC de menos de $ 5.000.000, las reasignaciones con sobrecosto de hasta 10 %, las guías, las retenciones de pedidos y los parámetros de reorden. Además, la bitácora no guardaba el impacto de cada decisión en un formato que pudiera leer quien aprueba.

## Decisión

1. **Acciones protegidas.** `PolicyEngine.APPROVAL_REQUIRED_ACTIONS` define las acciones que nunca se ejecutan sin una persona: `create_purchase_order`, `reassign_purchase_order`, `cancel_purchase_order`, `propose_stock_transfer`, `adjust_inventory` y `apply_budget_plan`. Si una política dice nivel 2 o 3, el motor la trata como nivel 1. `PolicyAdminService` rechaza subirlas de nivel (`approval_required`), y la migración `V170` corrige las políticas que ya estaban guardadas.
2. **Quién aprueba.** Por defecto, Compras aprueba las OC de hasta $ 20.000.000, las reasignaciones con sobrecosto de hasta 10 % y las cancelaciones. Gerencia aprueba las OC de más de $ 20.000.000, las reasignaciones con sobrecosto mayor, los traslados, los ajustes de inventario, los planes de presupuesto y cualquier compra que exceda el presupuesto o el tope diario.
3. **Acciones que siguen siendo automáticas** (no comprometen dinero de compras ni cambian el valor del inventario):
   - Alertas, tareas de bodega, recalificación de proveedores, parámetros de reorden, resumen diario y sugerencias de promoción (nivel 0).
   - **Guías de transporte.** Son flete rutinario y ya cotizado, unas 130 por día. Si requirieran aprobación, el despacho se congelaría y `reserved` nunca bajaría.
   - **Retención de pedidos anómalos.** La retención es la medida de protección. Una persona libera el pedido o revierte la decisión.
4. **Impacto en cada recomendación.** `ActionProposal` lleva un `Impact` con el monto que compromete o libera, el efecto económico, el efecto operativo, qué pasa si se rechaza y el nivel de riesgo (`low`, `medium` o `high`). `ActionGateway` le agrega el efecto en el presupuesto de cada categoría (disponible antes y después, y el uso resultante). Se guarda en `ai_decisions.impact` (jsonb) y se expone en el contrato como `AiDecision.impact` (`DecisionImpact`, `BudgetImpact`).
5. **Cuándo actúa la IA.** No cambia: eventos, condiciones, revisiones programadas y solicitudes del usuario. El LLM solo interviene en empates, excepciones, anomalías, explicaciones y el asistente, con el tope de `llm_max_calls_per_day`.

## Alternativas consideradas

- **Aprobar todo, incluidas las alertas y las tareas de bodega.** La regla queda más simple, pero la operación se llena de clics sin riesgo real. Se descartó.
- **Mantener automáticas las OC pequeñas.** Contradice el requisito: es una decisión financiera.
- **Exigir aprobación también para guías y retenciones.** Congela el despacho y desactiva la protección contra pedidos anómalos. Se descartó tras revisarlo con el equipo.
- **Calcular el impacto en el frontend.** El frontend no tiene los datos de presupuesto y cobertura en el momento de la decisión, y la bitácora debe quedar completa para auditoría.

## Consecuencias

- La IA ya no gasta dinero sola. El tope diario de compras de la IA (`ai_daily_spend_cap`) pierde importancia como freno y queda como criterio para escalar a Gerencia.
- Hay más aprobaciones pendientes. Las aprobaciones vencen a las 24 horas simuladas (9.3), así que la demo necesita un aprobador presente o `SIM_AUTO_APPROVE=true` para correr sola.
- Las OC de un plan de presupuesto aprobado se presentan una por una para aprobación.
- Pendiente (no se tocó en este cambio):
  - `frontend`: regenerar `schema.d.ts`; mostrar `impact` en `DecisionCard` y `ApprovalCard`; actualizar `labels.ts`, el texto de niveles en IA → Políticas (el nivel 2 no aplica a acciones protegidas) y los handlers de MSW (`policy_reason` de ejemplo).
  - `simulation`: revisar `scenarios.yml` y `docs/demo-script.md`. Las OC del día 1, la reasignación del día 5 a PRV-09 y la del día 6 a PRV-01 ahora quedan pendientes de aprobación.
- Segunda fase prevista: análisis financiero (ingresos, gastos, margen, cuentas por pagar, flujo de caja proyectado) calculado por reglas, con la explicación de la IA solo bajo condición, en el cierre o a pedido, y un panel de consumo de la IA (llamadas, tokens y costo estimado).
