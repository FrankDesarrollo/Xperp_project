<script lang="ts">
	import '$lib/styles/tokens.css';
	import '$lib/styles/base.css';
	import '$lib/styles/utilities.css';
	import { onMount } from 'svelte';
	import Toasts from '$lib/components/ui/Toasts.svelte';
	import { theme } from '$lib/stores/theme.svelte';

	let { children } = $props();

	onMount(() => theme.init());
</script>

{@render children()}
<Toasts />
