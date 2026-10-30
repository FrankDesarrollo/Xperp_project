<script lang="ts">
	import { onMount } from 'svelte';
	import LoadState from '$lib/components/ui/LoadState.svelte';
	import { Loader } from '$lib/utils/loader.svelte';
	import { api, unwrap } from '$lib/api/client';
	import type { Carrier, Category, Company, UserSummary, Warehouse } from '$lib/api/types';
	import { theme, type ThemeChoice } from '$lib/stores/theme.svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import { formatCop } from '$lib/utils/format';
	import { ROLE_LABEL } from '$lib/utils/labels';

	let company = $state<Company | null>(null);
	let users = $state<UserSummary[]>([]);
	let warehouses = $state<Warehouse[]>([]);
	let categories = $state<Category[]>([]);
	let carriers = $state<Carrier[]>([]);

	const loader = new Loader();

	function load() {
		return loader.run(async () => {
			[company, users, warehouses, categories, carriers] = await Promise.all([
				unwrap(api.GET('/company')),
				unwrap(api.GET('/users')),
				unwrap(api.GET('/warehouses')),
				unwrap(api.GET('/categories')),
				unwrap(api.GET('/carriers'))
			]);
		});
	}

	onMount(load);

	const themes: { id: ThemeChoice; label: string }[] = [
		{ id: 'system', label: 'Del sistema' },
		{ id: 'light', label: 'Claro' },
		{ id: 'dark', label: 'Oscuro' }
	];
</script>

<svelte:head><title>Configuración · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Configuración</h1>
		<p>Empresa, bodegas, usuarios y preferencias de la interfaz</p>
	</div>
</div>

<LoadState {loader} what="los datos de la empresa" onretry={load}>
	<div class="grid-2">
		<section class="card">
			<h2>Empresa</h2>
			{#if company}
				<dl class="facts">
					<dt>Razón social</dt>
					<dd>{company.name}</dd>
					<dt>NIT</dt>
					<dd>{company.nit} <Badge>de prueba</Badge></dd>
					<dt>Sede</dt>
					<dd>{company.city}</dd>
					<dt>Zona horaria</dt>
					<dd>{company.timezone}</dd>
					<dt>Moneda</dt>
					<dd>{company.currency}</dd>
				</dl>
				<p class="small text-muted note">
					Empresa ficticia con datos demo. Ninguna marca, persona o socio es real.
				</p>
			{/if}
		</section>
		<section class="card">
			<h2>Apariencia</h2>
			<div class="row">
				{#each themes as t (t.id)}
					<label class="row small"
						><input
							type="radio"
							name="theme"
							checked={theme.choice === t.id}
							onchange={() => theme.set(t.id)}
						/>{t.label}</label
					>
				{/each}
			</div>
			<h2 class="mt">Categorías</h2>
			<div class="row">
				{#each categories as c (c.id)}<Badge tone="info">{c.code} · {c.name}</Badge>{/each}
			</div>
		</section>
	</div>

	<section class="card mt-card">
		<h2>Bodegas</h2>
		<div class="table-wrap">
			<table class="data">
				<thead><tr><th>Código</th><th>Nombre</th><th>Ciudad</th><th>Dirección</th></tr></thead>
				<tbody>
					{#each warehouses as w (w.id)}
						<tr
							><td class="mono">{w.code}</td><td>{w.name}</td><td>{w.city}</td><td
								>{w.address ?? '—'}</td
							></tr
						>
					{/each}
				</tbody>
			</table>
		</div>
	</section>

	<section class="card mt-card">
		<h2>Usuarios</h2>
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Bodega</th><th>Estado</th></tr></thead
				>
				<tbody>
					{#each users as u (u.id)}
						<tr>
							<td>{u.full_name}</td>
							<td class="mono">{u.email}</td>
							<td>{ROLE_LABEL[u.role] ?? u.role}</td>
							<td>{u.warehouse_code ?? '—'}</td>
							<td
								><Badge tone={u.is_active ? 'success' : 'neutral'}
									>{u.is_active ? 'Activo' : 'Inactivo'}</Badge
								></td
							>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	</section>

	<section class="card mt-card">
		<h2>Transportadoras</h2>
		<div class="table-wrap">
			<table class="data">
				<thead
					><tr
						><th>Código</th><th>Nombre</th><th>Cobertura</th><th class="num">Tarifa base</th><th
							class="num">Por kg</th
						></tr
					></thead
				>
				<tbody>
					{#each carriers as c (c.id)}
						<tr>
							<td class="mono">{c.code}</td>
							<td>{c.name}</td>
							<td class="small">{c.national ? 'Nacional' : c.coverage_cities.join(', ')}</td>
							<td class="num">{formatCop(c.base_rate)}</td>
							<td class="num">{formatCop(c.rate_per_kg)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	</section>
</LoadState>

<style>
	.mt {
		margin-top: var(--space-5);
	}

	.mt-card {
		margin-top: var(--space-4);
	}

	.note {
		margin-top: var(--space-4);
	}
</style>
