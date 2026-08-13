package com.nhcarrigan.catalogservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Represents one stock adjustment within a bulk stock-adjustment request.
 *
 * <p>{@code delta} may be positive (restock) or negative (stock removal).
 * The service layer rejects adjustments that would result in negative stock.
 */
public record BulkStockAdjustmentRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Delta is required")
        Integer delta
){
} 
