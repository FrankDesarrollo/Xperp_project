# Registro de cambios

Aquí se anotan los cambios importantes del proyecto: estructura, dependencias, contratos, migraciones, decisiones de arquitectura y funcionalidades nuevas. El detalle fino queda en el historial de Git.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen [SemVer](https://semver.org/lang/es/).

**Cómo registrar un cambio:** en el mismo PR, agrega una línea en `[Sin publicar]` bajo la categoría que corresponda (Agregado, Cambiado, Corregido, Eliminado). Indica el módulo entre corchetes y, si aplica, enlaza el ADR.

## [Sin publicar]

### Agregado

- **[repo]** Esqueleto inicial del repositorio según la sección 5 de `AGENTS.md`: `contracts/`, `docs/`, `backend/`, `partners-mock/`, `frontend/`, `docker-compose.yml`, `.env.example`, `.editorconfig`, `.gitattributes` y `.gitignore`.
- **[repo]** `CLAUDE.md`, que remite a `AGENTS.md`; este `CHANGELOG.md`; y la carpeta de ADR con `0001-stack.md` y `0002-esqueleto-del-repositorio.md`.
- **[ci]** Workflows de GitHub Actions: `contracts.yml` (lint y bundle con Redocly), `backend.yml`, `partners-mock.yml` y `frontend.yml`. También la plantilla de PR y `CODEOWNERS`, con marcadores de usuario pendientes.
- **[contracts]** Contrato OpenAPI 3.1 base, dividido por módulo (`paths/` y `schemas/`). Incluye seguridad JWT, los esquemas comunes (`Error`, `PageMeta`, `Money`) y los endpoints `POST /auth/login` y `GET /auth/me`.
- **[contracts]** `partners.yaml` con la API de proveedores y transportadoras simulados, y `events.md` con el catálogo de eventos de dominio y SSE, cuyos campos están por definir.
- **[backend]** Proyecto Spring Boot 3.5.16 con Java 21 y Maven Wrapper. Trae Spring Modulith 1.4, Spring AI 1.1 con proveedor configurable (Anthropic, OpenAI u Ollama; `mock` por defecto), JPA, Flyway, PostgreSQL, Security, OpenPDF, Datafaker, Commons Math y springdoc.
- **[backend]** Paquetes de los 12 módulos con sus `package-info.java`. Cada módulo es un `@ApplicationModule`; `shared` es un módulo abierto; `api/` y `events/` son `@NamedInterface`.
- **[backend]** `ModularityTest`, que verifica los límites entre módulos y genera la documentación de módulos.
- **[backend]** Generación de interfaces desde el contrato con openapi-generator. El contrato se empaqueta antes con Redocly.
- **[backend]** Spotless con google-java-format, que se valida en `verify`, y failsafe para las pruebas `*IT`.
- **[backend]** Migración `V1__platform.sql` con `companies`, `users`, `holidays`, `simulation_state` y `event_publication`.
- **[backend]** `application.yml`, `application-dev.yml` y `application-demo.yml` con todas las variables de `.env.example`.
- **[partners-mock]** Proyecto Spring Boot con sus paquetes (`suppliers`, `carriers`, `clock`, `scenarios`, `webhook`) y `partners.yml` con el comportamiento de las tablas 2.6 y 2.7.
- **[frontend]** SvelteKit 2.70 con Svelte 5 (runes forzados), `adapter-static` como SPA, TypeScript estricto, ESLint, Prettier, Vitest, Testing Library, Playwright y MSW.
- **[frontend]** Rutas en español de la sección 5.3 con páginas provisionales, un layout con menú lateral y barra superior, y el cliente `openapi-fetch` con token.
- **[frontend]** Stores con runes, tokens de diseño con tema claro y oscuro, utilidades de formato COP y de fecha con sus pruebas, y permisos por rol.
- **[infra]** Docker Compose con PostgreSQL 16, Mailpit, backend, partners-mock, frontend (nginx) y Ollama opcional (perfil `ollama`). Hay un Dockerfile multietapa por servicio.

- **[contracts]** Contrato OpenAPI completo de los 13 módulos (auth, catálogo, inventario, ventas, compras, logística, finanzas, IA, asistente, tablero, notificaciones, simulación y webhooks).
- **[backend]** Migraciones V2, V100–V160, V500 y V510 con todo el modelo de datos de la sección 6.
- **[backend]** Módulos de dominio completos: catálogo, inventario (reservas con bloqueo, kardex, conteos, traslados, ventas perdidas), ventas, compras (OC, ofertas, recepciones, PDF), logística (tareas, guías, transportadoras), finanzas (presupuesto y cuentas por pagar), notificaciones (app, correo a Mailpit, SSE) y tablero (KPIs y series).
- **[ai]** Reglas de reorden, Holt-Winters, puntaje de proveedores y transportadoras, `PolicyEngine`, `ActionGateway`, aprobaciones, reversión, agentes (reabastecimiento, traslados, excepciones de OC, anomalías, discrepancias, precios, estancados, presupuesto, resumen diario), tools `@Tool`, LLM con Spring AI y modo mock, y asistente.
- **[simulation]** Reloj simulado, generador de demanda, bodeguero virtual, guion de escenarios, carga demo con historial de 90 días y reinicio.
- **[partners-mock]** Proveedores y transportadoras simulados con webhooks firmados (HMAC) y eventos forzados del guion.
- **[frontend]** Todas las pantallas de la sección 11.1, componentes de UI y dominio, gráficas con ECharts, SSE en vivo y tema claro/oscuro.
- **[infra]** Puerto de PostgreSQL configurable con `POSTGRES_PORT`.

- **[backend]** Pruebas unitarias de reglas, Holt-Winters, puntajes, políticas, anomalías y planificador de pedidos, más `DemoFlowIT` con Testcontainers (35 + 1).
- **[frontend]** Pruebas de formato y del parser SSE con Vitest, y prueba e2e con Playwright sobre MSW (login y tablero).

### Cambiado

- **[repo]** `AGENTS.md` ya no contradice al ADR 0002: el ejemplo de migraciones de 5.1 sigue los bloques de 12.2, la tabla 3.1 incluye Lombok y springdoc, y 12.4 lista `partners-mock.yml`.
- **[repo]** `CODEOWNERS` usa los usuarios reales (@FrankDesarrollo para A y @erickcolin2005 para B) en vez de los marcadores, que GitHub ignoraba.
- **[repo]** `README.md` ahora explica cómo levantar el proyecto, los comandos de calidad y el flujo de trabajo.
- **[contracts]** `events.md` con los campos reales de cada evento de dominio y SSE; `partners.yaml` con los endpoints `/admin` de partners-mock.
- **[docs]** ADR [0003](docs/decisions/0003-desviaciones-de-la-implementacion.md): desviaciones de la implementación respecto a `AGENTS.md` (punto de reorden con día de revisión, eventos de webhooks, llamadas de la simulación a la IA, SPI entre módulos, SQL del tablero, multiempresa en webhooks).
- **[docs]** Guion de la demo de 10 minutos y sección de la demo en el `README.md`.

### Corregido

- **[ai]** El resumen diario contaba 0 pedidos; ahora usa la serie de pedidos del día. Se corrigió la redacción "1 días" en las justificaciones.
- **[ai]** Una OC rechazada se reasignaba siempre al proveedor de emergencia; ahora el peso de urgencia depende de la cobertura real y se respeta el proveedor preferido.
- **[ai]** Se evitan guías duplicadas cuando el listener y el reintento horario compiten por el mismo pedido.
- **[simulation]** Se eliminaron anomalías falsas en la demanda (tope por línea en `OrderPlanner`); solo queda la anomalía del guion del día 20.
- **[partners-mock]** Las semillas aleatorias ahora se derivan con SHA-256; antes, con semillas correlacionadas, PRV-02 rechazaba 10 de 15 órdenes.
- **[frontend]** Al recargar la página ya no se cierra la sesión si una petición se cancela; la sesión solo se borra con un 401.

