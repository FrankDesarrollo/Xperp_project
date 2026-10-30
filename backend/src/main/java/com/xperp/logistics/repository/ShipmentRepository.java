package com.xperp.logistics.repository;

import com.xperp.logistics.domain.Shipment;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

  Optional<Shipment> findByTrackingNumber(String trackingNumber);

  boolean existsBySalesOrderId(Long salesOrderId);

  @Query(
      """
      select s from Shipment s
      where s.status in :statuses and (:carrierId is null or s.carrierId = :carrierId)
      """)
  Page<Shipment> search(
      @Param("statuses") Collection<String> statuses,
      @Param("carrierId") Long carrierId,
      Pageable pageable);
}
