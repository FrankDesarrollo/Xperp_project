/**
 * MSW en el navegador. Se activa con PUBLIC_API_MOCK=true y permite trabajar las pantallas
 * antes de que exista el endpoint (AGENTS.md, sección 12.2).
 */
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);
