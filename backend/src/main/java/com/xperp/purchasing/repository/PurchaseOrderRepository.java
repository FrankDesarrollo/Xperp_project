package com.xperp.purchasing.repository;

import com.xperp.purchasing.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

  Optional<PurchaseOrder> findByNumber(String number);

  Optional<PurchaseOrder> findByIdempotencyKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.id = :id")
  Optional<PurchaseOrder> findForUpdate(@Param("id") Long id);

  @Query(
      """
      select distinct o from PurchaseOrder o left join fetch o.items
      where o.status in :statuses
      """)
  List<PurchaseOrder> findOpenWithItems(@Param("statuses") Collection<String> statuses);

  List<PurchaseOrder> findByStatusAndExternalIdIsNull(String status);

  @Query(
      """
      select o from PurchaseOrder o
      where o.status in :statuses
        and (:supplierId is null or o.supplier.id = :supplierId)
        and (:warehouseId is null or o.warehouseId = :warehouseId)
      """)
  Page<PurchaseOrder> search(
      @Param("statuses") Collection<String> statuses,
      @Param("supplierId") Long supplierId,
      @Param("warehouseId") Long warehouseId,
      Pageable pageable);
}
