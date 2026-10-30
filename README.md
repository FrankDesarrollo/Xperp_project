# Xperp

Xperp (Expert ERP) es un ERP en el que la IA toma decisiones operativas, las ejecuta y les hace seguimiento. El caso de uso es una distribuidora de productos de consumo masivo, Distribuidora La Ceiba S.A.S., que trabaja con datos demo.

- **Contexto completo del proyecto:** [`AGENTS.md`](AGENTS.md). Es la fuente de verdad para el equipo y para cualquier agente de IA.
- **Historial de cambios importantes:** [`CHANGELOG.md`](CHANGELOG.md).
- **Decisiones de arquitectura (ADR):** [`docs/decisions/`](docs/decisions/).

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21 (Temurin recomendado) |
| Node.js | 22 o superior |
| Docker Desktop | Con Docker Compose v2 |

Maven no hace falta instalarlo: cada proyecto Java trae su wrapper (`mvnw`). El build del backend usa Node.js para empaquetar el contrato OpenAPI.

## Puesta en marcha

```bash
cp .env.example .env

# Infraestructura
docker compose up -d db mailpit partners-mock

# Backend (carga la empresa demo la primera vez)
cd backend && ./mvnw spring-boot:run

# Frontend
cd frontend && npm install && npm run gen:api && npm run dev
```

- App: http://localhost:5173 (entrar con `admin@laceiba.demo` / `Xperp2026*`)
- API y Swagger UI: http://localhost:8080/swagger-ui.html
- Correos enviados a proveedores y bodega: http://localhost:8025

Para levantar todo en contenedores: `docker compose up -d --build`.

Para trabajar el frontend sin backend, usa los mocks de MSW con `PUBLIC_API_MOCK=true npm run dev`.

Si otro PostgreSQL ya ocupa el puerto 5432 (por ejemplo, uno de WSL), pon `POSTGRES_PORT=5433` en `.env` y ajusta `DB_URL` a ese puerto.

## La demo

1. Entra como `admin@laceiba.demo` y abre **Simulación**.
2. Pulsa **Iniciar**. Con 20 s por día, el mes simulado dura unos 10 minutos. Con el reloj en pausa, **Avanzar horas** salta días completos.
3. La IA reabastece, asigna tareas, despacha y responde a rechazos, retrasos y anomalías. Cada decisión queda en **IA → Decisiones**.
4. Las OC de $ 5.000.000 o más, los traslados y los ajustes de inventario esperan aprobación en **IA → Aprobaciones**, con `compras@` o `gerencia@`. Si nadie las aprueba, vencen a las 24 horas simuladas.

Para dejar la simulación corriendo sola, levanta el backend con `SIM_AUTO_APPROVE=true`, que activa un aprobador virtual. El guion minuto a minuto está en [`docs/demo-script.md`](docs/demo-script.md).

Con `LLM_PROVIDER=mock` (el valor por defecto) la demo funciona completa sin claves. Para usar un LLM real, define `LLM_PROVIDER` (`anthropic`, `openai` u `ollama`), `LLM_MODEL` y `LLM_API_KEY`.

## Estructura

```
├── AGENTS.md            # contexto del proyecto (fuente de verdad)
├── CHANGELOG.md         # registro de cambios importantes
├── contracts/           # contrato OpenAPI 3.1 (por módulo), API de partners-mock y eventos
├── docs/                # arquitectura, modelo de datos, guion de la demo y ADR
├── backend/             # Spring Boot 3.5 + Spring Modulith (monolito modular)
├── partners-mock/       # proveedores y transportadoras simulados
├── frontend/            # SvelteKit 2 (SPA) + Svelte 5
└── docker-compose.yml   # PostgreSQL, Mailpit, servicios y Ollama (opcional)
```

El detalle de cada carpeta está en la sección 5 de `AGENTS.md`.

## Comandos de calidad

| Proyecto | Comando | Qué hace |
|---|---|---|
| contracts | `npx @redocly/cli@2.69.0 lint` (dentro de `contracts/`) | Lint del contrato |
| backend | `./mvnw verify` | Empaqueta el contrato, genera interfaces, compila, Spotless, pruebas y `ModularityTest` |
| backend | `./mvnw spotless:apply` | Formatea el código Java |
| partners-mock | `./mvnw verify` | Compila, Spotless y pruebas |
| frontend | `npm run check` | `svelte-check` con TypeScript estricto |
| frontend | `npm run lint` / `npm run format` | Prettier y ESLint |
| frontend | `npm run test` | Vitest |
| frontend | `npm run test:e2e` | Playwright (flujos clave, sobre MSW) |

CI (GitHub Actions) corre lo mismo en cada PR según las carpetas que cambien.

## Flujo de trabajo

- `main` está protegida y solo recibe cambios por PR con 1 aprobación y la CI en verde.
- Las ramas salen de `main` con el formato `feat/<modulo>-<descripcion>`, `fix/...`, `contract/...`, `docs/...` o `chore/...`.
- Los commits siguen Conventional Commits, por ejemplo `feat(purchasing): reasignar OC rechazada`.
- Un cambio importante se anota en `CHANGELOG.md` dentro del mismo PR.

Los detalles están en la sección 12 de `AGENTS.md`.
