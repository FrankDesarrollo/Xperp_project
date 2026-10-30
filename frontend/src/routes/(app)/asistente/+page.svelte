<script lang="ts">
	import AssistantChat from '$lib/components/domain/AssistantChat.svelte';
	import Button from '$lib/components/ui/Button.svelte';
	import { assistant, ASSISTANT_SUGGESTIONS } from '$lib/stores/assistant.svelte';
</script>

<svelte:head><title>Asistente · Xperp</title></svelte:head>

<div class="page-header">
	<div>
		<h1>Asistente</h1>
		<p>
			Pregunta por inventario, ventas, compras, presupuesto, qué recomienda la IA o por qué tomó una
			decisión. Solo consulta: no ejecuta acciones.
		</p>
	</div>
	{#if assistant.messages.length > 0}
		<Button onclick={() => assistant.reset()}>Nueva conversación</Button>
	{/if}
</div>

<div class="card chat">
	<AssistantChat suggestions={ASSISTANT_SUGGESTIONS} />
</div>

<style>
	.chat {
		height: calc(100dvh - 220px);
		min-height: 420px;
		padding: 0;
		overflow: hidden;
	}
</style>
