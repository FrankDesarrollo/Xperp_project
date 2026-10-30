package com.xperp.shared.persistence;

import com.xperp.shared.time.ClockHolder;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base para entidades de negocio con llave propia (compuesta o natural): {@code company_id} y
 * marcas de tiempo, sin {@code id} identity.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TimestampedTenantEntity {

  @TenantId
  @Column(name = "company_id", nullable = false, updatable = false)
  private Long companyId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = ClockHolder.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = ClockHolder.now();
  }
}
