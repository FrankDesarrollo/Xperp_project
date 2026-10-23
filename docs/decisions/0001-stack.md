# 0001. Stack tecnológico y versiones

- **Estado:** Aceptado
- **Fecha:** 2026-10-22
- **Módulos afectados:** todos

## Contexto

La sección 3 de `AGENTS.md` define el stack y la razón de cada elección. Este ADR fija las versiones exactas que se usaron al montar el esqueleto. También deja el motivo de las dependencias que no aparecen en la tabla 3.1, como pide la regla 15.

## Decisión

### Backend y partners-mock

| Dependencia | Versión | Para qué |
|---|---|---|
| Java | 21 | LTS definido en `AGENTS.md` |
| Spring Boot (parent) | 3.5.16 | Última versión 3.5.x |
| Spring Modulith | 1.4.14 | Límites entre módulos y eventos persistidos (línea compatible con Boot 3.5) |
| Spring AI | 1.1.8 | `ChatClient` y tool calling. Starters de Anthropic, OpenAI y Ollama |
| PostgreSQL / Flyway | Gestionados por Boot | Persistencia y migraciones |
| Apache Commons Math 3 | 3.6.1 | Estadística del pronóstico |
| Datafaker | 2.7.0 | Datos demo con semilla fija |
| OpenPDF | 3.0.5 | PDF de OC y guías |
| WireMock | 3.13.2 | Pruebas de los clientes HTTP hacia partners-mock |
| Testcontainers | Gestionado por Boot | Pruebas de integración con PostgreSQL |
| openapi-generator-maven-plugin | 7.26.0 | Interfaces de los controllers desde el contrato |
| Spotless + google-java-format | 3.10.4 + 1.35.0 | Formato del código Java |
| Maven (wrapper) | 3.9.16 | Build reproducible sin instalar Maven |

### Dependencias que no aparecen en la tabla 3.1

| Dependencia | Motivo |
|---|---|
| Lombok | La sección 13.1 la permite para los getters y setters de las entidades JPA |
| `spring-boot-starter-oauth2-resource-server` | Valida el JWT HS256 con `NimbusJwtDecoder`, la vía estándar de Spring Security, en lugar de escribir un filtro propio |
| springdoc-openapi 2.8.17 | Sirve Swagger UI en `/swagger-ui.html`, como pide la sección 15 |
| `spring-boot-configuration-processor` | Metadatos y autocompletado para las clases `@ConfigurationProperties` (sección 13.1) |
| exec-maven-plugin 3.6.4 + Redocly CLI 2.69.0 | Empaquetan el contrato antes de generar código; ver ADR 0002 |

### Frontend

| Dependencia | Versión | Para qué |
|---|---|---|
| SvelteKit | 2.70 | Definido en `AGENTS.md` (ya existe la 3; ver ADR 0002) |
| Svelte | 5.57 | Runes |
| Vite / vite-plugin-svelte | 7.3 / 6.2 | Las versiones más recientes compatibles con SvelteKit 2 |
| TypeScript | 5.9 | Modo estricto |
| `@sveltejs/adapter-static` | 3.0 | SPA |
| openapi-typescript / openapi-fetch | 7.13 / 0.17 | Tipos y cliente desde el contrato |
| ECharts | 6.1 | Gráficas |
| `@lucide/svelte` | 1.61 | Íconos (paquete oficial de Lucide para Svelte 5) |
| Zod | 4.6 | Validación de formularios |
| MSW | 2.15 | Mocks en el navegador |
| Vitest / Testing Library / jsdom / jest-dom | 3.2 / 5.4 / 26 / 6.10 | Pruebas unitarias y de componentes |
| Playwright | 1.64 | Pruebas de extremo a extremo |
| ESLint / typescript-eslint / eslint-plugin-svelte / Prettier | 9.39 / 8.72 / 3.23 / 3.9 | Calidad y formato |

### Infraestructura

PostgreSQL 16 (alpine), Mailpit 1.27, nginx 1.29 (alpine) para servir el frontend y Ollama opcional.

## Consecuencias

- Para subir una versión mayor de cualquiera de estas dependencias hace falta un ADR nuevo.
- Las versiones menores y de parche se pueden actualizar en un PR `chore/deps-...` con la CI en verde.
