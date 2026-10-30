-- V110: inventario (AGENTS.md, sección 6.3).

create table warehouses (
    id          bigint generated always as identity primary key,
    company_id  bigint       not null references companies (id),
    code        varchar(10)  not null,
    name        varchar(100) not null,
    city        varchar(100) not null,
    address     varchar(200),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uk_warehouses_code unique (company_id, code)
);

alter table users
    add constraint fk_users_warehouse foreign key (warehouse_id) references warehouses (id);

-- avg_daily_demand, promotion_suggested y reorder_blocked los actualiza la IA (reorder_params_update
-- y slow_mover) para que el semáforo y la cobertura no dependan de otros módulos.
create table inventory (
    product_id           bigint        not null references products (id),
    warehouse_id         bigint        not null references warehouses (id),
    company_id           bigint        not null references companies (id),
    on_hand              integer       not null default 0 check (on_hand >= 0),
    reserved             integer       not null default 0 check (reserved >= 0),
    safety_stock         integer       not null default 0,
    reorder_point        integer       not null default 0,
    cover_days           integer       not null default 10,
    max_stock            integer       not null default 0,
    avg_daily_demand     numeric(12,2) not null default 0,
    promotion_suggested  boolean       not null default false,
    reorder_blocked      boolean       not null default false,
    params_updated_at    timestamptz,
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now(),
    primary key (product_id, warehouse_id),
    constraint ck_inventory_reserved check (reserved <= on_hand)
);

create index ix_inventory_company on inventory (company_id, warehouse_id);

create table inventory_movements (
    id              bigint generated always as identity primary key,
    company_id      bigint        not null references companies (id),
    product_id      bigint        not null references products (id),
    warehouse_id    bigint        not null references warehouses (id),
    type            varchar(20)   not null
        check (type in ('sale', 'purchase_receipt', 'adjustment', 'transfer_out', 'transfer_in', 'return')),
    quantity        integer       not null,
    balance_after   integer       not null,
    unit_cost       numeric(14,2),
    reference_type  varchar(30),
    reference_id    bigint,
    note            varchar(300),
    occurred_at     timestamptz   not null,
    created_by      varchar(200),
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now()
);

create index ix_movements_item on inventory_movements (product_id, warehouse_id, occurred_at desc);
create index ix_movements_company_date on inventory_movements (company_id, occurred_at);

create table inventory_counts (
    id            bigint generated always as identity primary key,
    company_id    bigint       not null references companies (id),
    product_id    bigint       not null references products (id),
    warehouse_id  bigint       not null references warehouses (id),
    system_qty    integer      not null,
    counted_qty   integer      not null,
    status        varchar(20)  not null default 'recorded'
        check (status in ('recorded', 'discrepancy', 'adjusted')),
    counted_by    varchar(200),
    counted_at    timestamptz  not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create table stock_transfers (
    id                 bigint generated always as identity primary key,
    company_id         bigint       not null references companies (id),
    number             varchar(20)  not null,
    from_warehouse_id  bigint       not null references warehouses (id),
    to_warehouse_id    bigint       not null references warehouses (id),
    status             varchar(20)  not null default 'requested'
        check (status in ('requested', 'in_transit', 'received', 'cancelled')),
    ai_decision_id     bigint,
    shipped_at         timestamptz,
    received_at        timestamptz,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint uk_stock_transfers_number unique (company_id, number),
    constraint ck_stock_transfers_warehouses check (from_warehouse_id <> to_warehouse_id)
);

create table stock_transfer_items (
    id                 bigint generated always as identity primary key,
    company_id         bigint      not null references companies (id),
    stock_transfer_id  bigint      not null references stock_transfers (id) on delete cascade,
    product_id         bigint      not null references products (id),
    quantity           integer     not null check (quantity > 0),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create table lost_sales (
    id              bigint generated always as identity primary key,
    company_id      bigint      not null references companies (id),
    product_id      bigint      not null references products (id),
    warehouse_id    bigint      not null references warehouses (id),
    sales_order_id  bigint,
    quantity        integer     not null check (quantity > 0),
    occurred_at     timestamptz not null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index ix_lost_sales_company_date on lost_sales (company_id, occurred_at);
