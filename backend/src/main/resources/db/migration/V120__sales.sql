-- V120: ventas (AGENTS.md, sección 6.4).

create table customer_segments (
    company_id    bigint       not null references companies (id),
    code          varchar(20)  not null,
    name          varchar(100) not null,
    discount_pct  numeric(5,2) not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    primary key (company_id, code)
);

create table customers (
    id            bigint generated always as identity primary key,
    company_id    bigint       not null references companies (id),
    code          varchar(20)  not null,
    name          varchar(200) not null,
    document      varchar(30),
    segment_code  varchar(20)  not null,
    city          varchar(100) not null,
    address       varchar(200),
    warehouse_id  bigint       not null references warehouses (id),
    is_active     boolean      not null default true,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint uk_customers_code unique (company_id, code),
    constraint fk_customers_segment foreign key (company_id, segment_code)
        references customer_segments (company_id, code)
);

create table sales_orders (
    id            bigint generated always as identity primary key,
    company_id    bigint        not null references companies (id),
    number        varchar(20)   not null,
    customer_id   bigint        not null references customers (id),
    warehouse_id  bigint        not null references warehouses (id),
    status        varchar(20)   not null default 'created'
        check (status in ('created', 'on_hold', 'picking', 'packed', 'shipped', 'delivered', 'cancelled')),
    subtotal      numeric(14,2) not null default 0,
    discount      numeric(14,2) not null default 0,
    total         numeric(14,2) not null default 0,
    hold_reason   varchar(300),
    flagged       boolean       not null default false,
    ordered_at    timestamptz   not null,
    promised_at   timestamptz,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    constraint uk_sales_orders_number unique (company_id, number)
);

create index ix_sales_orders_company_date on sales_orders (company_id, ordered_at desc);
create index ix_sales_orders_status on sales_orders (company_id, status);
create index ix_sales_orders_customer on sales_orders (customer_id);

create table sales_order_items (
    id                  bigint generated always as identity primary key,
    company_id          bigint        not null references companies (id),
    sales_order_id      bigint        not null references sales_orders (id) on delete cascade,
    product_id          bigint        not null references products (id),
    quantity            integer       not null check (quantity > 0),
    quantity_fulfilled  integer       not null default 0 check (quantity_fulfilled >= 0),
    unit_price          numeric(14,2) not null,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now()
);

create index ix_sales_order_items_order on sales_order_items (sales_order_id);
create index ix_sales_order_items_product on sales_order_items (product_id);
