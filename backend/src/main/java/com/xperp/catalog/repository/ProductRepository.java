package com.xperp.catalog.repository;

import com.xperp.catalog.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  List<Product> findByActiveTrueOrderBySkuAsc();

  Optional<Product> findBySku(String sku);

  boolean existsBySku(String sku);

  @Query(
      """
      select p from Product p
      where (:categoryId is null or p.category.id = :categoryId)
        and (:q is null or lower(p.name) like :q or lower(p.sku) like :q)
      """)
  Page<Product> search(
      @Param("q") String q, @Param("categoryId") Long categoryId, Pageable pageable);
}
