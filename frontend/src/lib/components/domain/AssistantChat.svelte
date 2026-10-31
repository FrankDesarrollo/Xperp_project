<script lang="ts">
	import { tick } from 'svelte';
	import { Bot, Send, User, Wrench } from '@lucide/svelte';
	import { assistant } from '$lib/stores/assistant.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import { formatTime } from '$lib/utils/format';

	/** Chat con el asistente. Lo usan la pantalla «Asistente» y el panel flotante. */
	interface Props {
		suggestions: string[];
	}

	let { suggestions }: Props = $props();

	let input = $state('');
	let list = $state<HTMLDivElement>();
	let field = $state<HTMLInputElement>();

	function submit(e: SubmitEvent) {
		e.preventDefault();
		const text = input;
		input = '';
		void assistant.send(text);
	}

	// Baja al último mensaje cada vez que llega uno o cambia el estado de «Consultando…».
	$effect(() => {
		void assistant.messages.length;
		void assistant.sending;
		void tick().then(() => list?.scrollTo({ top: list.scrollHeight, behavior: 'smooth' }));
	});

	export function focus() {
		field?.focus();
	}
</script>

<div class="chat">
	<div class="messages" bind:this={list} aria-live="polite">
		{#if assistant.messages.length === 0}
			<div class="intro">
				<Bot size={32} aria-hidden="true" />
				<p>
					Hola, soy el asistente de Xperp. Respondo con los datos de la operación y te digo qué
					recomienda la IA. Solo consulto: nunca ejecuto acciones.
				</p>
				<div class="suggestions">
					{#each suggestions as s (s)}
						<button onclick={() => assistant.send(s)}>{s}</button>
					{/each}
				</div>
			</div>
		{/if}
		{#each assistant.messages as m (m.id)}
			<div class="msg {m.role}">
				<span class="avatar" aria-hidden="true"
					>{#if m.role === 'user'}<User size={16} />{:else}<Bot size={16} />{/if}</span
				>
				<div class="bubble">
					<span class="visually-hidden">{m.role === 'user' ? 'Tú' : 'Asistente'}:</span>
					<p>{m.content}</p>
					<span class="meta">
						{formatTime(m.created_at)}
						{#if m.tool_calls && m.tool_calls.length > 0}
							· <Wrench size={12} aria-hidden="true" />
							<span title="Herramientas de lectura que consultó"
								>{m.tool_calls.map((t) => String(t.tool)).join(', ')}</span
							>
						{/if}
					</span>
				</div>
			</div>
		{/each}
		{#if assistant.sending}
			<div class="msg assistant" role="status">
				<span class="avatar" aria-hidden="true"><Bot size={16} /></span>
				<div class="bubble typing">Consultando los datos…</div>
			</div>
		{/if}
	</div>
	<form onsubmit={submit}>
		<label class="visually-hidden" for="assistant-question">Pregunta para el asistente</label>
		<input
			id="assistant-question"
			bind:this={field}
			bind:value={input}
			placeholder="Escribe tu pregunta…"
			maxlength="2000"
			autocomplete="off"
			enterkeyhint="send"
		/>
		<Button
			type="submit"
			variant="ai"
			loading={assistant.sending}
			disabled={!input.trim()}
			aria-label="Enviar pregunta"
			><Send size={16} aria-hidden="true" /><span class="send-label">Enviar</span></Button
		>
	</form>
</div>

<style>
	.chat {
		display: flex;
		flex-direction: column;
		height: 100%;
		min-height: 0;
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
		padding: var(--space-5) 0;
	}

	.intro p {
		max-width: 46ch;
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
		color: var(--color-text);
		border-radius: var(--radius-full);
		padding: var(--space-2) var(--space-3);
		min-height: 2.75rem;
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
		min-width: 0;
	}

	.user .bubble {
		background: var(--color-primary);
		color: var(--color-on-primary);
	}

	.bubble p {
		margin: 0;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}

	.meta {
		display: inline-flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-1);
		font-size: var(--text-xs);
		opacity: 0.75;
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
		background: var(--color-surface);
	}

	form input {
		flex: 1;
		min-width: 0;
		min-height: 2.75rem;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		font-size: var(--text-md);
	}

	@media (max-width: 600px) {
		.send-label {
			display: none;
		}
	}
</style>
