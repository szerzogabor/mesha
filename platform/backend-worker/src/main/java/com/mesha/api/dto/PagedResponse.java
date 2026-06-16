package com.mesha.api.dto;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <E, D> PagedResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return new PagedResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
        );
    }
}
