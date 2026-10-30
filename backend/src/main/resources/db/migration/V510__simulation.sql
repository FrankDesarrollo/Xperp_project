-- V510: control de los trabajos diarios de la simulación, para no repetirlos tras un reinicio.

create table simulation_job_runs (
    company_id  bigint      not null references companies (id),
    job         varchar(40) not null,
    sim_date    date        not null,
    ran_at      timestamptz not null default now(),
    primary key (company_id, job, sim_date)
);
