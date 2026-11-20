<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { z } from 'zod';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import { auth } from '$lib/stores/auth.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
	import { Check } from '@lucide/svelte';
	import { ROLE_LABEL } from '$lib/utils/labels';

	const schema = z.object({
		email: z.email('Escribe un correo válido'),
		password: z.string().min(1, 'Escribe la contraseña')
	});

	const demoUsers = [
		{ email: 'admin@laceiba.demo', role: 'admin', name: 'Administrador Demo' },
		{ email: 'gerencia@laceiba.demo', role: 'manager', name: 'Laura Méndez' },
		{ email: 'compras@laceiba.demo', role: 'purchasing', name: 'Andrés Rojas' },
		{ email: 'bodega.cali@laceiba.demo', role: 'warehouse', name: 'Jhon Caicedo · CAL-01' },
		{ email: 'bodega.bogota@laceiba.demo', role: 'warehouse', name: 'Diana Pardo · BOG-01' },
		{ email: 'analista@laceiba.demo', role: 'viewer', name: 'Camila Torres' }
	];

	let email = $state('admin@laceiba.demo');
	let password = $state('');
	let errors = $state<{ email?: string; password?: string; form?: string }>({});
	let loading = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const parsed = schema.safeParse({ email, password });
		if (!parsed.success) {
			const flat = z.flattenError(parsed.error).fieldErrors;
			errors = { email: flat.email?.[0], password: flat.password?.[0] };
			return;
		}
		errors = {};
		loading = true;
		try {
			const session = await unwrap(api.POST('/auth/login', { body: parsed.data }));
			auth.setSession(session.access_token, session.user);
			const home = session.user.role === 'warehouse' ? '/bodega' : '/tablero';
			await goto(resolve(home));
		} catch (err) {
			errors = {
				form: err instanceof ApiError ? err.message : 'No se pudo conectar con el servidor'
			};
		} finally {
			loading = false;
		}
	}

	function pick(u: (typeof demoUsers)[number]) {
		email = u.email;
		password = 'Xperp2026*';
	}
</script>

<svelte:head>
	<title>Iniciar sesión · Xperp</title>
</svelte:head>

<main class="login">
	<section class="intro" aria-labelledby="intro-title">
		<div class="brand">
			<span class="logo" aria-hidden="true">
				<svg viewBox="0 0 24 24" width="20" height="20" fill="none">
					<path
						d="M5 5l14 14M19 5L5 19"
						stroke="currentColor"
						stroke-width="2.6"
						stroke-linecap="round"
					/>
				</svg>
			</span>
			<strong>Xperp</strong>
		</div>
		<h1 id="intro-title">La IA analiza y recomienda. Tu equipo decide.</h1>
		<p class="lead">
			ERP para distribución que detecta lo que necesita atención, propone la acción con su impacto
			económico y espera la aprobación de quien corresponde.
		</p>
		<ul class="points">
			<li>
				<span class="tick" aria-hidden="true"><Check size={12} strokeWidth={3} /></span>Inventario,
				compras, ventas y despachos en un solo lugar
			</li>
			<li>
				<span class="tick" aria-hidden="true"><Check size={12} strokeWidth={3} /></span
				>Recomendaciones con su costo, su efecto en el presupuesto y su riesgo
			</li>
			<li>
				<span class="tick" aria-hidden="true"><Check size={12} strokeWidth={3} /></span>La IA se
				activa con eventos y condiciones, no corre todo el tiempo
			</li>
		</ul>
	</section>

	<section class="panel" aria-labelledby="form-title">
		<h2 id="form-title">Iniciar sesión</h2>
		<p class="sub">Distribuidora La Ceiba S.A.S. · empresa demo</p>
		<form onsubmit={submit} novalidate>
			<Field label="Correo" error={errors.email}>
				<input type="email" autocomplete="username" bind:value={email} />
			</Field>
			<Field label="Contraseña" error={errors.password}>
				<input type="password" autocomplete="current-password" bind:value={password} />
			</Field>
			{#if errors.form}<p class="error" role="alert">{errors.form}</p>{/if}
			<Button type="submit" variant="primary" size="lg" {loading}>Entrar</Button>
		</form>

		<div class="demo">
			<span class="demo-title">Entrar como usuario demo</span>
			<ul>
				{#each demoUsers as u (u.email)}
					<li>
						<button type="button" class:selected={email === u.email} onclick={() => pick(u)}>
							<span class="who">{u.name}</span>
							<span class="role">{ROLE_LABEL[u.role]}</span>
						</button>
					</li>
				{/each}
			</ul>
			<p class="hint">Contraseña de todos: <code>Xperp2026*</code></p>
		</div>
	</section>
</main>

<style>
	.login {
		display: grid;
		grid-template-columns: minmax(0, 1.1fr) minmax(0, 440px);
		gap: var(--space-7);
		align-items: center;
		max-width: 1080px;
		min-height: 100dvh;
		margin: 0 auto;
		padding: var(--space-7) var(--space-5);
	}

	/* ---------- Presentación ---------- */
	.intro {
		animation: rise var(--duration-reveal) var(--ease-out) both;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-bottom: var(--space-7);
		font-size: var(--text-lg);
		letter-spacing: var(--tracking-tight);
	}

	.logo {
		display: grid;
		place-items: center;
		width: 2.25rem;
		height: 2.25rem;
		border-radius: var(--radius-md);
		background: linear-gradient(140deg, var(--color-primary), var(--color-primary-hover));
		color: var(--color-on-primary);
		box-shadow: var(--shadow-sm);
	}

	h1 {
		max-width: 16ch;
		margin: 0 0 var(--space-4);
		font-size: clamp(2rem, 3.4vw, 2.75rem);
		line-height: 1.1;
		letter-spacing: -0.03em;
	}

	.lead {
		max-width: 46ch;
		margin: 0 0 var(--space-6);
		font-size: var(--text-lg);
		color: var(--color-text-muted);
	}

	.points {
		display: grid;
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
		color: var(--color-text);
		font-size: var(--text-sm);
	}

	.points li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.tick {
		flex: none;
		width: 1.25rem;
		height: 1.25rem;
		border-radius: var(--radius-full);
		display: grid;
		place-items: center;
		background: var(--color-primary);
		color: var(--color-on-primary);
	}

	/* ---------- Formulario ---------- */
	.panel {
		padding: var(--space-6);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg);
		animation: rise var(--duration-reveal) var(--ease-out) 120ms both;
	}

	.panel h2 {
		margin: 0;
		font-size: var(--text-xl);
		letter-spacing: var(--tracking-tight);
	}

	.sub {
		margin: var(--space-1) 0 var(--space-5);
		font-size: var(--text-sm);
		color: var(--color-text-muted);
	}

	form {
		display: grid;
		gap: var(--space-4);
	}

	.error {
		margin: 0;
		font-size: var(--text-sm);
		color: var(--color-danger);
	}

	.demo {
		margin-top: var(--space-5);
		padding-top: var(--space-5);
		border-top: 1px solid var(--color-border-subtle);
	}

	.demo-title {
		display: block;
		margin-bottom: var(--space-3);
		font-size: var(--text-xs);
		font-weight: var(--weight-medium);
		letter-spacing: var(--tracking-wide);
		text-transform: uppercase;
		color: var(--color-text-muted);
	}

	.demo ul {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.demo button {
		display: grid;
		gap: 1px;
		width: 100%;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		text-align: left;
		cursor: pointer;
		transition:
			border-color var(--duration-fast) var(--ease-out),
			background var(--duration-fast) var(--ease-out);
	}

	.demo button:hover {
		border-color: var(--color-border-strong);
		background: var(--color-surface-hover);
	}

	.demo button.selected {
		border-color: var(--color-primary);
		background: var(--color-primary-soft);
	}

	.who {
		overflow: hidden;
		font-size: var(--text-sm);
		font-weight: var(--weight-medium);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.role {
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	.hint {
		margin: var(--space-3) 0 0;
		font-size: var(--text-xs);
		color: var(--color-text-muted);
	}

	code {
		font-family: var(--font-mono);
		color: var(--color-text);
	}

	@keyframes rise {
		from {
			opacity: 0;
			transform: translateY(12px);
		}
	}

	@media (max-width: 900px) {
		.login {
			grid-template-columns: minmax(0, 1fr);
			gap: var(--space-6);
			padding: var(--space-6) var(--space-4);
		}

		.brand {
			margin-bottom: var(--space-5);
		}

		.points {
			display: none;
		}
	}

	@media (max-width: 480px) {
		.demo ul {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
