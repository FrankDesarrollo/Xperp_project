<script lang="ts">
	interface Tab {
		id: string;
		label: string;
		count?: number;
	}

	interface Props {
		tabs: Tab[];
		active: string;
		onchange: (id: string) => void;
	}

	let { tabs, active, onchange }: Props = $props();
</script>

<div class="tabs" role="tablist">
	{#each tabs as tab (tab.id)}
		<button
			role="tab"
			aria-selected={tab.id === active}
			class:active={tab.id === active}
			onclick={() => onchange(tab.id)}
		>
			{tab.label}
			{#if tab.count !== undefined}<span class="count">{tab.count}</span>{/if}
		</button>
	{/each}
</div>

<style>
	.tabs {
		display: flex;
		gap: var(--space-1);
		border-bottom: 1px solid var(--color-border);
		margin-bottom: var(--space-4);
		overflow-x: auto;
	}

	button {
		border: none;
		background: transparent;
		padding: var(--space-2) var(--space-4);
		border-bottom: 2px solid transparent;
		margin-bottom: -1px;
		cursor: pointer;
		color: var(--color-text-muted);
		font-weight: var(--weight-medium);
		white-space: nowrap;
		display: inline-flex;
		gap: var(--space-2);
		align-items: center;
	}

	button.active {
		color: var(--color-primary);
		border-bottom-color: var(--color-primary);
	}

	.count {
		background: var(--color-surface-muted);
		border-radius: var(--radius-full);
		padding: 0 var(--space-2);
		font-size: var(--text-xs);
	}
</style>
