-- V160: IA y control (AGENTS.md, sección 6.8).

create table demand_forecasts (
    company_id     bigint        not null references companies (id),
    product_id     bigint        not null references products (id),
    warehouse_id   bigint        not null references warehouses (id),
    forecast_date  date          not null,
    quantity       numeric(12,2) not null,
    model          varchar(30)   not null,
    generated_at   timestamptz   not null,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    primary key (product_id, warehouse_id, forecast_date)
);

create table ai_decisions (
    id               bigint generated always as identity primary key,
    company_id       bigint        not null references companies (id),
    decision_type    varchar(40)   not null,
    action_type      varchar(40),
    title            varchar(200),
    trigger_event    varchar(60)   not null,
    trigger_ref      varchar(100),
    input_snapshot   jsonb         not null default '{}'::jsonb,
    alternatives     jsonb         not null default '[]'::jsonb,
    proposed_action  jsonb         not null default '{}'::jsonb,
    reasoning        text          not null,
    confidence       numeric(4,3)  not null default 1,
    amount           numeric(14,2),
    policy_result    varchar(20)   not null
        check (policy_result in ('executed', 'pending_approval', 'suggested', 'blocked')),
    policy_reason    varchar(300),
    autonomy_level   integer,
    status           varchar(20)   not null,
    result_message   varchar(500),
    result_ref       jsonb,
    approved_by      bigint references users (id),
    executed_at      timestamptz,
    reverted_at      timestamptz,
    llm_used         boolean       not null default false,
    llm_model        varchar(80),
    prompt_version   varchar(40),
    tokens_in        integer,
    tokens_out       integer,
    latency_ms       integer,
    sim_time         timestamptz   not null,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now()
);

create index ix_ai_decisions_company on ai_decisions (company_id, id desc);
create index ix_ai_decisions_type on ai_decisions (company_id, decision_type, id desc);
create index ix_ai_decisions_status on ai_decisions (company_id, status);

-- Referencias de cada decisión a las entidades que tocó (OC, pedido, traslado...), para filtrar.
create table ai_decision_refs (
    id              bigint generated always as identity primary key,
    company_id      bigint       not null references companies (id),
    ai_decision_id  bigint       not null references ai_decisions (id) on delete cascade,
    ref_type        varchar(30)  not null,
    ref_id          bigint       not null,
    label           varchar(100) not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create index ix_ai_decision_refs_ref on ai_decision_refs (ref_type, ref_id);
create index ix_ai_decision_refs_decision on ai_decision_refs (ai_decision_id);

create table autonomy_policies (
    id             bigint generated always as identity primary key,
    company_id     bigint        not null references companies (id),
    action_type    varchar(40)   not null,
    description    varchar(200),
    level          integer       not null check (level between 0 and 3),
    min_amount     numeric(14,2),
    max_amount     numeric(14,2),
    approver_role  varchar(20),
    conditions     jsonb         not null default '{}'::jsonb,
    is_enabled     boolean       not null default true,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now()
);

create index ix_autonomy_policies_action on autonomy_policies (company_id, action_type);

create table approvals (
    id              bigint generated always as identity primary key,
    company_id      bigint       not null references companies (id),
    ai_decision_id  bigint       not null references ai_decisions (id),
    requested_role  varchar(20)  not null,
    status          varchar(20)  not null default 'pending'
        check (status in ('pending', 'approved', 'rejected', 'expired')),
    decided_by      bigint references users (id),
    decided_at      timestamptz,
    comment         varchar(500),
    requested_at    timestamptz  not null,
    expires_at      timestamptz  not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create index ix_approvals_status on approvals (company_id, status);

create table alerts (
    id              bigint generated always as identity primary key,
    company_id      bigint       not null references companies (id),
    type            varchar(40)  not null,
    severity        varchar(10)  not null check (severity in ('info', 'warning', 'critical')),
    message         varchar(500) not null,
    entity_type     varchar(30),
    entity_id       bigint,
    ai_decision_id  bigint,
    is_read         boolean      not null default false,
    raised_at       timestamptz  not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now()
);

create index ix_alerts_company on alerts (company_id, id desc);

-- Uso diario del LLM y del gasto de la IA (topes de las secciones 8.5 y 9.3).
create table ai_daily_usage (
    company_id  bigint        not null references companies (id),
    sim_date    date          not null,
    llm_calls   integer       not null default 0,
    spend       numeric(14,2) not null default 0,
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    primary key (company_id, sim_date)
);

create table assistant_messages (
    id               bigint generated always as identity primary key,
    company_id       bigint       not null references companies (id),
    user_id          bigint       not null references users (id),
    conversation_id  varchar(40)  not null,
    role             varchar(20)  not null check (role in ('user', 'assistant')),
    content          text         not null,
    tool_calls       jsonb,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);

create index ix_assistant_messages_conversation on assistant_messages (conversation_id, id);
