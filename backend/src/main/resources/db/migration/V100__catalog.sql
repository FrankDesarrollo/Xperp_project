-- V100: catálogo (AGENTS.md, sección 6.2). Bloque del integrante A (V100–V499).

create table categories (
    id          bigint generated always as identity primary key,
    company_id  bigint       not null references companies (id),
    code        varchar(10)  not null,
    name        varchar(100) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uk_categories_code unique (company_id, code)
);

create table products (
    id               bigint generated always as identity primary key,
    company_id       bigint        not null references companies (id),
    sku              varchar(20)   not null,
    name             varchar(200)  not null,
    category_id      bigint        not null references categories (id),
    unit             varchar(10)   not null default 'und',
    units_per_case   integer       not null check (units_per_case > 0),
    weight_kg        numeric(8,3)  not null check (weight_kg >= 0),
    cost             numeric(14,2) not null check (cost >= 0),
    price            numeric(14,2) not null check (price >= 0),
    perishable       boolean       not null default false,
    shelf_life_days  integer,
    is_active        boolean       not null default true,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint uk_products_sku unique (company_id, sku)
);

create index ix_products_category on products (category_id);
