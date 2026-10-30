// Un archivo de handlers por módulo del contrato. Al agregar un endpoint, se agrega aquí su mock.
import { authHandlers } from './auth';
import { platformHandlers } from './platform';

export const handlers = [...authHandlers, ...platformHandlers];
