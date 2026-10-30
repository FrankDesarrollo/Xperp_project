package com.xperp.logistics.repository;

import com.xperp.logistics.domain.WarehouseTask;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<WarehouseTask, Long> {

  List<WarehouseTask> findBySalesOrderIdAndStatusIn(Long salesOrderId, Collection<String> statuses);

  @Query(
      """
      select t from WarehouseTask t
      where t.status in ('pending', 'in_progress') and t.handledByHuman = false
        and t.dueAt <= :now
      order by t.dueAt, t.id
      """)
  List<WarehouseTask> findDue(@Param("now") Instant now, Pageable pageable);

  @Query(
      """
      select t from WarehouseTask t
      where t.status in :statuses
        and (:assignedTo is null or t.assignedToUserId = :assignedTo)
        and (:type is null or t.type = :type)
        and (:warehouseId is null or t.warehouseId = :warehouseId)
      """)
  Page<WarehouseTask> search(
      @Param("statuses") Collection<String> statuses,
      @Param("assignedTo") Long assignedTo,
      @Param("type") String type,
      @Param("warehouseId") Long warehouseId,
      Pageable pageable);
}
