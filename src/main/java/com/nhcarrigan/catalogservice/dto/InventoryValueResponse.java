package com.nhcarrigan.catalogservice.dto;

import java.math.BigDecimal;
import java.util.Map;

public record InventoryValueResponse(
        BigDecimal totalValue,
        Map<String, BigDecimal> byCategory
) {
}
