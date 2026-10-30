<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { z } from 'zod';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import { auth } from '$lib/stores/auth.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import Field from '$lib/components/ui/Field.svelte';
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
	<section class="panel">
		<div class="brand">
			<span class="logo" aria-hidden="true">X</span>
			<div>
				<h1>Xperp</h1>
				<p class="text-muted">El ERP donde la IA decide, ejecuta y hace seguimiento</p>
			</div>
		</div>
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
	</section>
	<section class="demo">
		<h2>Usuarios demo</h2>
		<p class="text-muted small">
			Distribuidora La Ceiba S.A.S. (empresa ficticia). Contraseña: <code>Xperp2026*</code>
		</p>
		<ul>
			{#each demoUsers as u (u.email)}
				<li>
					<button onclick={() => pick(u)}>
						<strong>{u.name}</strong>
						<span class="text-muted small">{ROLE_LABEL[u.role]} · {u.email}</span>
					</button>
				</li>
			{/each}
		</ul>
	</section>
</main>

<style>
	.login {
		min-height: 100dvh;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
		gap: var(--space-6);
		align-items: center;
		max-width: 960px;
		margin: 0 auto;
		padding: var(--space-6) var(--space-4);
	}

	.panel,
	.demo {
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		padding: var(--space-6);
		box-shadow: var(--shadow-md);
	}

	.brand {
		display: flex;
		gap: var(--space-4);
		align-items: center;
		margin-bottom: var(--space-6);
	}

	.brand h1 {
		margin: 0;
	}

	.brand p {
		margin: 0;
	}

	.logo {
		width: 3rem;
		height: 3rem;
		border-radius: var(--radius-lg);
		background: var(--color-primary);
		color: var(--color-on-primary);
		display: grid;
		place-items: center;
		font-weight: var(--weight-bold);
		font-size: var(--text-xl);
	}

	form {
		display: grid;
		gap: var(--space-4);
	}

	.error {
		color: var(--color-danger);
		margin: 0;
		font-size: var(--text-sm);
	}

	ul {
		list-style: none;
		padding: 0;
		margin: 0;
		display: grid;
		gap: var(--space-2);
	}

	ul button {
		width: 100%;
		text-align: left;
		display: grid;
		gap: 2px;
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-bg);
		cursor: pointer;
	}

	ul button:hover {
		border-color: var(--color-primary);
	}

	code {
		font-family: var(--font-mono);
	}
</style>
