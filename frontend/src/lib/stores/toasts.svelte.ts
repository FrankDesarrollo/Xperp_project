export type ToastKind = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
	id: number;
	title: string;
	body?: string;
	kind: ToastKind;
}

/** Avisos flotantes que desaparecen solos. */
class ToastStore {
	items = $state<Toast[]>([]);
	private seq = 0;

	show(title: string, body?: string, kind: ToastKind = 'info', ms = 5000) {
		const id = ++this.seq;
		this.items = [...this.items.slice(-3), { id, title, body, kind }];
		setTimeout(() => this.dismiss(id), ms);
	}

	success(title: string, body?: string) {
		this.show(title, body, 'success');
	}

	error(title: string, body?: string) {
		this.show(title, body, 'danger', 8000);
	}

	dismiss(id: number) {
		this.items = this.items.filter((t) => t.id !== id);
	}
}

export const toasts = new ToastStore();
