# Arquitectura

Resumen de las secciones 4 y 5 de `AGENTS.md`, que siguen siendo la fuente de verdad. Este documento se amplía a medida que se implementan los módulos.

## Vista general

Xperp es un monolito modular, con un servicio aparte que simula a los socios externos.

```mermaid
flowchart TB
    FE["frontend<br/>SvelteKit SPA"]
    subgraph BE["backend · Spring Boot + Spring Modulith"]
        DOM["catalog · inventory · sales<br/>purchasing · logistics · finance"]
        AI["ai<br/>reglas → pronóstico → puntaje → agente → políticas"]
        SIM["simulation<br/>reloj · demanda · actores · guion"]
        NOT["notifications · dashboard"]
        PAR["partners<br/>clientes HTTP + webhooks"]
        SH["shared (módulo abierto)"]
    end
    PM["partners-mock<br/>PRV-01..09 · TRP-01..03"]
    DB[("PostgreSQL 16")]
    MAIL["Mailpit"]
    LLM["LLM<br/>Anthropic · OpenAI · Ollama · mock"]

    FE -- "REST /api/v1 + SSE /api/v1/stream" --> BE
    PAR -- HTTP --> PM
    PM -- "webhooks firmados (HMAC)" --> PAR
    BE --> DB
    NOT --> MAIL
    AI --> LLM
```

## Reglas de dependencia

- Un módulo solo puede usar `api/` y `events/` de otro módulo. `ModularityTest` lo verifica en cada build.
- Dentro de un módulo, las dependencias van `web → service → repository`.
- Los módulos se comunican por eventos de dominio persistidos (`event_publication`). El catálogo está en `contracts/events.md`.
- La IA propone, el backend valida y la política decide. El LLM nunca toca la base de datos; las tools pasan por `ActionGateway`.

## Contrato primero

1. Se cambia `contracts/openapi/` en un PR `contract/...`.
2. El backend empaqueta el contrato y genera interfaces en `com.xperp.shared.contract.api`. Los controllers de `web/` las implementan; si se apartan del contrato, no compilan.
3. El frontend genera `schema.d.ts` con `npm run gen:api` y crea los handlers de MSW.

## Documentación generada de módulos

`ModularityTest` escribe diagramas PlantUML de los módulos en `backend/target/spring-modulith-docs/`.
