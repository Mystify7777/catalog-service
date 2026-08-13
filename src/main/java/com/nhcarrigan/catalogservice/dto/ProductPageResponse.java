package com.nhcarrigan.catalogservice.dto;

import com.nhcarrigan.catalogservice.entity.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
        List<Product> content,
        long totalElements,
        int totalPages
) {

    public static ProductPageResponse from(Page<Product> page) {
        return new ProductPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
