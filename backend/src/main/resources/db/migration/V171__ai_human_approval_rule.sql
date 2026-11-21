-- V171: completa la regla del ADR 0004 sobre lo que dejó V170.
--
-- V170 creó ai_decisions.impact como un objeto con frases. Desde aquí el impacto es una lista de
-- cifras (ImpactItem: qué gasta, qué protege y qué arriesga) que calcula ImpactAssessor y que la
-- interfaz muestra. Las decisiones guardadas con el formato anterior quedan con la lista vacía.
--
-- approved_in_decision_id: si una acción financiera se ejecutó sin aprobación propia, es porque una
-- persona ya la aprobó dentro de otra decisión (las OC de un plan de presupuesto). Si esta columna
-- está vacía en una acción financiera ejecutada por política, la regla de aprobación se rompió.

alter table ai_decisions
    alter column impact set default '[]'::jsonb,
    add column approved_in_decision_id bigint references ai_decisions (id);

update ai_decisions
set impact = '[]'::jsonb
where jsonb_typeof(impact) <> 'array';

-- Las guías de despacho también comprometen dinero (flete): pasan a aprobación de Gerencia, en lote.
-- Las demás acciones protegidas ya las bajó V170 al nivel 1. Las empresas nuevas salen así de
-- PolicyDefaults.
update autonomy_policies
set level = 1,
    approver_role = 'manager',
    description = 'Guía con la transportadora elegida (flete): aprueba Gerencia',
    updated_at = now()
where level >= 2
  and action_type = 'create_shipment';

insert into autonomy_policies (company_id, action_type, description, level)
select c.id, 'send_financial_review', 'Revisión financiera a gerencia y compras: solo informa', 3
from companies c
where exists (select 1 from autonomy_policies p where p.company_id = c.id)
  and not exists (select 1 from autonomy_policies p
                  where p.company_id = c.id and p.action_type = 'send_financial_review');
