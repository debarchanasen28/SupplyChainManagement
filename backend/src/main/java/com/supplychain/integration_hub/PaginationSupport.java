package com.supplychain.integration_hub;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PaginationSupport {

    private PaginationSupport() {
    }

    public static <T> PagedResponse<T> page(List<T> source, Pageable pageable) {
        List<T> sorted = new ArrayList<>(source == null ? List.of() : source);
        Comparator<T> comparator = comparator(pageable.getSort());
        if (comparator != null) sorted.sort(comparator);

        int start = Math.toIntExact(Math.min(pageable.getOffset(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return PagedResponse.from(new PageImpl<>(sorted.subList(start, end), pageable, sorted.size()));
    }

    private static <T> Comparator<T> comparator(Sort sort) {
        Comparator<T> combined = null;
        for (Sort.Order order : sort) {
            Comparator<Object> values = PaginationSupport::compareValues;
            if (order.isDescending()) values = values.reversed();
            Comparator<T> next = Comparator.comparing(
                    value -> property(value, order.getProperty()),
                    Comparator.nullsLast(values));
            combined = combined == null ? next : combined.thenComparing(next);
        }
        return combined;
    }

    private static Object property(Object value, String property) {
        if (value == null || property == null || property.isBlank()) return null;
        try {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
            return wrapper.isReadableProperty(property) ? wrapper.getPropertyValue(property) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object left, Object right) {
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }
}
