<script lang="ts">
	import Badge from '$lib/components/ui/Badge.svelte';
	import { STOCK_STATUS } from '$lib/utils/labels';

	interface Props {
		status: string;
	}

	let { status }: Props = $props();
	let s = $derived(STOCK_STATUS[status] ?? { label: status, tone: 'neutral' as const, icon: '•' });
</script>

<!-- El semáforo lleva ícono y texto, no solo color (AGENTS.md, 11.2) -->
<Badge tone={s.tone}><span aria-hidden="true">{s.icon}</span>{s.label}</Badge>
