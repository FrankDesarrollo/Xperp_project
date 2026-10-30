<script lang="ts">
	import Badge from '$lib/components/ui/Badge.svelte';
	import type { Tone } from '$lib/utils/labels';

	interface Props {
		map: Record<string, { label: string; tone: Tone }>;
		value: string | null | undefined;
	}

	let { map, value }: Props = $props();
	let entry = $derived((value && map[value]) || { label: value ?? '—', tone: 'neutral' as Tone });
</script>

<Badge tone={entry.tone}>{entry.label}</Badge>
