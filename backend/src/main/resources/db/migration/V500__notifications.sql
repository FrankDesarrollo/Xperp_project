-- V500: notificaciones (AGENTS.md, sección 6.8). Bloque del integrante B (V500–V899).

create table notifications (
    id          bigint generated always as identity primary key,
    company_id  bigint       not null references companies (id),
    user_id     bigint references users (id),
    role        varchar(20),
    type        varchar(40)  not null,
    title       varchar(200) not null,
    body        text         not null,
    link        varchar(300),
    read_at     timestamptz,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create index ix_notifications_user on notifications (company_id, user_id, id desc);
