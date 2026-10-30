-- V140: logística (AGENTS.md, sección 6.6).

create table carriers (
    id               bigint generated always as identity primary key,
    company_id       bigint        not null references companies (id),
    code             varchar(10)   not null,
    name             varchar(200)  not null,
    partner_code     varchar(10)   not null,
    coverage_cities  jsonb         not null default '[]'::jsonb,
    national         boolean       not null default false,
    base_rate        numeric(14,2) not null default 0,
    rate_per_kg      numeric(14,2) not null default 0,
    on_time_rate     numeric(5,4)  not null default 1,
    is_active        boolean       not null default true,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint uk_carriers_code unique (company_id, code)
);

create table warehouse_tasks (
    id                   bigint generated always as identity primary key,
    company_id           bigint       not null references companies (id),
    warehouse_id         bigint       not null references warehouses (id),
    type                 varchar(20)  not null
        check (type in ('pick', 'pack', 'receive', 'count', 'transfer_out', 'transfer_in')),
    status               varchar(20)  not null default 'pending'
        check (status in ('pending', 'in_progress', 'done', 'cancelled')),
    priority             integer      not null default 3,
    title                varchar(200) not null,
    detail               varchar(500),
    sales_order_id       bigint,
    purchase_order_id    bigint,
    stock_transfer_id    bigint,
    product_id           bigint,
    reference_number     varchar(30),
    depends_on_task_id   bigint references warehouse_tasks (id),
    assigned_to_user_id  bigint references users (id),
    handled_by_human     boolean      not null default false,
    due_at               timestamptz,
    started_at           timestamptz,
    completed_at         timestamptz,
    completed_by         bigint references users (id),
    ai_decision_id       bigint,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);

create index ix_warehouse_tasks_open on warehouse_tasks (company_id, status, due_at);
create index ix_warehouse_tasks_sales_order on warehouse_tasks (sales_order_id);
create index ix_warehouse_tasks_assigned on warehouse_tasks (assigned_to_user_id, status);

create table shipments (
    id                bigint generated always as identity primary key,
    company_id        bigint        not null references companies (id),
    sales_order_id    bigint        not null,
    carrier_id        bigint        not null references carriers (id),
    service           varchar(40)   not null,
    tracking_number   varchar(40)   not null,
    status            varchar(20)   not null default 'created'
        check (status in ('created', 'in_transit', 'delivered', 'failed')),
    cost              numeric(14,2) not null default 0,
    weight_kg         numeric(10,3) not null default 0,
    origin_city       varchar(100)  not null,
    destination_city  varchar(100)  not null,
    eta               timestamptz,
    label_path        varchar(300),
    shipped_at        timestamptz,
    delivered_at      timestamptz,
    ai_decision_id    bigint,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    constraint uk_shipments_tracking unique (tracking_number)
);

create index ix_shipments_company on shipments (company_id, created_at desc);
create index ix_shipments_sales_order on shipments (sales_order_id);
