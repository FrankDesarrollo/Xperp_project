package com.xperp.sales.repository;

import com.xperp.sales.domain.SalesOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

  @EntityGraph(attributePaths = "items")
  @Query("select o from SalesOrder o where o.id = :id")
  Optional<SalesOrder> findWithItems(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "items")
  @Query("select o from SalesOrder o where o.id = :id")
  Optional<SalesOrder> findForUpdate(@Param("id") Long id);

  @Query(
      """
      select o from SalesOrder o join o.customer c
      where (:status is null or o.status = :status)
        and (:warehouseId is null or o.warehouseId = :warehouseId)
        and (:q is null or lower(o.number) like :q or lower(c.name) like :q)
      """)
  Page<SalesOrder> search(
      @Param("status") String status,
      @Param("warehouseId") Long warehouseId,
      @Param("q") String q,
      Pageable pageable);
}
