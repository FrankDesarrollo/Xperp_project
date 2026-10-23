export interface AppNotification {
	id: number;
	title: string;
	body: string;
	link?: string;
	read: boolean;
}

/** Notificaciones del usuario; se alimenta por SSE (`notification.created`). */
class NotificationsStore {
	items = $state<AppNotification[]>([]);
	unread = $derived(this.items.filter((n) => !n.read).length);
}

export const notifications = new NotificationsStore();
