# Cambios de Frank

> **Actualización del 2026-11-21:** este documento describe la versión de Frank. Al integrarla con la de Erick cambiaron dos cosas: las guías de despacho también esperan aprobación (de Gerencia, en lote), y el impacto es una lista de cifras (`ImpactItem`, calculada por `ImpactAssessor`) en vez del `Impact` por agente y los esquemas `DecisionImpact` y `BudgetImpact`. De los pendientes de la sección 5 ya están hechos el impacto en `DecisionCard` y `ApprovalCard`, los textos de IA → Políticas y la segunda fase (Finanzas y el panel de uso de la IA). El detalle está en el [ADR 0004](decisions/0004-ia-recomienda-persona-aprueba.md), en la sección «Integración de las dos versiones».

Erick: aquí está todo lo que cambié, por qué y cómo funciona. Hay dos bloques:

1. **Backend: "la IA recomienda y una persona aprueba"** ([ADR 0004](decisions/0004-ia-recomienda-persona-aprueba.md)), en el commit `2d21c28`.
2. **Frontend: rediseño visual completo y mejoras de rendimiento.**

Al final hay una lista de lo que cambia para ti, cómo probarlo y qué quedó pendiente.

> Desde ahora yo llevo la coordinación del proyecto. Si algo de esto te bloquea o no estás de acuerdo, escríbeme antes de revertirlo.

---

## 1. Backend: la IA recomienda y una persona aprueba

### Por qué

Ajustamos la visión del producto. Xperp tiene que ser un ERP inteligente que ayude a manejar el dinero, los recursos y el inventario, sin que la IA sea un gasto permanente ni reemplace el criterio de las personas. En concreto:

- **La IA no corre todo el tiempo.** Se activa con un evento (una venta, un rechazo de OC), cuando se cumple una condición (stock bajo el punto de reorden, un conteo con diferencia, un alza de precio), en revisiones programadas (el ciclo diario) o cuando el usuario pregunta. Esto ya funcionaba así; ahora quedó escrito en `AGENTS.md`.
- **La IA no toma decisiones financieras sola.** Recomienda, explica por qué, muestra el impacto económico y operativo, y una persona autorizada aprueba.

Antes, `PolicyDefaults` dejaba que la IA **ejecutara sola** las OC de menos de $ 5.000.000 y las reasignaciones con sobrecosto de hasta 10 %. Eso ya no pasa.

### Qué acciones necesitan aprobación

`PolicyEngine.APPROVAL_REQUIRED_ACTIONS` (en `ai/policies/PolicyEngine.java`):

| Acción | Aprueba por defecto |
|---|---|
| `create_purchase_order` | Compras hasta $ 20.000.000; Gerencia por encima |
| `reassign_purchase_order` | Compras si el sobrecosto es ≤ 10 %; Gerencia si es mayor |
| `cancel_purchase_order` | Compras |
| `propose_stock_transfer` | Gerencia |
| `adjust_inventory` | Gerencia |
| `apply_budget_plan` | Gerencia |

Si una compra excede el presupuesto de la categoría o el tope diario de la IA, la aprobación sube a Gerencia.

**Siguen siendo automáticas** porque no comprometen dinero de compras: alertas, tareas de bodega, recalificación de proveedores, parámetros de reorden, resumen diario y sugerencias de promoción. Hay dos excepciones que discutimos a propósito:

- **Guías de transporte** (`create_shipment`): son unas 130 al día. Si hubiera que aprobarlas, el despacho se congelaría y `reserved` nunca bajaría.
- **Retención de pedidos anómalos** (`hold_sales_order`): retener es la medida de protección. Una persona libera el pedido o revierte la decisión.

### Cómo se garantiza

Hay tres capas, para que no dependa de que alguien configure bien las políticas:

1. **`PolicyEngine.evaluate`**: aunque una política diga nivel 2 o 3, una acción protegida termina en `pending_approval`. El kill switch y el modo sombra siguen teniendo prioridad.
2. **`PolicyAdminService.update`**: desde IA → Políticas no se puede subir una acción protegida a nivel 2 o 3. Responde con el error `approval_required`.
3. **Migración `V170__ai_decision_impact.sql`**: corrige las políticas que ya estaban guardadas en nivel 2 o 3.

### Impacto de cada recomendación

Cada decisión guarda ahora qué cuesta y qué pasa si se aprueba o se rechaza:

- **`ai/policies/Impact.java`** (record): `amount` (positivo compromete dinero, negativo lo libera), `economic`, `operational`, `riskIfRejected`, `riskLevel` (`low`, `medium` o `high`) y `budget`.
- **Quién lo calcula:** los agentes, cuando arman la propuesta (`.impact(...)` en `ActionProposal.Builder`):
  - `PurchaseProposals.impact(plan)`: monto, plazo de pago, unidades que repone y riesgo de quiebre (cobertura del SKU más justo frente al tiempo de entrega del proveedor).
  - `ExceptionAgent`: sobrecosto de reasignar, costo de esperar o dinero que libera cancelar.
  - `TransferAgent`, `InventoryAgents` (ajuste por conteo), `BudgetPlanner`, `FulfillmentAgents` (flete y retención).
- **`ActionGateway`** le agrega el efecto en el presupuesto de cada categoría (`PolicyEngine.budgetImpact`): disponible antes, disponible después y porcentaje de uso.
- **Dónde se guarda:** en la columna nueva `ai_decisions.impact` (jsonb).
- **En el contrato:** `AiDecision.impact`, con los esquemas `DecisionImpact` y `BudgetImpact` en `contracts/openapi/schemas/ai.yaml`. Es opcional, así que no rompe nada.

Ejemplo real de la API:

```json
"impact": {
  "amount": "14296800.00",
  "economic": "Compromete $ 14.296.800 con Higiene Total Distribuciones S.A.S. (pago a 60 días).",
  "operational": "Repone 888 und de 1 SKU en CAL-01; llega en 5 días.",
  "risk_if_rejected": "Papel higiénico x 12 rollos cubre 3,8 días; el pedido tarda 5 días en llegar. Si no se aprueba hoy, habrá quiebre y ventas perdidas.",
  "risk_level": "high",
  "budget": [{ "category": "Aseo del hogar", "available_before": "130000000.00", "available_after": "115703200.00", "usage_pct_after": 11.0 }]
}
```

### Flyway con `out-of-order`

Al levantar el backend con `V170`, Flyway lo rechazaba porque tus `V500` y `V510` ya estaban aplicadas y 170 < 510. Esto le pasa a cualquier migración nueva del bloque de A (V100–V499) cuando ya hay migraciones del bloque de B. Lo resolví con `spring.flyway.out-of-order: true` en `application.yml`. No hace falta borrar la base.

### Pruebas

- `ApprovalFirstPolicyTest` (9 pruebas nuevas): ninguna acción protegida se ejecuta sola, lo operativo sigue siendo automático, el kill switch y el modo sombra tienen prioridad, y el impacto se guarda y se lee bien.
- Pasan las 56 pruebas del módulo `ai`, `ModularityTest`, Spotless y el lint de Redocly.

---

## 2. Frontend: rediseño y rendimiento

### Sistema visual «grafito + esmeralda»

Todo sale de `src/lib/styles/tokens.css`, igual que antes; no hay colores sueltos en los componentes.

- **Neutros puros** (sin el tinte verdoso de antes). El **esmeralda** solo aparece en acciones y en lo activo, y el **violeta** queda para la IA.
- Mismos tokens en tema claro y oscuro.
- **Tokens nuevos:**
  - Colores: `--color-surface-hover`, `--color-border-subtle`, `--color-border-strong`, `--color-primary-soft`, `--color-text-subtle` y `--color-track`.
  - Movimiento: `--ease-out`, `--duration-slow`, `--duration-reveal` y `--stagger`.
  - Tipografía: `--text-3xl`, `--tracking-tight`, `--tracking-wide` y `--weight-semibold`.
- **Utilidades nuevas** (`utilities.css`): `.reveal` (entrada escalonada con `style:--i="n"`), `.eyebrow` (rótulo pequeño en mayúsculas) y `.tabular`.
- **Componentes base** unificados:
  - `Button`, `Badge` y `EmptyState`.
  - `Field`: borde más firme y anillo de foco esmeralda.
  - `Tabs`: se quitó la barra de desplazamiento que aparecía sola.
  - Tablas: cabecera discreta y fila resaltada al pasar el puntero.
- **Sin emojis:** `StockBadge` usa íconos Lucide; `STOCK_STATUS` en `labels.ts` ya no tiene campo `icon`.

### Estructura (`routes/(app)/+layout.svelte`)

- **Menú agrupado** en Operación, Inteligencia artificial y Sistema. Las etiquetas ya no llevan el prefijo "IA ·".
- **Ítem activo**: fondo gris suave y una barra esmeralda a la izquierda. Antes iba en verde menta lleno, que en oscuro casi no se leía.
- **Aprobaciones** muestra cuántas hay pendientes, con `src/lib/stores/approvals.svelte.ts`:
  - Pide `GET /approvals?status=pending&mine=true&size=1` y solo vuelve a pedir cuando llega `approval.requested` o `ai.decision`, agrupando los eventos.
  - El tablero usa el mismo conteo, así que el menú y el tablero nunca muestran números distintos.
  - La pantalla de aprobaciones lo actualiza al aprobar o rechazar.
- Avatar con iniciales, barra superior translúcida, reloj simulado en una "píldora" y botón «Preguntar a la IA» más discreto.
- **Login rediseñado**, con el mensaje del ADR 0004: "La IA analiza y recomienda. Tu equipo decide.". Los usuarios demo aparecen como selector en dos columnas.

### Tablero (`routes/(app)/tablero/+page.svelte`)

Se reorganizó por jerarquía:

1. **Saludo y botón «Revisar aprobaciones»**, con el número de pendientes.
2. **Franja «Requiere atención»**: aprobaciones pendientes, quiebres y OC atrasadas, cada una con su enlace. Si no hay nada, dice "Todo en orden".
3. **Cuatro indicadores principales** (`components/domain/MetricCard.svelte`): ventas del mes con tendencia, nivel de servicio con tendencia, existencias sanas con barra y presupuesto con barra y marca del 85 %. El estado va en texto ("En meta", "Atención"), no solo en color.
4. **Franja de secundarios**: margen, OC abiertas, decisiones de la IA, valor del inventario y sobrecosto.
5. **Gráficas**: ventas diarias (curva suave con degradado) y estado del inventario en una barra segmentada.
6. **Listas**:
   - Recomendaciones de hoy.
   - Presupuesto por categoría, con barras propias en vez de ECharts.
   - Bitácora de la IA como línea de tiempo, con el monto del impacto.
   - **Alertas agrupadas por tipo**: antes eran 11 filas casi iguales; ahora es una sola: «Anomalía de venta · 11».
   - Ventas en vivo.

Las tendencias usan **solo días cerrados**. Antes, la curva caía a cero al final porque incluía el día en curso.

### Animaciones (sin dependencias nuevas)

- `components/ui/AnimatedNumber.svelte`: los números cuentan hasta su valor con `Tween` de `svelte/motion`. El lector de pantalla solo lee el valor final.
- `components/charts/Sparkline.svelte`: mini gráfica en SVG que se dibuja al aparecer, con `clip-path`.
- Barras que crecen, entrada escalonada (`.reveal`) y ventas en vivo que entran deslizándose (`fly` + `flip`).
- Con "reducir movimiento" activado en el sistema, `base.css` deja todo quieto.

### Rendimiento

- **ECharts por módulos** (`components/charts/echarts.ts`): solo líneas, barras, rejilla, tooltip, leyenda y el renderer SVG. El archivo bajó de **1,13 MB a 544 KB**, y solo se descarga en pantallas con gráficas. Si una gráfica nueva necesita otra pieza (`markLine`, `pie`, `dataZoom`…), se registra en ese archivo.
- **`src/lib/utils/live-refresh.ts`** centraliza las recargas en vivo:
  - Agrupa ráfagas de eventos en una sola recarga.
  - Con la pestaña oculta no pide nada, y al volver recarga una vez si algo cambió mientras tanto.
  - Admite una condición (`when`), por ejemplo, recargar solo en la primera página.
  - Lo usan Tablero (como mucho cada 5 s), Inventario, Ventas, Compras, Despachos, Bodega y Aprobaciones.
  - Compras recargaba con **cada** evento de OC sin agruparlos.

  Uso:

  ```ts
  onMount(() => {
  	void loader.run(load);
  	return liveRefresh(['sale.created'], () => void load(), { when: () => pageNo === 1 });
  });
  ```
- MSW (293 KB) ya se importaba de forma dinámica y solo con `PUBLIC_API_MOCK=true`. Lo verifiqué: no se descarga en uso normal.

`svelte-check` y ESLint pasan sin errores ni advertencias, y las 27 pruebas de Vitest también.

---

## 3. Qué cambia para ti

- **Demo y guion** (`scenarios.yml`, `docs/demo-script.md`): las OC del día 1 y las reasignaciones de los días 5 (PRV-09) y 6 (PRV-01) **ahora esperan aprobación**. Las aprobaciones vencen a las 24 horas simuladas, así que en la demo alguien tiene que aprobar a tiempo o hay que usar `SIM_AUTO_APPROVE=true`. Si nadie aprueba, las OC vencen y el inventario se queda sin reponer.
- **Tipos del frontend**: corre `npm run gen:api` para tener `impact` en `AiDecision`.
- **Al crear una pantalla o componente**: usa los tokens nuevos y `liveRefresh` en vez de `live.on` con un `setTimeout` a mano.
- **Si tocas el menú**: los ítems están en `groups`, dentro de `+layout.svelte`.

## 4. Cómo probarlo

```bash
docker compose up -d --build        # backend aplica V170 solo
# o en desarrollo:
cd frontend && npm install && npm run gen:api && npm run dev
```

1. Entra con `admin@laceiba.demo` / `Xperp2026*`.
2. Inicia la simulación. A las 06:00 simuladas el ciclo diario recomienda OC, que **no** se envían solas.
3. En **Aprobaciones** (con el contador en el menú) cada OC muestra su impacto. Al aprobarla, se envía al proveedor.
4. `GET /api/v1/ai/decisions` devuelve el campo `impact`.

## 5. Pendiente

- **Mostrar el impacto en `DecisionCard` y `ApprovalCard`.** Ya llega en la API; falta pintarlo: monto, riesgo y presupuesto antes y después.
- **Actualizar** `labels.ts` (`LEVEL_LABEL`), el texto de niveles en IA → Políticas (el nivel 2 no aplica a acciones protegidas) y el ejemplo de MSW (`policy_reason`).
- **Ajustar** `scenarios.yml` y `docs/demo-script.md` a las aprobaciones.
- **Segunda fase:** análisis financiero (ingresos, gastos, margen, cuentas por pagar, flujo de caja proyectado) calculado por reglas, con la explicación de la IA solo bajo condición o a pedido. También un panel de consumo de la IA: llamadas, tokens y costo estimado.
- **Llevar el estilo nuevo** al resto de pantallas por dentro (detalles, cajones, formularios de Compras e Inventario). Los componentes base ya lo tienen.

Todo está registrado también en `CHANGELOG.md` (sección `[Sin publicar]`).
