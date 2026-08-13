package com.nhcarrigan.catalogservice.service;

import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.exception.DuplicateSkuException;
import com.nhcarrigan.catalogservice.exception.InsufficientStockException;
import com.nhcarrigan.catalogservice.exception.ProductNotFoundException;
import com.nhcarrigan.catalogservice.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing {@link Product} entities: enforces SKU
 * uniqueness, looks products up by id (raising
 * {@link com.nhcarrigan.catalogservice.exception.ProductNotFoundException}
 * when missing), and applies stock adjustments under the invariant that
 * stock can never go negative. Delegates persistence to
 * {@link ProductRepository}.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Returns a page of products from the catalog.
     *
     * @param pageable the pagination information
     * @return a page containing the requested products and pagination metadata
     */
    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    /**
     * Retrieves a single product by its id.
     *
     * @param id the product id
     * @return the matching product
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     */
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Searches for products whose name contains the given substring,
     * case-insensitive.
     *
     * @param name the substring to match against product names
     * @return matching products, or an empty list if none match
     */
    @Transactional(readOnly = true)
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Returns every category currently in use across all products
     *
     * @return the distinct category values, or an empty list if the catalog has no products.
     */
    @Transactional(readOnly = true)
    public List<String> listCategories(){
        return productRepository.listCategories();
    }

    /**
     * Creates and persists a new product.
     *
     * @param request the fields for the new product
     * @return the persisted product, including its generated id
     * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException
     *         if a product with the same SKU already exists
     */
    @Transactional
    public Product create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        Product product = new Product(
                request.getName(),
                request.getSku(),
                request.getCategory(),
                request.getPrice(),
                request.getStockQuantity(),
                request.getDescription());
        return productRepository.save(product);
    }

    /**
     * Replaces all fields of an existing product.
     *
     * @param id      the id of the product to update
     * @param request the new field values
     * @return the updated, persisted product
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException
     *         if the new SKU collides with a different existing product
     */
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product existing = findById(id);

        if (!existing.getSku().equalsIgnoreCase(request.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        existing.setName(request.getName());
        existing.setSku(request.getSku());
        existing.setCategory(request.getCategory());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setDescription(request.getDescription());
        return productRepository.save(existing);
    }

    /**
     * Deletes a product by its id.
     *
     * @param id the id of the product to delete
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given id
     */
    @Transactional
    public void delete(Long id) {
        Product existing = findById(id);
        productRepository.delete(existing);
    }

    /**
     * Applies a signed delta to a product's stock quantity. Positive deltas
     * restock, negative deltas draw down stock. The operation is rejected
     * (no partial writes) if it would take stock below zero.
     */
    @Transactional
    public Product adjustStock(Long id, int delta) {
        Product product = findById(id);
        int newQuantity = product.getStockQuantity() + delta;
        if (newQuantity < 0) {
            throw new InsufficientStockException(id, product.getStockQuantity(), delta);
        }
        product.setStockQuantity(newQuantity);
        return productRepository.save(product);
    }
}