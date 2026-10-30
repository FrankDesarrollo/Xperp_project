-- V130: compras (AGENTS.md, sección 6.5).

create table suppliers (
    id                  bigint generated always as identity primary key,
    company_id          bigint        not null references companies (id),
    code                varchar(10)   not null,
    name                varchar(200)  not null,
    nit                 varchar(20)   not null,
    city                varchar(100)  not null,
    email               varchar(254)  not null,
    partner_code        varchar(10)   not null,
    min_order_amount    numeric(14,2) not null default 0,
    payment_terms_days  integer       not null default 0,
    on_time_rate        numeric(5,4)  not null default 1,
    fill_rate           numeric(5,4)  not null default 1,
    is_active           boolean       not null default true,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    constraint uk_suppliers_code unique (company_id, code)
);

create table supplier_warehouses (
    supplier_id     bigint      not null references suppliers (id),
    warehouse_id    bigint      not null references warehouses (id),
    company_id      bigint      not null references companies (id),
    lead_time_days  integer     not null check (lead_time_days > 0),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    primary key (supplier_id, warehouse_id)
);

create table supplier_products (
    supplier_id       bigint        not null references suppliers (id),
    product_id        bigint        not null references products (id),
    company_id        bigint        not null references companies (id),
    unit_cost         numeric(14,2) not null check (unit_cost >= 0),
    min_order_cases   integer       not null default 5,
    is_preferred      boolean       not null default false,
    price_updated_at  timestamptz,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    primary key (supplier_id, product_id)
);

create index ix_supplier_products_product on supplier_products (product_id);

create table supplier_price_history (
    id           bigint generated always as identity primary key,
    company_id   bigint        not null references companies (id),
    supplier_id  bigint        not null references suppliers (id),
    product_id   bigint        not null references products (id),
    unit_cost    numeric(14,2) not null,
    valid_from   timestamptz   not null,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now()
);

create table purchase_orders (
    id                  bigint generated always as identity primary key,
    company_id          bigint        not null references companies (id),
    number              varchar(20)   not null,
    supplier_id         bigint        not null references suppliers (id),
    warehouse_id        bigint        not null references warehouses (id),
    status              varchar(20)   not null default 'draft'
        check (status in ('draft', 'pending_approval', 'sent', 'confirmed', 'delayed', 'rejected',
                          'shipped', 'received', 'cancelled')),
    status_reason       varchar(300),
    subtotal            numeric(14,2) not null default 0,
    total               numeric(14,2) not null default 0,
    expected_at         timestamptz,
    first_expected_at   timestamptz,
    sent_at             timestamptz,
    confirmed_at        timestamptz,
    received_at         timestamptz,
    created_by_user_id  bigint references users (id),
    ai_decision_id      bigint,
    replaces_po_id      bigint references purchase_orders (id),
    idempotency_key     varchar(64),
    external_id         varchar(40),
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    constraint uk_purchase_orders_number unique (company_id, number),
    constraint uk_purchase_orders_idempotency unique (idempotency_key)
);

create index ix_purchase_orders_status on purchase_orders (company_id, status);

create table purchase_order_items (
    id                 bigint generated always as identity primary key,
    company_id         bigint        not null references companies (id),
    purchase_order_id  bigint        not null references purchase_orders (id) on delete cascade,
    product_id         bigint        not null references products (id),
    quantity_shipped   integer,
    quantity_received  integer       not null default 0,
    quantity           integer       not null check (quantity > 0),
    unit_cost          numeric(14,2) not null,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now()
);

create index ix_purchase_order_items_order on purchase_order_items (purchase_order_id);

-- Línea de tiempo de cada OC (pantalla de Compras).
create table purchase_order_events (
    id                 bigint generated always as identity primary key,
    company_id         bigint       not null references companies (id),
    purchase_order_id  bigint       not null references purchase_orders (id) on delete cascade,
    status             varchar(20)  not null,
    note               varchar(300),
    occurred_at        timestamptz  not null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);

create index ix_purchase_order_events_order on purchase_order_events (purchase_order_id);

create table goods_receipts (
    id                 bigint generated always as identity primary key,
    company_id         bigint       not null references companies (id),
    purchase_order_id  bigint       not null references purchase_orders (id),
    received_by        varchar(200),
    received_at        timestamptz  not null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now()
);

create table goods_receipt_items (
    id                 bigint generated always as identity primary key,
    company_id         bigint      not null references companies (id),
    goods_receipt_id   bigint      not null references goods_receipts (id) on delete cascade,
    product_id         bigint      not null references products (id),
    quantity_received  integer     not null check (quantity_received >= 0),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);
