-- V2: ajustes del sistema y consecutivos de documentos (bloque compartido V1–V99).

-- Ajustes por empresa (AGENTS.md, sección 6.8). Claves: kill_switch, shadow_mode,
-- ai_daily_spend_cap, llm_max_calls_per_day, scoring_weights.
create table system_settings (
    company_id  bigint      not null references companies (id),
    key         varchar(60) not null,
    value       jsonb       not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    primary key (company_id, key)
);

-- Consecutivos por empresa, prefijo y año: PV-2026-000001, OC-2026-000001, TR-2026-000001.
create table document_sequences (
    company_id  bigint      not null references companies (id),
    prefix      varchar(5)  not null,
    year        integer     not null,
    last_value  bigint      not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    primary key (company_id, prefix, year)
);

alter table users add column last_login_at timestamptz;
