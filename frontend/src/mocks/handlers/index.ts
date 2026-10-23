// Un archivo de handlers por módulo del contrato. Al agregar un endpoint, se agrega aquí su mock.
import { authHandlers } from './auth';

export const handlers = [...authHandlers];
