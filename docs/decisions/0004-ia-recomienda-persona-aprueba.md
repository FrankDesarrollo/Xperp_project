# 0004. La IA recomienda y una persona aprueba lo que mueve dinero

- **Estado:** Propuesto (lo revisan los dos integrantes)
- **Fecha:** 2026-11-20 (las dos versiones se integraron el 2026-11-21)
- **Autores:** Frank David Palma y Erick Albornoz, con Claude Code
- **Módulos afectados:** `ai`, `dashboard`, `notifications`, `contracts`, `frontend`, `db/migration` (V170 y V171)

## Contexto

La retroalimentación de la exposición pidió tres cosas:

1. **La IA no debe ser un gasto permanente.** No se quiere un modelo corriendo todo el tiempo; el
   sistema debe saber cuándo usar la IA: cuando pasa algo, cuando se cruza una condición, cuando
   alguien lo pide o en revisiones programadas.
2. **La IA no toma decisiones financieras ni ejecuta acciones críticas sola.** Recomienda, explica
   por qué, muestra el impacto económico u operativo y pide la aprobación de una persona autorizada.
3. **Ampliar el análisis a las finanzas:** ingresos, gastos, movimientos, presupuestos, existencias y
   rotación, no solo el inventario.

Así estaba el código antes de este cambio:

- El punto 1 ya se cumplía por dentro, pero no se veía. Las reglas resuelven casi todo; el LLM
  solo entra en empates, excepciones, anomalías, el resumen diario y el asistente, con tope de 30
  llamadas por día (`LlmRouter`). No hay ningún proceso de IA corriendo todo el tiempo.
- El punto 2 no se cumplía: las OC de menos de $ 5.000.000, las reasignaciones con sobrecosto de
  hasta el 10 % y las guías de despacho se ejecutaban solas (nivel 2).
- El punto 3 no existía: `finance` solo llevaba el presupuesto de compras y las cuentas por pagar.

Esto contradice partes de `AGENTS.md`: la frase de entrada («la IA toma decisiones operativas, las
ejecuta»), el principio 5 («opera solo»), los niveles por defecto de 8.4 y los umbrales de 9.2.

## Decisión

### 1. Regla de oro en el código, no en la configuración

`PolicyEngine.HUMAN_APPROVAL_ACTIONS` lista lo que mueve o compromete dinero o activos:
`create_purchase_order`, `reassign_purchase_order`, `cancel_purchase_order`,
`propose_stock_transfer`, `adjust_inventory`, `apply_budget_plan` y `create_shipment`. Esas
acciones siempre quedan en `pending_approval`, aunque alguien configure el nivel 2 o 3. IA →
Políticas no deja subirlas del nivel 1 (`PolicyAdminService` responde `approval_required`), y la
pantalla las muestra con candado.

- Las guías de despacho entran en la regla (lo decidió Erick): el flete es un gasto. Las aprueba
  Gerencia, en lote.
- Siguen solas las acciones que no comprometen dinero y se pueden deshacer: tareas de bodega,
  alertas, recalificar proveedores, parámetros de reorden, retener un pedido anómalo (es la acción
  conservadora: lo libera una persona), esperar una OC retrasada y los resúmenes.
- **Aprobación heredada:** cuando Gerencia aprueba un plan de presupuesto, las OC que caben en él
  se ejecutan sin pedir una segunda aprobación. `PolicyEngine` lo verifica contra la aprobación
  real del plan (que exista, que esté aprobada y que su rol cubra el de la OC) y guarda el vínculo
  en `ai_decisions.approved_in_decision_id`.
- Las migraciones bajan al nivel 1 las políticas de las empresas ya cargadas: V170 las seis
  primeras acciones y V171 las guías. No hace falta reiniciar la simulación.

### 2. Impacto calculado por el backend

`ImpactAssessor` calcula, antes de registrar la decisión, qué gasta, qué protege y qué arriesga
cada propuesta. Se guarda en `ai_decisions.impact` (jsonb) y sale en el contrato (`ImpactItem`).
La OC trae el monto, el uso del presupuesto disponible, el margen esperado, las ventas en riesgo si
no se compra hoy y la cobertura al recibir. La reasignación trae el sobrecosto o el ahorro y la
entrega. La guía, el flete, su peso en el pedido y lo que cuesta de más frente a la más barata. Hay
cifras equivalentes para traslados, ajustes, planes de presupuesto, retenciones y estancados. Lo
calcula el backend con sus datos y no el LLM: una propuesta del modelo pasa por el mismo cálculo y
el modelo no puede equivocarse con las cifras. Las reglas puras están en `ai/rules/Impact.java`.

### 3. Aprobación en lote

`POST /approvals/batch-approve` aprueba varias de una vez. Cada una pasa por las mismas reglas que
una sola: la que no le corresponde al rol, ya se resolvió o falla al ejecutarse queda en `failed`
sin frenar a las demás. La bandeja filtra por tipo («Guías de despacho», «Órdenes de compra»…),
muestra cuántas hay marcadas y el monto total. Las guías no mandan una notificación por cada una:
serían unas 130 por día.

### 4. Qué activó cada análisis y cuánto costó

Cada decisión dice qué la activó (`trigger_kind`): **event** (un pedido, un webhook, un conteo),
**condition** (stock bajo el de seguridad, presupuesto proyectado), **request** (lo pidió una
persona) o **schedule** (ciclo diario, cierre del día, revisión semanal). `TriggerKind` lo deduce de
`trigger_event`, y una prueba revisa que todo disparador escrito en el código esté clasificado.
`GET /ai/usage` cuenta las decisiones por disparador, cuántas resolvieron las reglas y cuántas el
LLM, las llamadas, los tokens, el costo estimado (con `LLM_PRICE_INPUT_PER_MTOK_USD` y
`LLM_PRICE_OUTPUT_PER_MTOK_USD`; si no se configuran, el costo queda vacío) y las acciones
financieras ejecutadas sin aprobación, que tienen que ser 0. Se ve en IA → Decisiones.

### 5. Finanzas: reglas que calculan y una IA que se activa cuando hace falta

- `GET /dashboard/finance` (módulo `dashboard`, SQL de solo lectura como dice el ADR 0003) entrega
  las cifras del mes: ventas, costo, margen, fletes, compras recibidas y comprometidas, ventas
  perdidas, cuentas por pagar (abiertas, por vencer en 7 días y vencidas), inventario a costo, en
  exceso y estancado, y el presupuesto por categoría con su proyección al cierre. La proyección suma
  a lo usado lo que se repondría al ritmo de la demanda, a costo, en los días que faltan. No reparte
  lo usado entre los días transcurridos, porque eso exageraría el primer día del mes.
- `FinanceRules` (reglas puras) saca los hallazgos: presupuesto que se va a pasar o cerca del tope,
  pagos vencidos o por vencer, capital inmovilizado, fletes altos, ventas perdidas y margen bajo.
  Cada uno trae su recomendación, que decide una persona.
- `FinancialAdvisor` corre la revisión. El LLM, si está, solo redacta el resumen
  (`financial-review.v1.st`). Corre en cuatro casos:
  - **a pedido:** «Analizar ahora» en Finanzas (`POST /ai/financial-reviews`);
  - **programada:** cada lunes y al cierre del mes;
  - **por condición:** los demás días, las reglas, sin LLM, miran si apareció un hallazgo grave
    que no se haya avisado en 7 días, y solo entonces corre la revisión;
  - **desde el asistente:** con la tool de lectura `get_financial_overview`.
- Nueva pantalla **Finanzas** para admin, gerencia, compras y el analista.

No se agregó ninguna dependencia.

## Alternativas consideradas

- **Dejar la regla como configuración** (el nivel 1 por defecto, editable): más flexible, pero la
  garantía dependería de que nadie la cambie. Se eligió fijarla en el código porque así lo pide la
  retroalimentación y porque una prueba puede demostrarla.
- **Guías automáticas** (la versión de Frank, por flete rutinario ya cotizado): menos trabajo para
  Gerencia y el despacho no se frena si nadie aprueba. Se descartó porque el flete es un gasto y la
  retroalimentación no hace excepciones; la aprobación en lote compensa el volumen. El costo es
  real: sin nadie que apruebe, los pedidos empacados esperan y `reserved` no baja.
- **Impacto en frases que arma cada agente** (la versión de Frank: efecto económico, operativo y
  riesgo si se rechaza): se lee bien, pero cada agente lo calcula a su manera y la interfaz no lo
  mostraba. Se eligió una lista de cifras que calcula un solo componente (`ImpactAssessor`) y que
  la interfaz pinta igual en todas las decisiones.
- **Que el LLM calcule el impacto en su razonamiento:** más simple, pero las cifras dependerían del
  modelo. En modo `mock` ni siquiera existirían.
- **Una tabla aparte para las revisiones financieras:** se usó `ai_decisions`, que ya guarda qué vio
  la IA, qué propuso y quién la activó, y así la revisión queda en la misma bitácora que el resto.

## Consecuencias

- La demo ya no «opera sola» en sentido estricto: sin nadie que apruebe, las OC y las guías esperan
  y vencen a las 24 horas simuladas. Para dejarla corriendo sin público está el aprobador simulado
  (`SIM_AUTO_APPROVE=true`), que ahora se presenta como una persona simulada y no como la IA. La
  medición con el mes completo está más abajo.
- Gerencia recibe en la bandeja unas 130 guías por día. Se aprueban en lote con el filtro «Guías de
  despacho».
- `AGENTS.md` se actualiza en el mismo PR: la entrada, los principios 1, 4 y 5, los escenarios de
  2.13, los flujos 4.1 y 4.2, 6.8, 7.2, 8.4, 8.5, 8.6, 8.9 y 8.11 (nueva), 9.1, 9.2 y 9.3, 10.2 y
  11.1.
- Pendiente fuera de este alcance: el modelo no tiene caja ni cartera de clientes, y las cuentas por
  pagar no se pagan (solo quedan pagadas las de contado). Los ingresos salen de las ventas de
  pedidos válidos.
- Los umbrales de `FinanceRules` (fletes 8 %, margen 18 %, capital inmovilizado 3 %, ventas
  perdidas 2 % y 5 %) son parámetros. Se contrastaron con la medición de abajo.

## Integración de las dos versiones (2026-11-21)

Frank y Erick implementaron este ADR al mismo tiempo, cada uno por su lado. La de Frank llegó a
`main` (`2d21c28` y `440d052`); la de Erick estaba sin commit y era más amplia (Finanzas, uso de la
IA, impacto en pantalla y aprobación en lote). Se integraron sobre `main` así:

- **De Frank quedan:** la migración `V170__ai_decision_impact.sql` sin cambios (ya estaba aplicada),
  `spring.flyway.out-of-order`, este nombre de archivo, el rediseño grafito + esmeralda del
  frontend, el contador de aprobaciones del menú y `live-refresh`.
- **De Erick quedan:** la regla con las guías incluidas, el impacto como lista (`ImpactItem`), la
  aprobación heredada de un plan, la aprobación en lote, `trigger_kind`, `GET /ai/usage`, Finanzas y
  la revisión financiera.
- **Se retiró:** el `Impact` por agente de Frank y los esquemas `DecisionImpact` y `BudgetImpact`.
  `V171` convierte el impacto guardado con ese formato en una lista vacía. Los casos de
  `ApprovalFirstPolicyTest` que seguían vigentes pasaron a `HumanApprovalRuleTest` (retener un
  pedido anómalo sigue siendo automático; el nivel 0 queda como sugerencia).
- **Para hablar entre los dos:** las reasignaciones con sobrecosto mayor al 10 %. En la versión de
  Frank las aprobaba Gerencia y en esta las aprueba Compras.

## Medición: noviembre completo, semilla 42, aprobador simulado

Dos corridas el 2026-11-21, del 1 al 30 de noviembre, con `LLM_PROVIDER=mock`, `SIM_AUTO_APPROVE=true`
y revisión en la base de datos de los 14 escenarios de la tabla 2.13 de `AGENTS.md`:

| | `main` de Frank (`440d052`) | Versión integrada |
|---|---|---|
| Escenarios cumplidos | 14 de 14 | 14 de 14 |
| Propuestas aprobadas por una persona | 139 | 3.708 (3.572 son guías) |
| Acciones financieras sin aprobación (`GET /ai/usage`) | 3.571 con la regla de esta versión (las guías salían solas) | 0 |
| Nivel de servicio | 99,8 % | 99,8 % |
| Unidades perdidas | 241 | 223 |
| Presupuesto usado | 77,5 % | 77,6 % |
| Decisiones con LLM | 65 de 7.359 | 72 de 7.363 |
| Revisiones financieras | — | 9 (5 los lunes, 3 por condición y el cierre) |

Las dos OC ejecutadas sin aprobación propia heredan la de un plan de presupuesto que Gerencia
aprobó (`approved_in_decision_id`). Cuidado personal cerró en 107,9 % en las dos corridas: lo que
no cabía llegó a Gerencia y la persona simulada lo aprueba todo. Con una persona real, ahí decide
Gerencia.
