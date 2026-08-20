package com.nhcarrigan.catalogservice.service;

import com.nhcarrigan.catalogservice.dto.ProductImportError;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductImportService {

    private final Validator validator;

    public ProductImportService(Validator validator) {
      this.validator = validator;
    }

  /**
   * Validates the product rows in a parsed CSV import.
   *
   * <p>This method deliberately does not persist products. Persistence is handled separately so
   * that parsing, validation, and persistence remain independently testable.
   */
  public ProductImportValidationResult validateRows(List<ProductCsvParser.ParsedProductRow> rows) {
    List<ProductImportError> errors = new ArrayList<>();
    List<ProductRequest> validRequests = new ArrayList<>();
    Set<String> seenSkus = new HashSet<>();

    for (ProductCsvParser.ParsedProductRow row : rows) {
      ProductRequest request = toProductRequest(row);

      List<String> rowErrors = validateRequest(request);

      String normalizedSku = normalizeSku(request.getSku());
      if (rowErrors.isEmpty()
          && normalizedSku != null
          && !seenSkus.add(normalizedSku)) {
        rowErrors.add("Duplicate SKU in import: " + request.getSku());
      }

      if (rowErrors.isEmpty()) {
        validRequests.add(request);
      } else {
        errors.add(new ProductImportError(row.rowNumber(), String.join("; ", rowErrors)));
      }
    }

    return new ProductImportValidationResult(validRequests, errors);
  }

  private ProductRequest toProductRequest(ProductCsvParser.ParsedProductRow row) {
    ProductRequest request = new ProductRequest();
    request.setName(row.name());
    request.setSku(row.sku());
    request.setCategory(row.category());
    request.setPrice(parsePrice(row.price()));
    request.setStockQuantity(parseStockQuantity(row.stockQuantity()));
    request.setDescription(row.description());
    return request;
  }

  private List<String> validateRequest(ProductRequest request) {
    List<String> errors = new ArrayList<>();

    if (request.getPrice() == null) {
      errors.add("Price is required");
    }

    if (request.getStockQuantity() == null) {
      errors.add("Stock quantity is required");
    }

    Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);

    violations.stream()
        .map(ConstraintViolation::getMessage)
        .filter(message -> !errors.contains(message))
        .forEach(errors::add);

    return errors;
  }

  private BigDecimal parsePrice(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Integer parseStockQuantity(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String normalizeSku(String sku) {
    if (sku == null || sku.isBlank()) {
      return null;
    }

    return sku.trim().toLowerCase();
  }

  public record ProductImportValidationResult(
      List<ProductRequest> validRequests, List<ProductImportError> errors) {}
}
