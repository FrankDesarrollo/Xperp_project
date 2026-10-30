package com.xperp.catalog.web;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.catalog.api.ProductCommand;
import com.xperp.catalog.api.ProductDto;
import com.xperp.shared.contract.api.CatalogApi;
import com.xperp.shared.contract.model.Category;
import com.xperp.shared.contract.model.Product;
import com.xperp.shared.contract.model.ProductInput;
import com.xperp.shared.contract.model.ProductPage;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController implements CatalogApi {

  private final CatalogService catalog;

  CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @Override
  public ResponseEntity<List<Category>> listCategories() {
    return ResponseEntity.ok(
        catalog.categories().stream().map(CatalogController::toModel).toList());
  }

  @Override
  public ResponseEntity<ProductPage> listProducts(
      String q, Long categoryId, Integer page, Integer size) {
    PageResult<Product> result =
        catalog.search(q, categoryId, page, size).map(CatalogController::toModel);
    return ResponseEntity.ok(
        new ProductPage(result.items(), result.total(), result.page(), result.size()));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','purchasing')")
  public ResponseEntity<Product> createProduct(ProductInput input) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toModel(catalog.create(toCommand(input))));
  }

  @Override
  public ResponseEntity<Product> getProduct(Long id) {
    return ResponseEntity.ok(toModel(catalog.product(id)));
  }

  @Override
  @PreAuthorize("hasAnyRole('admin','purchasing')")
  public ResponseEntity<Product> updateProduct(Long id, ProductInput input) {
    return ResponseEntity.ok(toModel(catalog.update(id, toCommand(input))));
  }

  private static ProductCommand toCommand(ProductInput in) {
    return new ProductCommand(
        in.getSku(),
        in.getName(),
        in.getCategoryId(),
        in.getUnit(),
        in.getUnitsPerCase(),
        BigDecimal.valueOf(in.getWeightKg()),
        new BigDecimal(in.getCost()),
        new BigDecimal(in.getPrice()),
        Boolean.TRUE.equals(in.getPerishable()),
        in.getShelfLifeDays(),
        in.getIsActive() == null || in.getIsActive());
  }

  static Category toModel(CategoryDto c) {
    return new Category(c.id(), c.code(), c.name());
  }

  static Product toModel(ProductDto p) {
    return new Product(
            p.id(),
            p.sku(),
            p.name(),
            p.categoryId(),
            p.categoryCode(),
            p.unit(),
            p.unitsPerCase(),
            p.weightKg().doubleValue(),
            Money.format(p.cost()),
            Money.format(p.price()),
            p.perishable(),
            p.active())
        .shelfLifeDays(p.shelfLifeDays());
  }
}
