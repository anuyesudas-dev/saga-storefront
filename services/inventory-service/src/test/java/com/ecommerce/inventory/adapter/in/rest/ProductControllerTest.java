package com.ecommerce.inventory.adapter.in.rest;

import com.ecommerce.inventory.application.service.ProductAdminService;
import com.ecommerce.inventory.domain.exception.ProductAlreadyExistsException;
import com.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.ecommerce.inventory.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductAdminService admin;

    @Test
    void createReturns201WithLocationAndCorrelationHeader() throws Exception {
        when(admin.create("p1", "Keyboard", 10)).thenReturn(
                Product.create("p1", "Keyboard", 10, NOW));

        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"name\":\"Keyboard\",\"initialStock\":10}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.productId").value("p1"))
                .andExpect(jsonPath("$.availableQty").value(10))
                .andExpect(jsonPath("$.reservedQty").value(0));
    }

    @Test
    void duplicateProductReturns409() throws Exception {
        when(admin.create(anyString(), anyString(), anyInt()))
                .thenThrow(new ProductAlreadyExistsException("p1"));

        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"name\":\"Keyboard\",\"initialStock\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Product already exists"));
    }

    @Test
    void invalidSeedRequestReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"\",\"name\":\"x\",\"initialStock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.productId").exists())
                .andExpect(jsonPath("$.errors.initialStock").exists());
    }

    @Test
    void unknownProductReturns404() throws Exception {
        when(admin.get(eq("ghost"))).thenThrow(new ProductNotFoundException("ghost"));

        mvc.perform(get("/api/v1/products/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }
}
