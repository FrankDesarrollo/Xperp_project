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

### Cambiado

- **[repo]** `README.md` ahora explica cómo levantar el proyecto, los comandos de calidad y el flujo de trabajo.
