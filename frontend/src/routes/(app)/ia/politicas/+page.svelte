<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { OctagonX, Eye } from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { AiSettings, AutonomyPolicy, Budget } from '$lib/api/types';
	import { clock } from '$lib/stores/clock.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import { formatCop, formatNumber } from '$lib/utils/format';
	import { ACTION_TYPE, LEVEL_LABEL, ROLE_LABEL } from '$lib/utils/labels';

	let settings = $state<AiSettings | null>(null);
	let policies = $state<AutonomyPolicy[]>([]);
	let budgets = $state<Budget[]>([]);
	let saving = $state(false);
	let dirty = $state(false);

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function load() {
		return loader.run(fetchSettings);
	}

	async function fetchSettings() {
		[settings, policies, budgets] = await Promise.all([
			unwrap(api.GET('/ai/settings')),
			unwrap(api.GET('/ai/policies')),
			unwrap(api.GET('/budgets'))
		]);
		dirty = false;
	}

	async function saveSettings(patch: Partial<AiSettings> = {}) {
		if (!settings) return;
		saving = true;
		try {
			settings = await unwrap(api.PUT('/ai/settings', { body: { ...settings, ...patch } }));
			clock.apply({
				ai_mode: settings.kill_switch ? 'stopped' : settings.shadow_mode ? 'shadow' : 'active'
			});
			toasts.success('Ajustes guardados');
		} catch (e) {
			toasts.error(
				'No se pudieron guardar los ajustes',
				e instanceof ApiError ? e.message : undefined
			);
		} finally {
			saving = false;
		}
	}

	async function savePolicies() {
		saving = true;
		try {
			policies = await unwrap(api.PUT('/ai/policies', { body: policies }));
			dirty = false;
			toasts.success('Políticas guardadas');
		} catch (e) {
			toasts.error(
				'No se pudieron guardar las políticas',
				e instanceof ApiError ? e.message : undefined
			);
		} finally {
			saving = false;
		}
	}

	onMount(load);

	let weightSum = $derived(
		settings
			? settings.scoring_weights.price +
					settings.scoring_weights.lead_time +
					settings.scoring_weights.fulfillment +
					settings.scoring_weights.preferred
			: 1
	);
</script>

<svelte:head><title>Políticas de la IA · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Políticas de la IA</h1>
		<p>
			Supervisión humana: niveles de autonomía, umbrales, topes, pesos del puntaje, kill switch y
			modo sombra
		</p>
	</div>
</div>

<LoadState {loader} what="los ajustes de la IA" onretry={load}>
	{#if settings}
		<div class="grid-2">
			<section class="card controls">
				<h2>Controles de emergencia</h2>
				<div class="switch-row" class:on={settings.kill_switch}>
					<div>
						<strong><OctagonX size={16} /> Kill switch</strong>
						<p class="small text-muted">
							Bloquea todas las acciones de escritura de la IA. Todo queda registrado como
							bloqueado.
						</p>
					</div>
					<Button
						variant={settings.kill_switch ? 'primary' : 'danger'}
						loading={saving}
						onclick={() => saveSettings({ kill_switch: !settings!.kill_switch })}
					>
						{settings.kill_switch ? 'Reactivar IA' : 'Detener IA'}
					</Button>
				</div>
				<div class="switch-row" class:on={settings.shadow_mode}>
					<div>
						<strong><Eye size={16} /> Modo sombra</strong>
						<p class="small text-muted">
							La IA decide pero no ejecuta: todo queda como sugerencia, para medir cuánto acertaría.
						</p>
					</div>
					<Button
						loading={saving}
						onclick={() => saveSettings({ shadow_mode: !settings!.shadow_mode })}
					>
						{settings.shadow_mode ? 'Salir del modo sombra' : 'Activar modo sombra'}
					</Button>
				</div>
				<p class="small">
					Proveedor de LLM: <Badge tone="ai"
						>{settings.llm_provider ?? 'mock'} {settings.llm_model ?? ''}</Badge
					>
					· llamadas hoy {settings.llm_calls_today ?? 0} de {settings.llm_max_calls_per_day}
					· compras de la IA hoy {formatCop(settings.ai_spend_today)}
				</p>
			</section>

			<section class="card">
				<h2>Topes y puntaje de proveedores</h2>
				<div class="form-grid">
					<Field label="Tope diario de compras de la IA (COP)">
						<input type="text" inputmode="decimal" bind:value={settings.ai_daily_spend_cap} />
					</Field>
					<Field label="Llamadas al LLM por día simulado">
						<input type="number" min="0" bind:value={settings.llm_max_calls_per_day} />
					</Field>
					<Field label="Peso del precio"
						><input
							type="number"
							step="0.05"
							min="0"
							max="1"
							bind:value={settings.scoring_weights.price}
						/></Field
					>
					<Field label="Peso del tiempo de entrega"
						><input
							type="number"
							step="0.05"
							min="0"
							max="1"
							bind:value={settings.scoring_weights.lead_time}
						/></Field
					>
					<Field label="Peso del cumplimiento"
						><input
							type="number"
							step="0.05"
							min="0"
							max="1"
							bind:value={settings.scoring_weights.fulfillment}
						/></Field
					>
					<Field label="Peso de proveedor preferido"
						><input
							type="number"
							step="0.05"
							min="0"
							max="1"
							bind:value={settings.scoring_weights.preferred}
						/></Field
					>
				</div>
				<div class="row">
					<span class="small" class:bad={Math.abs(weightSum - 1) > 0.01}
						>Suma de pesos: {formatNumber(weightSum, 2)} (debe ser 1)</span
					>
					<span class="spacer"></span>
					<Button
						variant="primary"
						loading={saving}
						disabled={Math.abs(weightSum - 1) > 0.01}
						onclick={() =>
							saveSettings({ ai_daily_spend_cap: Number(settings!.ai_daily_spend_cap).toFixed(2) })}
					>
						Guardar
					</Button>
				</div>
			</section>
		</div>

		<section class="card policies">
			<div class="row">
				<h2>Niveles de autonomía por acción</h2>
				<span class="spacer"></span>
				<Button variant="primary" loading={saving} disabled={!dirty} onclick={savePolicies}
					>Guardar políticas</Button
				>
			</div>
			<p class="small text-muted">
				Nivel 0 solo sugiere · nivel 1 prepara y espera aprobación · nivel 2 ejecuta y notifica ·
				nivel 3 ejecuta sin notificar. Los montos van en COP; el mínimo incluye y el máximo excluye.
			</p>
			<div class="table-wrap">
				<table class="data">
					<thead
						><tr
							><th>Acción</th><th>Descripción</th><th>Nivel</th><th>Desde</th><th>Hasta</th><th
								>Aprueba</th
							><th>Condiciones</th><th>Activa</th></tr
						></thead
					>
					<tbody>
						{#each policies as p (p.id)}
							<tr>
								<td><strong class="small">{ACTION_TYPE[p.action_type] ?? p.action_type}</strong></td
								>
								<td class="small text-muted">{p.description}</td>
								<td>
									<select bind:value={p.level} onchange={() => (dirty = true)} aria-label="Nivel">
										{#each [0, 1, 2, 3] as l (l)}<option value={l}>{LEVEL_LABEL[l]}</option>{/each}
									</select>
								</td>
								<td
									><input
										class="amount"
										type="text"
										inputmode="decimal"
										bind:value={p.min_amount}
										oninput={() => (dirty = true)}
										aria-label="Monto mínimo"
									/></td
								>
								<td
									><input
										class="amount"
										type="text"
										inputmode="decimal"
										bind:value={p.max_amount}
										oninput={() => (dirty = true)}
										aria-label="Monto máximo"
									/></td
								>
								<td>
									<select
										bind:value={p.approver_role}
										onchange={() => (dirty = true)}
										aria-label="Rol aprobador"
									>
										<option value={null}>—</option>
										{#each ['purchasing', 'manager', 'admin'] as r (r)}<option value={r}
												>{ROLE_LABEL[r]}</option
											>{/each}
									</select>
								</td>
								<td class="small mono"
									>{Object.keys(p.conditions ?? {}).length > 0
										? JSON.stringify(p.conditions)
										: '—'}</td
								>
								<td
									><label
										><input
											type="checkbox"
											bind:checked={p.is_enabled}
											onchange={() => (dirty = true)}
											aria-label="Activa"
										/></label
									></td
								>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>

		<section class="card">
			<h2>Presupuesto mensual por categoría</h2>
			<div class="table-wrap">
				<table class="data">
					<thead
						><tr
							><th>Categoría</th><th class="num">Presupuesto</th><th class="num">Usado</th><th
								class="num">Uso</th
							></tr
						></thead
					>
					<tbody>
						{#each budgets as b (b.id)}
							<tr>
								<td>{b.category_name}</td>
								<td class="num">{formatCop(b.amount)}</td>
								<td class="num">{formatCop(Number(b.committed) + Number(b.spent))}</td>
								<td class="num">{formatNumber(b.usage_pct, 1)} %</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
			<p class="small text-muted">
				Si una categoría pasa del 85 %, la IA prioriza por margen y cobertura y deja el resto para
				aprobación.
			</p>
		</section>
	{:else}
		<p class="text-muted">Cargando…</p>
	{/if}
</LoadState>

<style>
	.grid-2,
	.policies {
		margin-bottom: var(--space-4);
	}

	.controls {
		display: grid;
		gap: var(--space-4);
	}

	.switch-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
	}

	.switch-row.on {
		border-color: var(--color-warning);
		background: var(--color-warning-bg);
	}

	.switch-row p {
		margin: var(--space-1) 0 0;
	}

	.switch-row strong {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
	}

	.form-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-3);
		margin-bottom: var(--space-4);
	}

	.bad {
		color: var(--color-danger);
	}

	select,
	.amount {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-size: var(--text-sm);
	}

	.amount {
		width: 8.5rem;
	}

	h2 {
		margin: 0 0 var(--space-3);
	}
</style>
