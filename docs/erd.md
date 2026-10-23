# Modelo entidad-relación

Modelo de datos de la sección 6 de `AGENTS.md`. Cada diagrama se actualiza en el mismo PR que la migración que cambia el esquema.

## Plataforma (V1)

```mermaid
erDiagram
    companies ||--o{ users : "tiene"
    companies ||--|| simulation_state : "tiene"

    companies {
        bigint id PK
        varchar name
        varchar nit UK
        varchar city
        varchar timezone
        char currency
        boolean is_active
    }
    users {
        bigint id PK
        bigint company_id FK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role
        bigint warehouse_id "FK pendiente (inventario)"
        boolean is_active
    }
    holidays {
        date date PK
        varchar name
    }
    simulation_state {
        bigint company_id PK, FK
        timestamptz sim_time
        varchar status
        integer seconds_per_day
        integer scenario_cursor
    }
    event_publication {
        uuid id PK
        text listener_id
        text event_type
        text serialized_event
        timestamptz publication_date
        timestamptz completion_date
    }
```

## Pendiente

Estos diagramas se agregan cuando llegue la migración de cada módulo: catálogo (6.2), inventario (6.3), ventas (6.4), compras (6.5), logística (6.6), finanzas (6.7) e IA y control (6.8).
