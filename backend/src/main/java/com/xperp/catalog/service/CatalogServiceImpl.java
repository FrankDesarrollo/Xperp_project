package com.xperp.catalog.service;

import com.xperp.catalog.api.CatalogService;
import com.xperp.catalog.api.CategoryDto;
import com.xperp.catalog.api.ProductCommand;
import com.xperp.catalog.api.ProductDto;
import com.xperp.catalog.domain.Category;
import com.xperp.catalog.domain.Product;
import com.xperp.catalog.repository.CategoryRepository;
import com.xperp.catalog.repository.ProductRepository;
import com.xperp.shared.config.ResettableCache;
import com.xperp.shared.error.BusinessException;
import com.xperp.shared.persistence.Money;
import com.xperp.shared.persistence.PageResult;
import com.xperp.shared.tenancy.TenantContext;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogServiceImpl implements CatalogService, ResettableCache {

  private final CategoryRepository categories;
  private final ProductRepository products;

  /** Productos por empresa. El catálogo es pequeño y se consulta en cada línea de pedido. */
  private final Map<Long, Map<Long, ProductDto>> cache = new ConcurrentHashMap<>();

  CatalogServiceImpl(CategoryRepository categories, ProductRepository products) {
    this.categories = categories;
    this.products = products;
  }

  @Override
  public List<CategoryDto> categories() {
    return categories.findAllByOrderByCodeAsc().stream().map(CatalogServiceImpl::toDto).toList();
  }

  @Override
  public CategoryDto category(long id) {
    return categories
        .findById(id)
        .map(CatalogServiceImpl::toDto)
        .orElseThrow(() -> BusinessException.notFound("Categoría", id));
  }

  @Override
  public Optional<CategoryDto> categoryByCode(String code) {
    return categories.findByCode(code).map(CatalogServiceImpl::toDto);
  }

  @Override
  @Transactional
  public CategoryDto createCategory(String code, String name) {
    Category category = new Category();
    category.setCode(code);
    category.setName(name);
    return toDto(categories.save(category));
  }

  @Override
  public List<ProductDto> activeProducts() {
    return all().values().stream()
        .filter(ProductDto::active)
        .sorted((a, b) -> a.sku().compareTo(b.sku()))
        .toList();
  }

  @Override
  public ProductDto product(long id) {
    ProductDto dto = all().get(id);
    if (dto == null) {
      throw BusinessException.notFound("Producto", id);
    }
    return dto;
  }

  @Override
  public Optional<ProductDto> productBySku(String sku) {
    return all().values().stream().filter(p -> p.sku().equalsIgnoreCase(sku)).findFirst();
  }

  @Override
  public Map<Long, ProductDto> productsById(Collection<Long> ids) {
    Map<Long, ProductDto> all = all();
    Map<Long, ProductDto> result = new LinkedHashMap<>();
    for (Long id : ids) {
      ProductDto dto = all.get(id);
      if (dto != null) {
        result.put(id, dto);
      }
    }
    return result;
  }

  @Override
  public PageResult<ProductDto> search(String q, Long categoryId, Integer page, Integer size) {
    String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
    return PageResult.of(
        products.search(pattern, categoryId, PageResult.pageable(page, size, Sort.by("sku"))),
        CatalogServiceImpl::toDto);
  }

  @Override
  @Transactional
  public ProductDto create(ProductCommand command) {
    if (products.existsBySku(command.sku())) {
      throw BusinessException.conflict("duplicate_sku", "Ya existe un producto con ese SKU");
    }
    Product product = new Product();
    apply(product, command);
    ProductDto dto = toDto(products.save(product));
    evict();
    return dto;
  }

  @Override
  @Transactional
  public ProductDto update(long id, ProductCommand command) {
    Product product =
        products.findById(id).orElseThrow(() -> BusinessException.notFound("Producto", id));
    if (!product.getSku().equals(command.sku()) && products.existsBySku(command.sku())) {
      throw BusinessException.conflict("duplicate_sku", "Ya existe un producto con ese SKU");
    }
    apply(product, command);
    ProductDto dto = toDto(product);
    evict();
    return dto;
  }

  private void apply(Product product, ProductCommand c) {
    Category category =
        categories
            .findById(c.categoryId())
            .orElseThrow(() -> BusinessException.invalid("invalid_category", "Categoría inválida"));
    product.setSku(c.sku());
    product.setName(c.name());
    product.setCategory(category);
    product.setUnit(c.unit() == null || c.unit().isBlank() ? "und" : c.unit());
    product.setUnitsPerCase(c.unitsPerCase());
    product.setWeightKg(c.weightKg());
    product.setCost(Money.round(c.cost()));
    product.setPrice(Money.round(c.price()));
    product.setPerishable(c.perishable());
    product.setShelfLifeDays(c.shelfLifeDays());
    product.setActive(c.active());
  }

  private Map<Long, ProductDto> all() {
    long companyId = TenantContext.require();
    return cache.computeIfAbsent(
        companyId,
        k ->
            products.findAll().stream()
                .map(CatalogServiceImpl::toDto)
                .collect(
                    Collectors.toMap(
                        ProductDto::id, Function.identity(), (a, b) -> a, ConcurrentHashMap::new)));
  }

  private void evict() {
    Long companyId = TenantContext.current();
    if (companyId != null) {
      cache.remove(companyId);
    }
  }

  @Override
  public void clearCache() {
    cache.clear();
  }

  private static CategoryDto toDto(Category c) {
    return new CategoryDto(c.getId(), c.getCode(), c.getName());
  }

  private static ProductDto toDto(Product p) {
    return new ProductDto(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getCategory().getId(),
        p.getCategory().getCode(),
        p.getCategory().getName(),
        p.getUnit(),
        p.getUnitsPerCase(),
        p.getWeightKg(),
        p.getCost(),
        p.getPrice(),
        p.isPerishable(),
        p.getShelfLifeDays(),
        p.isActive());
  }
}
