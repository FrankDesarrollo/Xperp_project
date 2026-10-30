/** Alias cortos de los tipos generados desde el contrato (no se redefinen a mano). */
import type { components } from './schema';

type S = components['schemas'];

export type CurrentUser = S['CurrentUser'];
export type Role = S['Role'];
export type UserSummary = S['UserSummary'];
export type Company = S['Company'];
export type Category = S['Category'];
export type Product = S['Product'];
export type Warehouse = S['Warehouse'];
export type InventoryItem = S['InventoryItem'];
export type InventoryMovement = S['InventoryMovement'];
export type InventoryCount = S['InventoryCount'];
export type StockTransfer = S['StockTransfer'];
export type Customer = S['Customer'];
export type SalesOrder = S['SalesOrder'];
export type Supplier = S['Supplier'];
export type SupplierOffer = S['SupplierOffer'];
export type PurchaseOrder = S['PurchaseOrder'];
export type WarehouseTask = S['WarehouseTask'];
export type Carrier = S['Carrier'];
export type Shipment = S['Shipment'];
export type Budget = S['Budget'];
export type AccountPayable = S['AccountPayable'];
export type AiDecision = S['AiDecision'];
export type Approval = S['Approval'];
export type AutonomyPolicy = S['AutonomyPolicy'];
export type AiSettings = S['AiSettings'];
export type Alert = S['Alert'];
export type ForecastSeries = S['ForecastSeries'];
export type AssistantMessage = S['AssistantMessage'];
export type Kpis = S['Kpis'];
export type Series = S['Series'];
export type Notification = S['Notification'];
export type SimulationClock = S['SimulationClock'];
export type Scenario = S['Scenario'];
