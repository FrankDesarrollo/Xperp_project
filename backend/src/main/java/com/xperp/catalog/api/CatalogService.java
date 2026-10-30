package com.xperp.catalog.api;

import com.xperp.shared.persistence.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** API pública del catálogo. */
public interface CatalogService {

  List<CategoryDto> categories();

  CategoryDto category(long id);

  Optional<CategoryDto> categoryByCode(String code);

  CategoryDto createCategory(String code, String name);

  /** Productos activos, ordenados por SKU. Cacheado: se consulta en cada pedido. */
  List<ProductDto> activeProducts();

  ProductDto product(long id);

  Optional<ProductDto> productBySku(String sku);

  Map<Long, ProductDto> productsById(Collection<Long> ids);

  PageResult<ProductDto> search(String q, Long categoryId, Integer page, Integer size);

  ProductDto create(ProductCommand command);

  ProductDto update(long id, ProductCommand command);
}
