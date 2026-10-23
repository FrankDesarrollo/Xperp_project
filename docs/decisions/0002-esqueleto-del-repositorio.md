# 0002. Decisiones del esqueleto del repositorio

- **Estado:** Propuesto (lo aprueban los dos integrantes en el PR del esqueleto)
- **Fecha:** 2026-10-22
- **Módulos afectados:** `contracts`, `backend`, `partners-mock`, `frontend`, CI

## Contexto

Al montar el esqueleto de la sección 5 de `AGENTS.md` aparecieron detalles que el documento no cubre o que no funcionan tal como están escritos. Este ADR los registra para que los dos integrantes los revisen.

## Decisiones

### 1. El contrato se empaqueta antes de generar código

openapi-generator 7.26 no resuelve bien las referencias entre archivos cuando el contrato es OpenAPI 3.1. En lugar de usar los esquemas por su nombre, crea modelos duplicados como `Login200Response`. Con OpenAPI 3.0 no pasa, pero `AGENTS.md` pide 3.1.

**Decisión:** el contrato se sigue escribiendo dividido por módulo. En la fase `initialize`, Maven lo empaqueta con Redocly CLI en `contracts/dist/openapi.yaml`, que está ignorado por Git, y el generador lee ese archivo. Por eso el build del backend necesita Node.js; la CI lo instala y el Dockerfile lo empaqueta en una etapa con Node. El frontend no necesita este paso, porque `openapi-typescript` resuelve bien varios archivos.

**Convención:** todo esquema nuevo se registra en `components.schemas` de `openapi/openapi.yaml`, y los archivos de `paths/` lo referencian así: `../schemas/<modulo>.yaml#/<Nombre>`.

### 2. Dónde queda el código generado

Las interfaces y los modelos se generan en `com.xperp.shared.contract.api` y `com.xperp.shared.contract.model`, dentro de `target/generated-sources`. Como `shared` es un módulo abierto, cualquier `web/` puede implementar esas interfaces sin romper `ModularityTest`.

### 3. Interfaces con nombre de Spring Modulith

`api/` y `events/` de cada módulo llevan `@NamedInterface`. El resto de subpaquetes queda interno. `AGENTS.md` solo muestra `events/` en los módulos de dominio, pero `ai`, `simulation` y `partners` también publican eventos (tabla 7.5), así que también tienen `events/`. `partners` tiene además `client/` para los clientes HTTP.

### 4. partners-mock tiene su propio wrapper y su propio workflow

Cada proyecto Maven es independiente; no hay un POM padre. `partners-mock` trae su propio `mvnw` y un workflow `partners-mock.yml`, que la tabla 12.4 no menciona.

### 5. Migración V1

`V1__platform.sql` crea las tablas de la sección 6.1 y `event_publication` con el esquema de Spring Modulith 1.4. Hace falta para que la aplicación arranque, porque Hibernate valida el esquema. `users.warehouse_id` todavía no tiene FK: `warehouses` llega con la migración de inventario, que agregará la restricción.

### 6. google-java-format 1.35.0

La versión 1.37.0 falla al formatear con JDK 21. Se fija en 1.35.0 en los dos proyectos Java.

### 7. Versiones mayores más nuevas que las de AGENTS.md

A la fecha ya existen Spring Boot 4.x y SvelteKit 3.x. Se respeta lo que dice `AGENTS.md` (Boot 3.5.x y SvelteKit 2). Hay que revisar hasta cuándo tiene soporte Spring Boot 3.5 y decidir, con un ADR, si conviene migrar antes de que el proyecto crezca.

### 8. Otros detalles del frontend

- ESLint prohíbe usar `fetch` global fuera de `lib/api/` y `mocks/`, para que se cumpla la regla de la sección 13.2.
- Se agregaron `Dockerfile` + `nginx.conf` (puerto 3000 en Compose), `e2e/`, `playwright.config.ts`, `vitest-setup.ts`, `eslint.config.js` y `.prettierrc`.
- `schema.d.ts` está ignorado por Git y se regenera con `npm run gen:api`.

## Puntos de `AGENTS.md` que no cuadran

Se dejan señalados en lugar de improvisar (regla 17):

1. **Numeración de migraciones:** la sección 5.1 pone como ejemplo `V2__catalog.sql` y `V3__inventory.sql`, pero la sección 12.2 asigna bloques (A: `V100`–`V499`, B: `V500`–`V899`). Se recomienda seguir 12.2 y corregir el ejemplo de 5.1.
2. **Lombok** aparece en la sección 13.1, pero no en la tabla del stack (3.1).
3. **Swagger UI** aparece en la sección 15, pero ninguna dependencia de la tabla 3.1 lo provee. Se agregó springdoc (ver ADR 0001).
4. **CODEOWNERS:** falta definir quién es el integrante A y quién el B, y sus usuarios de GitHub.

## Consecuencias

- Hace falta Node.js 22 para compilar el backend en local.
- Cuando se acepte este ADR, se actualizan las secciones 5.1, 3.1 y 12.4 de `AGENTS.md` en el mismo PR.
