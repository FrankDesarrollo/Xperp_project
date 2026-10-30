package com.xperp.notifications.service;

import com.xperp.ai.events.AiDecisionRecorded;
import com.xperp.ai.events.AlertRaised;
import com.xperp.ai.events.ApprovalRequested;
import com.xperp.catalog.api.CatalogService;
import com.xperp.inventory.api.InventoryService;
import com.xperp.logistics.events.WarehouseTaskCreated;
import com.xperp.purchasing.api.PurchaseOrderDto;
import com.xperp.purchasing.api.PurchasingService;
import com.xperp.purchasing.api.SupplierDto;
import com.xperp.purchasing.events.PurchaseOrderSent;
import com.xperp.purchasing.events.PurchaseOrderStatusChanged;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.security.UserDirectory;
import com.xperp.shared.time.SimTime;
import java.util.Map;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** A quién se avisa de qué (AGENTS.md, 7.5): en la app, por correo y por SSE. */
@Component
class NotificationListeners {

  /** Rol al que se notifica cada tipo de decisión ejecutada en nivel 2. */
  static final Map<String, String> ROLE_BY_DECISION =
      Map.of(
          "replenishment", "purchasing",
          "po_exception", "purchasing",
          "price_anomaly", "purchasing",
          "budget_prioritization", "manager",
          "stock_transfer", "manager",
          "demand_anomaly", "manager",
          "inventory_discrepancy", "manager",
          "reorder_params_update", "purchasing");

  /** Decisiones muy frecuentes que se resumen al final del día en vez de notificar una por una. */
  static final Set<String> QUIET = Set.of("carrier_selection", "fulfillment");

  private final NotificationServiceImpl notifications;
  private final MailService mail;
  private final PurchasingService purchasing;
  private final InventoryService inventory;
  private final CatalogService catalog;
  private final UserDirectory users;

  NotificationListeners(
      NotificationServiceImpl notifications,
      MailService mail,
      PurchasingService purchasing,
      InventoryService inventory,
      CatalogService catalog,
      UserDirectory users) {
    this.notifications = notifications;
    this.mail = mail;
    this.purchasing = purchasing;
    this.inventory = inventory;
    this.catalog = catalog;
    this.users = users;
  }

  @ApplicationModuleListener
  void on(AiDecisionRecorded e) {
    String link = "/ia/decisiones?id=" + e.decisionId();
    if ("daily_summary".equals(e.decisionType())) {
      notifications.notifyRole("manager", "daily_summary", e.title(), e.summary(), link, false);
      notifications.notifyRole("purchasing", "daily_summary", e.title(), e.summary(), link, false);
      return;
    }
    if ("failed".equals(e.status()) && !QUIET.contains(e.decisionType())) {
      notifications.notifyRole(
          "admin", "ai_failed", "Falló: " + e.title(), e.summary(), link, false);
      return;
    }
    if (!e.notifyUsers() || QUIET.contains(e.decisionType())) {
      return;
    }
    String role = ROLE_BY_DECISION.getOrDefault(e.decisionType(), "manager");
    notifications.notifyRole(
        role, "ai_decision", "La IA ejecutó: " + e.title(), e.summary(), link, false);
  }

  @ApplicationModuleListener
  void on(ApprovalRequested e) {
    String amount = e.amount() == null ? "" : " por " + Money.display(e.amount());
    notifications.notifyRole(
        e.requestedRole(),
        "approval_requested",
        "Aprobación pendiente: " + e.title(),
        "La IA necesita tu aprobación" + amount + ". Vence en 24 horas simuladas.",
        "/ia/aprobaciones",
        true);
  }

  @ApplicationModuleListener
  void on(AlertRaised e) {
    if ("critical".equals(e.severity())) {
      notifications.notifyRole("manager", "alert", "Alerta crítica", e.message(), "/tablero", true);
    }
  }

  /** La OC sale por correo al proveedor con el PDF adjunto (AGENTS.md, 4.2 paso 7). */
  @ApplicationModuleListener
  void on(PurchaseOrderSent e) {
    PurchaseOrderDto po = purchasing.order(e.purchaseOrderId());
    SupplierDto supplier = purchasing.supplier(e.supplierId());
    String warehouse = inventory.warehouse(e.warehouseId()).name();
    StringBuilder rows = new StringBuilder();
    for (PurchaseOrderDto.Line l : po.lines()) {
      var p = catalog.product(l.productId());
      rows.append(
          "<tr><td>%s</td><td>%s</td><td style='text-align:right'>%d</td><td style='text-align:right'>%s</td></tr>"
              .formatted(p.sku(), p.name(), l.quantity(), Money.display(l.unitCost())));
    }
    String html =
        """
        <p>Estimado equipo de %s:</p>
        <p>Adjuntamos la orden de compra <b>%s</b> por <b>%s</b> para entregar en %s.</p>
        <table border='1' cellpadding='4' cellspacing='0'>
          <tr><th>SKU</th><th>Producto</th><th>Cantidad</th><th>Costo</th></tr>%s
        </table>
        <p>%s</p>
        <p>Distribuidora La Ceiba S.A.S. · Datos demo</p>
        """
            .formatted(
                supplier.name(),
                po.number(),
                Money.display(po.total()),
                warehouse,
                rows,
                e.createdByAi()
                    ? "Orden generada por Xperp IA."
                    : "Orden creada por el área de compras.");
    mail.send(
        supplier.email(),
        "Orden de compra " + po.number() + " · Distribuidora La Ceiba",
        html,
        po.number() + ".pdf",
        purchasing.pdf(po.id()));
  }

  @ApplicationModuleListener
  void on(PurchaseOrderStatusChanged e) {
    if (("rejected".equals(e.status()) || "delayed".equals(e.status()))
        && !e.status().equals(e.previousStatus())) {
      String title =
          ("rejected".equals(e.status()) ? "OC rechazada: " : "OC retrasada: ") + e.number();
      String body =
          "delayed".equals(e.status())
              ? "Nueva fecha: "
                  + SimTime.display(e.expectedAt())
                  + ". "
                  + (e.reason() == null ? "" : e.reason())
              : e.reason() == null ? "El proveedor rechazó la orden." : e.reason();
      notifications.notifyRole(
          "purchasing", "purchase_order", title, body, "/compras?oc=" + e.purchaseOrderId(), false);
    }
  }

  /** Aviso a bodega de lo que tiene que preparar: recepciones, conteos y traslados. */
  @ApplicationModuleListener
  void on(WarehouseTaskCreated e) {
    if (Set.of("pick", "pack").contains(e.type()) || e.assignedToUserId() == null) {
      return;
    }
    notifications.notifyUser(
        e.assignedToUserId(),
        "task",
        "Nueva tarea: " + e.title(),
        "Revisa tus tareas en Bodega.",
        "/bodega");
    users
        .byId(e.assignedToUserId())
        .ifPresent(
            u ->
                mail.send(
                    u.email(),
                    "Nueva tarea en bodega: " + e.title(),
                    "<p>Hola %s,</p><p>Xperp te asignó la tarea <b>%s</b>. Ábrela en la sección Bodega.</p>"
                        .formatted(u.fullName(), e.title()),
                    null,
                    null));
  }
}
