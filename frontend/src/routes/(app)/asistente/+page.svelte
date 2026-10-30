<script lang="ts">
	import { tick } from 'svelte';
	import { Bot, Send, User, Wrench } from '@lucide/svelte';
	import { api, unwrap, ApiError } from '$lib/api/client';
	import type { AssistantMessage } from '$lib/api/types';
	import Button from '$lib/components/ui/Button.svelte';
	import { formatTime } from '$lib/utils/format';
	import { clock } from '$lib/stores/clock.svelte';

	const suggestions = [
		'¿Qué se agota esta semana?',
		'¿Por qué se le compró a Mayorista Central?',
		'¿Cuánto llevamos gastado en aseo?',
		'¿Cómo van las ventas del mes?',
		'¿Cuánto hay de ABA-001?',
		'¿Hay aprobaciones pendientes?'
	];

	let conversationId = $state<string | null>(null);
	let messages = $state<AssistantMessage[]>([]);
	let input = $state('');
	let sending = $state(false);
	let list: HTMLDivElement;

	async function send(text: string) {
		const content = text.trim();
		if (!content || sending) return;
		sending = true;
		input = '';
		messages = [
			...messages,
			{
				id: -Date.now(),
				role: 'user',
				content,
				created_at: clock.simTime ?? new Date().toISOString()
			}
		];
		await scroll();
		try {
			const reply = await unwrap(
				api.POST('/assistant/messages', { body: { conversation_id: conversationId, content } })
			);
			conversationId = reply.conversation_id;
			messages = [...messages, reply.message];
		} catch (e) {
			messages = [
				...messages,
				{
					id: -Date.now(),
					role: 'assistant',
					content: e instanceof ApiError ? e.message : 'No pude responder en este momento.',
					created_at: new Date().toISOString()
				}
			];
		} finally {
			sending = false;
			await scroll();
		}
	}

	async function scroll() {
		await tick();
		list?.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
	}
</script>

<svelte:head><title>Asistente · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Asistente</h1>
		<p>
			Pregunta sobre inventario, ventas, compras, presupuesto o por qué la IA tomó una decisión.
			Solo lectura.
		</p>
	</div>
	{#if messages.length > 0}
		<Button onclick={() => ((messages = []), (conversationId = null))}>Nueva conversación</Button>
	{/if}
</div>

<div class="chat card">
	<div class="messages" bind:this={list} aria-live="polite">
		{#if messages.length === 0}
			<div class="intro">
				<Bot size={32} />
				<p>
					Hola, soy el asistente de Xperp. Consulto los datos con herramientas de lectura; nunca
					ejecuto acciones.
				</p>
				<div class="suggestions">
					{#each suggestions as s (s)}
						<button onclick={() => send(s)}>{s}</button>
					{/each}
				</div>
			</div>
		{/if}
		{#each messages as m (m.id)}
			<div class="msg {m.role}">
				<span class="avatar" aria-hidden="true"
					>{#if m.role === 'user'}<User size={16} />{:else}<Bot size={16} />{/if}</span
				>
				<div class="bubble">
					<p>{m.content}</p>
					<span class="meta">
						{formatTime(m.created_at)}
						{#if m.tool_calls && m.tool_calls.length > 0}
							· <Wrench size={12} /> {m.tool_calls.map((t) => String(t.tool)).join(', ')}
						{/if}
					</span>
				</div>
			</div>
		{/each}
		{#if sending}<div class="msg assistant">
				<span class="avatar"><Bot size={16} /></span>
				<div class="bubble typing">Consultando…</div>
			</div>{/if}
	</div>
	<form onsubmit={(e) => (e.preventDefault(), send(input))}>
		<input
			bind:value={input}
			placeholder="Escribe tu pregunta…"
			aria-label="Pregunta"
			maxlength="2000"
		/>
		<Button type="submit" variant="ai" loading={sending} disabled={!input.trim()}
			><Send size={16} />Enviar</Button
		>
	</form>
</div>

<style>
	.chat {
		display: flex;
		flex-direction: column;
		height: calc(100dvh - 220px);
		min-height: 420px;
		padding: 0;
		overflow: hidden;
	}

	.messages {
		flex: 1;
		overflow-y: auto;
		padding: var(--space-5);
		display: grid;
		gap: var(--space-4);
		align-content: start;
	}

	.intro {
		text-align: center;
		color: var(--color-text-muted);
		display: grid;
		justify-items: center;
		gap: var(--space-3);
		padding: var(--space-6) 0;
	}

	.suggestions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		justify-content: center;
	}

	.suggestions button {
		border: 1px solid var(--color-border);
		background: var(--color-surface);
		border-radius: var(--radius-full);
		padding: var(--space-2) var(--space-3);
		cursor: pointer;
		font-size: var(--text-sm);
	}

	.suggestions button:hover {
		border-color: var(--color-ai);
		color: var(--color-ai);
	}

	.msg {
		display: flex;
		gap: var(--space-3);
		align-items: flex-start;
		max-width: 820px;
	}

	.msg.user {
		flex-direction: row-reverse;
		margin-left: auto;
	}

	.avatar {
		width: 2rem;
		height: 2rem;
		border-radius: var(--radius-full);
		display: grid;
		place-items: center;
		background: var(--color-ai-bg);
		color: var(--color-ai);
		flex-shrink: 0;
	}

	.user .avatar {
		background: var(--color-surface-muted);
		color: var(--color-text-muted);
	}

	.bubble {
		background: var(--color-surface-muted);
		border-radius: var(--radius-lg);
		padding: var(--space-3) var(--space-4);
		font-size: var(--text-sm);
	}

	.user .bubble {
		background: var(--color-primary);
		color: var(--color-on-primary);
	}

	.bubble p {
		margin: 0;
		white-space: pre-wrap;
	}

	.meta {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		font-size: var(--text-xs);
		opacity: 0.7;
		margin-top: var(--space-1);
	}

	.typing {
		color: var(--color-text-muted);
	}

	form {
		display: flex;
		gap: var(--space-3);
		padding: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	form input {
		flex: 1;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}
</style>
