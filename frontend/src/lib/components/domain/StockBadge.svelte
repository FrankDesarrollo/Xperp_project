<script lang="ts">
	import { ArrowDown, ArrowUp, Check, CircleSlash } from '@lucide/svelte';
	import Badge from '$lib/components/ui/Badge.svelte';
	import { STOCK_STATUS } from '$lib/utils/labels';

	interface Props {
		status: string;
	}

	let { status }: Props = $props();

	const ICONS = { stockout: CircleSlash, low: ArrowDown, ok: Check, over: ArrowUp } as const;

	let s = $derived(STOCK_STATUS[status] ?? { label: status, tone: 'neutral' as const });
	let Icon = $derived(ICONS[status as keyof typeof ICONS] ?? Check);
</script>

<!-- El semáforo lleva ícono y texto, no solo color (AGENTS.md, 11.2) -->
<Badge tone={s.tone}><Icon size={12} strokeWidth={2.5} aria-hidden="true" />{s.label}</Badge>
