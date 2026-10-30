import { api, unwrap } from '$lib/api/client';
import type { Notification } from '$lib/api/types';

/** Notificaciones del usuario; se cargan al entrar y se alimentan por SSE (`notification.created`). */
class NotificationsStore {
	items = $state<Notification[]>([]);
	unread = $state(0);

	async load() {
		const page = await unwrap(api.GET('/notifications', { params: { query: { size: 30 } } }));
		this.items = page.items;
		this.unread = page.unread;
	}

	push(n: Notification) {
		this.items = [n, ...this.items].slice(0, 50);
		this.unread += 1;
	}

	async markRead(id: number) {
		await api.POST('/notifications/{id}/read', { params: { path: { id } } });
		const n = this.items.find((i) => i.id === id);
		if (n && !n.read_at) {
			n.read_at = new Date().toISOString();
			this.unread = Math.max(0, this.unread - 1);
		}
	}

	async markAllRead() {
		await api.POST('/notifications/read-all');
		const now = new Date().toISOString();
		this.items = this.items.map((n) => ({ ...n, read_at: n.read_at ?? now }));
		this.unread = 0;
	}
}

export const notifications = new NotificationsStore();
