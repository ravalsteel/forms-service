package com.ravalgroups.forms.shared.pagination;

import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageQuery {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageQuery() {}

    public static Pageable toPageable(
            Integer page, Integer size, List<String> sortParams, Map<String, String> allowedSortFields, String defaultSortProperty) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0) {
            throw new DomainException("INVALID_PAGE", "page must be >= 0");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new DomainException("INVALID_SIZE", "size must be between 1 and " + MAX_SIZE);
        }
        Sort sort = resolveSort(sortParams, allowedSortFields, defaultSortProperty);
        return PageRequest.of(resolvedPage, resolvedSize, sort);
    }

    public static Pageable toPageable(
            Integer page, Integer size, String sort, Map<String, String> allowedSortFields, String defaultSortProperty) {
        List<String> sorts = sort == null || sort.isBlank() ? List.of() : List.of(sort);
        return toPageable(page, size, sorts, allowedSortFields, defaultSortProperty);
    }

    public static Pageable toPageable(
            Integer page, Integer size, String sort, Set<String> allowedApiFields, String defaultApiField) {
        Map<String, String> identity = allowedApiFields.stream()
                .collect(Collectors.toMap(f -> f, f -> f, (a, b) -> a, java.util.LinkedHashMap::new));
        return toPageable(page, size, sort, identity, defaultApiField);
    }

    private static Sort resolveSort(
            List<String> sortParams, Map<String, String> allowedSortFields, String defaultSortProperty) {
        if (sortParams == null || sortParams.isEmpty()) {
            return Sort.by(Sort.Order.desc(allowedSortFields.getOrDefault(defaultSortProperty, defaultSortProperty)));
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String raw : sortParams) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",", 2);
            String apiField = parts[0].trim();
            String property = allowedSortFields.get(apiField);
            if (property == null) {
                throw new DomainException(
                        "INVALID_SORT", "Unsupported sort field: " + apiField + ". Allowed: " + allowedSortFields.keySet());
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1) {
                String dir = parts[1].trim().toLowerCase(Locale.ROOT);
                if ("desc".equals(dir)) {
                    direction = Sort.Direction.DESC;
                } else if (!"asc".equals(dir)) {
                    throw new DomainException("INVALID_SORT", "Sort direction must be asc or desc");
                }
            }
            orders.add(new Sort.Order(direction, property));
        }
        if (orders.isEmpty()) {
            return Sort.by(Sort.Order.desc(allowedSortFields.getOrDefault(defaultSortProperty, defaultSortProperty)));
        }
        return Sort.by(orders);
    }
}
