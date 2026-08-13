package com.nhcarrigan.catalogservice.service;

import com.nhcarrigan.catalogservice.dto.BulkStockAdjustmentRequest;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.exception.DuplicateSkuException;
import com.nhcarrigan.catalogservice.exception.InsufficientStockException;
import com.nhcarrigan.catalogservice.exception.ProductNotFoundException;
import com.nhcarrigan.catalogservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-style tests for the stock-adjustment business logic, backed by
 * the in-memory H2 database configured in src/test/resources/application.yml.
 */
@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;

    private Product createTestProduct(String sku, int stock) {
        ProductRequest request = new ProductRequest();
        request.setName("Test Widget");
        request.setSku(sku);
        request.setCategory("Test Category");
        request.setPrice(new BigDecimal("9.99"));
        request.setStockQuantity(stock);
        return productService.create(request);
    }

    @BeforeEach
    void setUp() {
        ProductRequest request = new ProductRequest();
        request.setName("Test Widget");
        request.setSku("TEST-SKU-" + System.nanoTime());
        request.setCategory("Test Category");
        request.setPrice(new BigDecimal("9.99"));
        request.setStockQuantity(10);
        testProduct = productService.create(request);
    }

    @Test
    void createPersistsProductWithGivenFields() {
        assertThat(testProduct.getId()).isNotNull();
        assertThat(testProduct.getName()).isEqualTo("Test Widget");
        assertThat(testProduct.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void createRejectsDuplicateSku() {
        ProductRequest duplicate = new ProductRequest();
        duplicate.setName("Another Widget");
        duplicate.setSku(testProduct.getSku());
        duplicate.setCategory("Test Category");
        duplicate.setPrice(new BigDecimal("5.00"));
        duplicate.setStockQuantity(5);

        assertThatThrownBy(() -> productService.create(duplicate))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> productService.findById(-1L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void adjustStockIncrementsQuantity() {
        Product updated = productService.adjustStock(testProduct.getId(), 5);
        assertThat(updated.getStockQuantity()).isEqualTo(15);
    }

    @Test
    void adjustStockDecrementsQuantity() {
        Product updated = productService.adjustStock(testProduct.getId(), -4);
        assertThat(updated.getStockQuantity()).isEqualTo(6);
    }

    @Test
    void adjustStockRejectsDropBelowZero() {
        assertThatThrownBy(() -> productService.adjustStock(testProduct.getId(), -11))
                .isInstanceOf(InsufficientStockException.class);

        // stock must be unchanged after the rejected operation
        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void adjustStockAllowsExactlyZero() {
        Product updated = productService.adjustStock(testProduct.getId(), -10);
        assertThat(updated.getStockQuantity()).isZero();
    }

    @Test
    void bulkAdjustStockAppliesAllAdjustments() {
        Product secondProduct = createTestProduct(
                "TEST-SKU-" + System.nanoTime(),
                20);

        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(testProduct.getId(), 5),
                new BulkStockAdjustmentRequest(secondProduct.getId(), -4)
        );

        List<Product> updated = productService.bulkAdjustStock(adjustments);

        assertThat(updated)
                .extracting(Product::getId)
                .containsExactly(testProduct.getId(), secondProduct.getId());

        assertThat(productRepository.findById(testProduct.getId()).orElseThrow()
                .getStockQuantity())
                .isEqualTo(15);

        assertThat(productRepository.findById(secondProduct.getId()).orElseThrow()
                .getStockQuantity())
                .isEqualTo(16);
    }

    @Test
    void bulkAdjustStockAllowsExactlyZero() {
        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(testProduct.getId(), -10)
        );

        List<Product> updated = productService.bulkAdjustStock(adjustments);

        assertThat(updated.get(0).getStockQuantity()).isZero();
    }

    @Test
    void bulkAdjustStockRejectsNegativeStock() {
        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(testProduct.getId(), -11)
        );

        assertThatThrownBy(() ->
                productService.bulkAdjustStock(adjustments))
                .isInstanceOf(InsufficientStockException.class);

        Product reloaded =
                productRepository.findById(testProduct.getId()).orElseThrow();

        assertThat(reloaded.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void bulkAdjustStockRollsBackEntireBatchWhenOneAdjustmentFails() {
        Product secondProduct = createTestProduct(
                "TEST-SKU-" + System.nanoTime(),
                10);

        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(testProduct.getId(), 5),
                new BulkStockAdjustmentRequest(secondProduct.getId(), -11)
        );

        assertThatThrownBy(() ->
                productService.bulkAdjustStock(adjustments))
                .isInstanceOf(InsufficientStockException.class);

        Product firstReloaded =
                productRepository.findById(testProduct.getId()).orElseThrow();

        Product secondReloaded =
                productRepository.findById(secondProduct.getId()).orElseThrow();

        assertThat(firstReloaded.getStockQuantity()).isEqualTo(10);
        assertThat(secondReloaded.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void bulkAdjustStockRejectsMissingProduct() {
        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(-1L, 5)
        );

        assertThatThrownBy(() ->
                productService.bulkAdjustStock(adjustments))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void bulkAdjustStockAppliesRepeatedAdjustmentsSequentially() {
        List<BulkStockAdjustmentRequest> adjustments = List.of(
                new BulkStockAdjustmentRequest(testProduct.getId(), 5),
                new BulkStockAdjustmentRequest(testProduct.getId(), -3)
        );

        List<Product> updated = productService.bulkAdjustStock(adjustments);

        assertThat(updated)
                .hasSize(1)
                .first()
                .extracting(Product::getStockQuantity)
                .isEqualTo(12);
    }
}
