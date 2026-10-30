package com.xperp.logistics.domain;

import com.xperp.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "warehouse_tasks")
public class WarehouseTask extends TenantEntity {

  @Column(name = "warehouse_id", nullable = false)
  private Long warehouseId;

  @Column(nullable = false, length = 20)
  private String type;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private int priority;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String detail;

  @Column(name = "sales_order_id")
  private Long salesOrderId;

  @Column(name = "purchase_order_id")
  private Long purchaseOrderId;

  @Column(name = "stock_transfer_id")
  private Long stockTransferId;

  @Column(name = "product_id")
  private Long productId;

  @Column(name = "reference_number", length = 30)
  private String referenceNumber;

  @Column(name = "depends_on_task_id")
  private Long dependsOnTaskId;

  @Column(name = "assigned_to_user_id")
  private Long assignedToUserId;

  @Column(name = "handled_by_human", nullable = false)
  private boolean handledByHuman;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "completed_by")
  private Long completedBy;

  @Column(name = "ai_decision_id")
  private Long aiDecisionId;
}
