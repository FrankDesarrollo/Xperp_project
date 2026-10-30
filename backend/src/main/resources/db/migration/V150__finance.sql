-- V150: finanzas (AGENTS.md, sección 6.7).

create table budgets (
    id           bigint generated always as identity primary key,
    company_id   bigint        not null references companies (id),
    category_id  bigint        not null references categories (id),
    period       varchar(7)    not null,
    amount       numeric(14,2) not null check (amount >= 0),
    committed    numeric(14,2) not null default 0,
    spent        numeric(14,2) not null default 0,
    version      bigint        not null default 0,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),
    constraint uk_budgets_period unique (company_id, category_id, period)
);

-- Lo que cada OC comprometió en cada presupuesto, para liberar o pasar a gastado sin recalcular.
create table budget_commitments (
    id                 bigint generated always as identity primary key,
    company_id         bigint        not null references companies (id),
    budget_id          bigint        not null references budgets (id),
    purchase_order_id  bigint        not null,
    amount             numeric(14,2) not null,
    status             varchar(20)   not null default 'committed'
        check (status in ('committed', 'spent', 'released')),
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now()
);

create index ix_budget_commitments_po on budget_commitments (purchase_order_id);

create table accounts_payable (
    id                 bigint generated always as identity primary key,
    company_id         bigint        not null references companies (id),
    purchase_order_id  bigint        not null,
    supplier_id        bigint        not null references suppliers (id),
    amount             numeric(14,2) not null,
    due_date           date          not null,
    status             varchar(20)   not null default 'open' check (status in ('open', 'paid')),
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now()
);
