-- V1: base compartida de la plataforma (AGENTS.md, sección 6.1).
-- Bloque V1–V99: compartido. Nunca se edita una migración ya aplicada; los cambios van en una nueva.

create table companies (
    id          bigint generated always as identity primary key,
    name        varchar(200) not null,
    nit         varchar(20)  not null unique,
    city        varchar(100) not null,
    timezone    varchar(50)  not null default 'America/Bogota',
    currency    char(3)      not null default 'COP',
    is_active   boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

-- warehouse_id queda sin FK aquí: la tabla warehouses llega con la migración de inventario.
create table users (
    id             bigint generated always as identity primary key,
    company_id     bigint       not null references companies (id),
    email          varchar(254) not null,
    password_hash  varchar(100) not null,
    full_name      varchar(200) not null,
    role           varchar(20)  not null
        check (role in ('admin', 'manager', 'purchasing', 'warehouse', 'viewer')),
    warehouse_id   bigint,
    is_active      boolean      not null default true,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint uk_users_email unique (email)
);

create index ix_users_company on users (company_id);

create table holidays (
    date        date primary key,
    name        varchar(100) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table simulation_state (
    company_id       bigint primary key references companies (id),
    sim_time         timestamptz not null,
    status           varchar(10) not null default 'paused'
        check (status in ('paused', 'running')),
    seconds_per_day  integer     not null default 20 check (seconds_per_day > 0),
    scenario_cursor  integer     not null default 0,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

-- Registro de publicación de eventos de Spring Modulith (spring-modulith-starter-jpa 1.4).
create table event_publication (
    id                uuid        primary key,
    listener_id       text        not null,
    event_type        text        not null,
    serialized_event  text        not null,
    publication_date  timestamptz not null,
    completion_date   timestamptz
);

create index ix_event_publication_incomplete
    on event_publication (publication_date)
    where completion_date is null;
