package com.nhcarrigan.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductImportServiceTest {

  private final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

    private final ProductImportService service = new ProductImportService(validator);

  @Test
  void validatesValidRows() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validRequests()).hasSize(1);
    assertThat(result.errors()).isEmpty();

    assertThat(result.validRequests().get(0).getName()).isEqualTo("Keyboard");
    assertThat(result.validRequests().get(0).getSku()).isEqualTo("SKU-001");
    assertThat(result.validRequests().get(0).getPrice()).isEqualByComparingTo("49.99");
    assertThat(result.validRequests().get(0).getStockQuantity()).isEqualTo(10);
  }

  @Test
  void reportsInvalidPrice() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            3, "Keyboard", "SKU-001", "Electronics", "-5.00", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validRequests()).isEmpty();
    assertThat(result.errors())
        .singleElement()
        .extracting(error -> error.row())
        .isEqualTo(3);

    assertThat(result.errors().get(0).reason())
        .contains("Price must be greater than 0.00");
  }

  @Test
  void reportsMissingRequiredField() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            4, "", "SKU-001", "Electronics", "49.99", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validRequests()).isEmpty();
    assertThat(result.errors()).singleElement();
    assertThat(result.errors().get(0).row()).isEqualTo(4);
    assertThat(result.errors().get(0).reason()).contains("Name must not be blank");
  }

  @Test
  void rejectsDuplicateSkuWithinImport() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "SKU-001", "Electronics", "19.99", "20", null));

    ProductImportService.ProductImportValidationResult result = service.validateRows(rows);

    assertThat(result.validRequests()).hasSize(1);
    assertThat(result.errors()).singleElement();
    assertThat(result.errors().get(0).row()).isEqualTo(3);
    assertThat(result.errors().get(0).reason()).contains("Duplicate SKU");
  }

  @Test
  void treatsSkuComparisonAsCaseInsensitive() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "sku-001", "Electronics", "19.99", "20", null));

    ProductImportService.ProductImportValidationResult result = service.validateRows(rows);

    assertThat(result.validRequests()).hasSize(1);
    assertThat(result.errors()).singleElement();
    assertThat(result.errors().get(0).row()).isEqualTo(3);
  }

  @Test
  void reportsInvalidNumericValues() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "not-a-price", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "SKU-002", "Electronics", "19.99", "not-a-number", null));

    ProductImportService.ProductImportValidationResult result = service.validateRows(rows);

    assertThat(result.validRequests()).isEmpty();
    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors().get(0).row()).isEqualTo(2);
    assertThat(result.errors().get(1).row()).isEqualTo(3);
  }
}
