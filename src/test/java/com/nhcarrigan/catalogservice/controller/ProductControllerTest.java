package com.nhcarrigan.catalogservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.dto.StockAdjustmentRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    private ProductRequest validRequest(String sku) {
        ProductRequest request = new ProductRequest();
        request.setName("Integration Test Product");
        request.setSku(sku);
        request.setCategory("Test Category");
        request.setPrice(new BigDecimal("19.99"));
        request.setStockQuantity(20);
        return request;
    }

    private Product createTestProduct(String sku, int stock) {
        Product product = new Product(
                "Bulk Test Product",
                sku,
                "Test Category",
                new BigDecimal("19.99"),
                stock,
                "Product created for bulk stock adjustment tests."
        );
        
        return productRepository.save(product);
    }

    @Test
    void createProductReturns201AndBody() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Integration Test Product")))
                .andExpect(jsonPath("$.stockQuantity", is(20)));
    }

    @Test
    void createProductWithBlankNameReturns400() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setName("");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void createProductWithNegativePriceReturns400() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setPrice(new BigDecimal("-5.00"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("price: Price must be greater than 0.00")));
    }

    @Test
    void createProductWithZeroPriceReturns400() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setPrice(new BigDecimal("0.00"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("price: Price must be greater than 0.00")));
    }

    @Test
    void getUnknownProductReturns404() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCategoriesReturnsDistinctCategories() throws Exception{
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder("Electronics", "Office Supplies", "Furniture")));
    }

    @Test
    void getEmptyCategoriesReturnsEmpty() throws Exception{
        productRepository.deleteAll();
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void deleteProductRemovesIt() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        String body = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void adjustStockRejectsDropBelowZero() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setStockQuantity(3);
        String body = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
        adjustment.setDelta(-4);

        mockMvc.perform(patch("/api/products/{id}/stock", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustment)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adjustStockAppliesPositiveDelta() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setStockQuantity(3);
        String body = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
        adjustment.setDelta(7);

        mockMvc.perform(patch("/api/products/{id}/stock", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(10)));
    }

    @Test
    void getUnknownProductReturns404WithCorrectShape() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpectAll(
                        jsonPath("$.timestamp").exists(),
                        jsonPath("$.status", is(404)),
                        jsonPath("$.error", is("Not Found")),
                        jsonPath("$.message").exists(),
                        jsonPath("$.details").exists());
    }

    @Test
    void createDescriptionReturns201AndBody() throws Exception {
        ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
        request.setDescription("This is a pilot Description for the product.");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description", is("This is a pilot Description for the product.")));
    }

@Test
void bulkAdjustStockReturnsUpdatedProducts() throws Exception {
    Product firstProduct = createTestProduct("BULK-TEST-1", 20);
    Product secondProduct = createTestProduct("BULK-TEST-2", 10);

    String request = """
            [
                {
                    "productId": %d,
                    "delta": 5
                },
                {
                    "productId": %d,
                    "delta": -3
                }
            ]
            """.formatted(firstProduct.getId(), secondProduct.getId());

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is(firstProduct.getId().intValue())))
            .andExpect(jsonPath("$[0].stockQuantity", is(25)))
            .andExpect(jsonPath("$[1].id", is(secondProduct.getId().intValue())))
            .andExpect(jsonPath("$[1].stockQuantity", is(7)));
}

@Test
void bulkAdjustStockRejectsEmptyRequest() throws Exception {
    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[]"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Validation Failed")));
}

@Test
void bulkAdjustStockRejectsMissingProductId() throws Exception {
    String request = """
            [
                {
                    "delta": 5
                }
            ]
            """;

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Validation Failed")));
}

@Test
void bulkAdjustStockRejectsMissingDelta() throws Exception {
    String request = """
            [
                {
                    "productId": 1
                }
            ]
            """;

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Validation Failed")));
}

@Test
void bulkAdjustStockReturns422ForInsufficientStock() throws Exception {
    String request = """
            [
                {
                    "productId": 1,
                    "delta": -999
                }
            ]
            """;

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error", is("Unprocessable Entity")));
}

@Test
void bulkAdjustStockReturns404ForUnknownProduct() throws Exception {
    String request = """
            [
                {
                    "productId": 999999,
                    "delta": 5
                }
            ]
            """;

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("Not Found")));
}

@Test
void bulkAdjustStockRollsBackEntireBatchWhenOneAdjustmentFails() throws Exception {
    Product firstProduct = createTestProduct("BULK-ROLLBACK-1", 20);
    Product secondProduct = createTestProduct("BULK-ROLLBACK-2", 10);

    String request = """
            [
                {
                    "productId": %d,
                    "delta": 5
                },
                {
                    "productId": %d,
                    "delta": -11
                }
            ]
            """.formatted(firstProduct.getId(), secondProduct.getId());

    mockMvc.perform(patch("/api/products/stock/bulk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error", is("Unprocessable Entity")));

    Product firstReloaded =
            productRepository.findById(firstProduct.getId()).orElseThrow();

    Product secondReloaded =
            productRepository.findById(secondProduct.getId()).orElseThrow();

    assertThat(firstReloaded.getStockQuantity(), is(20));
    assertThat(secondReloaded.getStockQuantity(), is(10));
}


    @Test
    void getProductsReturnsFirstPageWithDefaultSize() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(7)))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @Test
    void getProductsSupportsPagination() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[1].id", is(2)))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(4)));
    }

    @Test
    void getProductsReturnsSecondPage() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is(3)))
                .andExpect(jsonPath("$.content[1].id", is(4)))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(4)));
    }

    @Test
    void getProductsReturnsEmptyContentForOutOfRangePage() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", "99")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }
}
