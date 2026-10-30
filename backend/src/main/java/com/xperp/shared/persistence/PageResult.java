package com.xperp.shared.persistence;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Página de resultados: {@code { items, total, page, size }} con page desde 1. */
public record PageResult<T>(List<T> items, long total, int page, int size) {

  public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
    return new PageResult<>(
        page.getContent().stream().map(mapper).toList(),
        page.getTotalElements(),
        page.getNumber() + 1,
        page.getSize());
  }

  public <R> PageResult<R> map(Function<T, R> mapper) {
    return new PageResult<>(items.stream().map(mapper).toList(), total, page, size);
  }

  public static Pageable pageable(Integer page, Integer size, Sort sort) {
    int p = page == null || page < 1 ? 0 : page - 1;
    int s = size == null || size < 1 ? 20 : Math.min(size, 200);
    return PageRequest.of(p, s, sort);
  }

  /** Pagina una lista ya cargada en memoria. */
  public static <T> PageResult<T> slice(List<T> all, Integer page, Integer size) {
    int s = size == null || size < 1 ? 20 : Math.min(size, 200);
    int p = page == null || page < 1 ? 1 : page;
    int from = Math.min((p - 1) * s, all.size());
    int to = Math.min(from + s, all.size());
    return new PageResult<>(all.subList(from, to), all.size(), p, s);
  }
}
