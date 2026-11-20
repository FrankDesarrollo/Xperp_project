-- ADR 0004: la IA recomienda y una persona aprueba. Cada decisión guarda su impacto económico y
-- operativo para que quien aprueba vea qué cuesta, qué cambia y qué pasa si la rechaza.
alter table ai_decisions
    add column impact jsonb not null default '{}'::jsonb;

comment on column ai_decisions.impact is
    'amount, economic, operational, risk_if_rejected, risk_level y budget (efecto por categoría)';

-- Las políticas que ya existían con nivel 2 o 3 para acciones protegidas pasan a nivel 1.
-- El PolicyEngine también lo impone, pero así la pantalla IA → Políticas muestra lo que de verdad pasa.
update autonomy_policies
   set level = 1,
       description = left(split_part(coalesce(description, action_type), ':', 1)
                          || ': requiere aprobación (ADR 0004)', 200),
       approver_role = coalesce(nullif(approver_role, ''),
                                case when action_type in ('create_purchase_order',
                                                          'reassign_purchase_order',
                                                          'cancel_purchase_order')
                                     then 'purchasing' else 'manager' end)
 where level >= 2
   and action_type in ('create_purchase_order', 'reassign_purchase_order', 'cancel_purchase_order',
                       'propose_stock_transfer', 'adjust_inventory', 'apply_budget_plan');
