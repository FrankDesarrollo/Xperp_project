# 0003. Desviaciones de la implementación respecto a AGENTS.md

- **Estado:** Propuesto (lo revisan los dos integrantes)
- **Fecha:** 2026-10-23
- **Módulos afectados:** `shared`, `inventory`, `sales`, `purchasing`, `logistics`, `partners`, `dashboard`, `ai`, `simulation`, `contracts`, `docker-compose.yml`

## Contexto

Cuando se implementaron las fases 1 a 7, algunos puntos de `AGENTS.md` no funcionaban tal como están escritos, o el documento no los cubría. Este ADR los reúne en un solo lugar para que el equipo los apruebe o los corrija. No se agregó ninguna dependencia: todas estaban en el ADR 0001.

## Decisiones

### 1. Punto de reorden con un día de revisión

`AGENTS.md` 8.1 define `ROP = demanda × lead_time + SS`. El ciclo de reabastecimiento corre una vez al día, así que un SKU puede bajar del punto de reorden justo después de la revisión y esperar casi un día sin que nadie lo pida. **Decisión:** `ROP = demanda × (lead_time + 1) + SS` (`ReorderRules.REVIEW_PERIOD_DAYS = 1`), que es la fórmula de revisión periódica. Las pruebas lo cubren.

### 2. Webhooks: `partners` traduce y el dueño del dato publica

En 7.5, `partners` publica `PurchaseOrderStatusChanged` y `ShipmentStatusChanged`. Pero `partners` no conoce el estado anterior de la OC ni puede validar sus transiciones. **Decisión:** `partners` publica `SupplierUpdateReceived` y `CarrierUpdateReceived` con lo que llegó del webhook. `purchasing` y `logistics` aplican el cambio y publican los eventos de 7.5.

### 3. Eventos nuevos y eventos que no existen

Se agregaron `SalesOrderReleased`, `SalesOrderCancelled`, `PurchaseOrderDelivered`, `WarehouseTaskCreated` y `AlertRaised`. `StockReserved` y `StockReleased` no existen: el tablero lee el inventario y el cambio sale por SSE (`inventory.changed`). El catálogo actualizado, con los campos de cada evento, está en `contracts/events.md`.

### 4. La simulación llama a la IA a horas fijas

En 7.5, la IA escucha `SimulationDayStarted` y `SimulationDayEnded`. Como esos listeners son asíncronos, el pronóstico (05:00) y el reabastecimiento (06:00) podían correr en otro orden o encimarse con el avance rápido del reloj, y entonces la misma semilla ya no daba siempre el mismo resultado. **Decisión:** `SimulationEngine` llama a `AiFacade` en orden en cada hora programada, y `simulation_job_runs` evita que un trabajo se repita. Los eventos `SimulationDay*` se siguen publicando para quien los necesite.

### 5. Interfaces de extensión (SPI) para no crear ciclos entre módulos

`inventory` necesita saber cuánto hay en tránsito (OC abiertas), pero `purchasing` ya depende de `inventory`. Lo mismo pasa con `sales`, que debe preguntarle a `ai` si retiene un pedido anómalo. **Decisión:** `inventory.api.InTransitProvider` lo implementa `purchasing`, y `sales.api.SalesOrderScreener` lo implementa `ai`. Las dependencias quedan en una sola dirección y `ModularityTest` pasa.

### 6. El tablero lee con SQL

`dashboard` calcula los KPI con consultas SQL de solo lectura (`DashboardQueriesImpl`, con `JdbcTemplate`) sobre las tablas de los demás módulos. Pasar por los servicios obligaría a traer miles de filas para agregarlas en memoria. Es la segunda excepción a la regla de pasar por servicios, después de `HistoryGenerator`. Solo lee, siempre filtra por `company_id` y no lo usa ningún otro módulo.

### 7. Multiempresa en webhooks y en el reloj

Los webhooks y `GET /simulation/clock` llegan sin JWT. **Decisión:** `TenantFilter` les asigna la empresa demo (`TenantDirectory.defaultCompanyId()`). Para pasar a producto comercial, cada socio tendría que llevar su `company_id` en la firma o en la URL. Además, los listeners asíncronos reciben el tenant y la hora simulada a través de `TenantTaskDecorator`.

### 8. `created_at` usa la hora simulada

`BaseEntity` toma `created_at` y `updated_at` de `ClockHolder` (el `SimClock`) para que la bitácora, los KPI y las notificaciones coincidan con el día simulado. En producción el reloj simulado es el reloj real, así que no cambia nada.

### 9. Ampliaciones del contrato

Se agregaron endpoints que el tablero y la demo necesitan: `POST /simulation/advance` (adelanta horas con el reloj en pausa), `GET /simulation/scenarios`, `GET /ai/alerts` y filtros extra como `exclude_type` en decisiones o `mine` en aprobaciones. `partners-mock` expone `/admin/reset`, `/admin/scenarios` y `/admin/queue`, que están documentados en `contracts/partners.yaml`.

### 10. Puerto de PostgreSQL configurable

`docker-compose.yml` publica la base en `${POSTGRES_PORT:-5432}`. En equipos donde otro Postgres ya ocupa el 5432 (por ejemplo, uno de WSL) se usa `POSTGRES_PORT=5433` y `DB_URL` con ese puerto.

## Alternativas consideradas

- Seguir `AGENTS.md` al pie de la letra en los puntos 2 y 4: se probó, y salieron transiciones de estado inválidas y resultados distintos entre corridas con la misma semilla.
- Un read model propio para el tablero, alimentado por eventos: es más correcto, pero cuesta más de lo que vale en un MVP académico.

## Consecuencias

- Si el equipo aprueba este ADR, `AGENTS.md` (secciones 7.5, 8.1 y 7.2) se actualiza en el mismo PR para que el documento y el código no se contradigan.
- Para una empresa real hace falta resolver la multiempresa en los webhooks (punto 7).
