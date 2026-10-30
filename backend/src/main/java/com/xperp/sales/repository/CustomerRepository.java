package com.xperp.sales.repository;

import com.xperp.sales.domain.Customer;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  List<Customer> findByActiveTrueOrderByIdAsc();

  @Query(
      """
      select c from Customer c
      where (:warehouseId is null or c.warehouseId = :warehouseId)
        and (:q is null or lower(c.name) like :q or lower(c.code) like :q or lower(c.city) like :q)
      """)
  Page<Customer> search(
      @Param("q") String q, @Param("warehouseId") Long warehouseId, Pageable pageable);
}
