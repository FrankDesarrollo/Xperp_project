package com.xperp.inventory.repository;

import com.xperp.inventory.domain.Stock;
import com.xperp.inventory.domain.StockId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, StockId> {

  /** Bloquea la fila para reservar o mover stock sin carreras entre pedidos. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stock s where s.id = :id")
  Optional<Stock> findForUpdate(@Param("id") StockId id);

  List<Stock> findByIdWarehouseId(Long warehouseId);
}
