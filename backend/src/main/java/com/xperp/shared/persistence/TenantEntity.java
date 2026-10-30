package com.xperp.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/** Entidad de negocio: lleva {@code company_id}, que Hibernate llena y filtra solo. */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

  @TenantId
  @Column(name = "company_id", nullable = false, updatable = false)
  private Long companyId;
}
