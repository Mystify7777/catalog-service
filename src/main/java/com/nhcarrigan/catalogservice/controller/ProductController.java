package com.nhcarrigan.catalogservice.controller;

import com.nhcarrigan.catalogservice.dto.BulkStockAdjustmentRequest;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.dto.StockAdjustmentRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.service.ProductService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * REST controller exposing CRUD and search operations for {@link Product}
 * resources. All business logic and validation is delegated to
 * {@link ProductService}; this class is only responsible for mapping HTTP
 * requests to service calls and shaping the HTTP response.
 *
 * <p>Base path: {@code /api/products}.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Returns every product in the catalog.
     *
     * @return the full list of products
     */
    @GetMapping
    public List<Product> getAll() {
        return productService.findAll();
    }

    /**
     * Searches for products whose name contains the given substring,
     * case-insensitive.
     *
     * @param name the substring to match against product names
     * @return matching products, or an empty list if none match
     */
    @GetMapping("/search")
    public List<Product> search(@RequestParam String name) {
        return productService.searchByName(name);
    }

    /**
     * Returns every category currently in use across all products
     *
     * @return the distinct category values, or an empty list if the catalog has no products.
     */
    @GetMapping("/categories")
    public List<String> getCategories() {
        return productService.listCategories();
    }

    /**
     * Retrieves a single product by its id.
     *
     * @param id the product id
     * @return the matching product
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     */
    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return productService.findById(id);
    }

    /**
     * Creates a new product.
     *
     * @param request the product fields to create; validated via
     *                 {@link Valid}
     * @return a 201 response containing the newly created product
     * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException
     *         if a product with the same SKU already exists
     */
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Replaces all fields of an existing product.
     *
     * @param id      the id of the product to update
     * @param request the new field values; validated via {@link Valid}
     * @return the updated product
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException
     *         if the new SKU collides with a different existing product
     */
    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * Deletes a product by its id.
     *
     * @param id the id of the product to delete
     * @return a 204 response with no body
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Adjusts a product's stock by a signed delta (positive to restock,
     * negative to draw down).
     *
     * @param id      the id of the product whose stock is being adjusted
     * @param request the signed delta to apply; validated via {@link Valid}
     * @return the product with its updated stock quantity
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     * @throws com.nhcarrigan.catalogservice.exception.InsufficientStockException
     *         if applying the delta would take stock below zero
     */
    @PatchMapping("/{id}/stock")
    public Product adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, request.getDelta());
    }

    /**
     * Applies multiple stock adjustments as one atomic operation.
     *
     * @param adjustments the stock adjustments to apply
     * @return the products with their updated stock quantities
     */
    @PatchMapping("/stock/bulk")
    public List<Product> bulkAdjustStock(
            @NotEmpty(message = "At least one stock adjustment is required")
            @RequestBody List<@Valid BulkStockAdjustmentRequest> adjustments) {
        return productService.bulkAdjustStock(adjustments);
    }
}