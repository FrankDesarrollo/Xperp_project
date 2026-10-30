<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { CheckCircle2, Circle, FastForward, Pause, Play, RotateCcw } from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { Scenario } from '$lib/api/types';
	import { clock } from '$lib/stores/clock.svelte';
	import { live } from '$lib/stores/live.svelte';
	import { toasts } from '$lib/stores/toasts.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import Modal from '$lib/components/ui/Modal.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import { formatDateTime } from '$lib/utils/format';

	let scenarios = $state<Scenario[]>([]);
	let speed = $state(clock.secondsPerDay);
	let hours = $state(24);
	let busy = $state<string | null>(null);
	let confirmReset = $state(false);

	const loader = new Loader();

	/** Toda carga pasa por el Loader: cargando, error con salida o datos. */
	function loadScenarios() {
		return loader.run(fetchScenarios);
	}

	async function fetchScenarios() {
		scenarios = await unwrap(api.GET('/simulation/scenarios'));
	}

	async function run(kind: string, action: () => Promise<unknown>, done?: string) {
		busy = kind;
		try {
			const c = (await action()) as Parameters<typeof clock.apply>[0];
			clock.apply(c);
			if (done) toasts.success(done);
			await loadScenarios();
		} catch (e) {
			toasts.error('No se pudo completar', e instanceof ApiError ? e.message : undefined);
		} finally {
			busy = null;
		}
	}

	onMount(() => {
		void loadScenarios();
		const off = live.on('clock.tick', () => {
			if (scenarios.length > 0 && clock.scenarioCursor !== scenarios.filter((s) => s.done).length)
				void loadScenarios();
		});
		return off;
	});

	let progress = $derived(
		clock.scenariosTotal ? Math.round((clock.scenarioCursor / clock.scenariosTotal) * 100) : 0
	);
</script>

<svelte:head><title>Simulación · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Simulación</h1>
		<p>
			Reloj simulado de la demo: 30 días de operación de Distribuidora La Ceiba con su guion de
			escenarios
		</p>
	</div>
</div>

<div class="grid-2">
	<section class="card clock-card">
		<span class="text-muted small">Hora simulada</span>
		<span class="time mono">{formatDateTime(clock.simTime)}</span>
		<div class="row">
			<Badge tone={clock.status === 'running' ? 'success' : 'neutral'}
				>{clock.status === 'running' ? 'En marcha' : 'En pausa'}</Badge
			>
			<span class="small text-muted"
				>Día {clock.dayNumber} · un día dura {clock.secondsPerDay} s reales</span
			>
		</div>
		<div class="row">
			{#if clock.status === 'running'}
				<Button
					size="lg"
					loading={busy === 'pause'}
					onclick={() => run('pause', () => unwrap(api.POST('/simulation/pause')))}
					><Pause size={20} />Pausar</Button
				>
			{:else}
				<Button
					size="lg"
					variant="primary"
					loading={busy === 'start'}
					onclick={() =>
						run('start', () => unwrap(api.POST('/simulation/start')), 'Simulación en marcha')}
				>
					<Play size={20} />Iniciar
				</Button>
			{/if}
		</div>
	</section>

	<section class="card stack">
		<div class="row end">
			<Field label="Segundos reales por día simulado" hint="20 = un mes en unos 10 minutos">
				<input type="number" min="2" max="3600" bind:value={speed} />
			</Field>
			<Button
				loading={busy === 'speed'}
				onclick={() =>
					run(
						'speed',
						() =>
							unwrap(api.PUT('/simulation/speed', { body: { seconds_per_day: Number(speed) } })),
						'Velocidad actualizada'
					)}
			>
				Cambiar velocidad
			</Button>
		</div>
		<div class="row end">
			<Field label="Avanzar horas (con la simulación en pausa)" hint="Útil para ensayar la demo">
				<input type="number" min="1" max="240" bind:value={hours} />
			</Field>
			<Button
				loading={busy === 'advance'}
				disabled={clock.status === 'running'}
				onclick={() =>
					run(
						'advance',
						() =>
							unwrap(
								api.POST('/simulation/advance', { params: { query: { hours: Number(hours) } } })
							),
						`Reloj adelantado ${hours} h`
					)}
			>
				<FastForward size={16} />Avanzar
			</Button>
		</div>
		<div class="row end">
			<p class="small text-muted grow">
				Reiniciar borra todo y vuelve a cargar la empresa demo con la misma semilla.
			</p>
			<Button variant="danger" onclick={() => (confirmReset = true)}
				><RotateCcw size={16} />Reiniciar demo</Button
			>
		</div>
	</section>
</div>

<LoadState {loader} what="el guion de escenarios" onretry={loadScenarios}>
	<section class="card scenarios">
		<div class="row">
			<h2>Guion de escenarios</h2>
			<span class="spacer"></span>
			<span class="small text-muted"
				>{clock.scenarioCursor} de {clock.scenariosTotal} · {progress} %</span
			>
		</div>
		<div
			class="bar"
			role="progressbar"
			aria-valuenow={progress}
			aria-valuemin="0"
			aria-valuemax="100"
		>
			<span style:width="{progress}%"></span>
		</div>
		<ol>
			{#each scenarios as s (s.index)}
				<li class:done={s.done}>
					<span class="icon" aria-hidden="true"
						>{#if s.done}<CheckCircle2 size={18} />{:else}<Circle size={18} />{/if}</span
					>
					<div>
						<strong>Día {s.day} · {s.time} — {s.title}</strong>
						<p class="small text-muted">Qué debe hacer Xperp: {s.expected}</p>
					</div>
				</li>
			{/each}
		</ol>
	</section>
</LoadState>

<Modal open={confirmReset} title="¿Reiniciar la demo?" onclose={() => (confirmReset = false)}>
	<p>
		Se borran todos los pedidos, compras, decisiones y movimientos, y se vuelve a cargar la empresa
		demo desde el día 1. Tarda unos 30 segundos.
	</p>
	{#snippet actions()}
		<Button onclick={() => (confirmReset = false)}>Cancelar</Button>
		<Button
			variant="danger"
			loading={busy === 'reset'}
			onclick={async () => {
				await run('reset', () => unwrap(api.POST('/simulation/reset')), 'Demo reiniciada');
				confirmReset = false;
			}}
		>
			Sí, reiniciar
		</Button>
	{/snippet}
</Modal>

<style>
	.clock-card {
		display: grid;
		gap: var(--space-3);
		align-content: start;
	}

	.time {
		font-size: 2.25rem;
		font-weight: var(--weight-bold);
	}

	.end {
		align-items: flex-end;
	}

	.grow {
		flex: 1;
		margin: 0;
	}

	.scenarios {
		margin-top: var(--space-4);
	}

	.bar {
		height: 8px;
		background: var(--color-surface-muted);
		border-radius: var(--radius-full);
		overflow: hidden;
		margin-bottom: var(--space-4);
	}

	.bar span {
		display: block;
		height: 100%;
		background: var(--color-primary);
		transition: width var(--duration-base);
	}

	ol {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-3);
	}

	li {
		display: flex;
		gap: var(--space-3);
	}

	li p {
		margin: 2px 0 0;
	}

	.icon {
		color: var(--color-text-muted);
	}

	li.done .icon {
		color: var(--color-success);
	}

	h2 {
		margin: 0;
	}
</style>
