package com.ecommerce.inventory.adapter.in.rest;

import com.ecommerce.inventory.adapter.in.rest.dto.ProductDtos.CreateProductRequest;
import com.ecommerce.inventory.adapter.in.rest.dto.ProductDtos.ProductResponse;
import com.ecommerce.inventory.adapter.in.rest.dto.ProductDtos.ReplenishRequest;
import com.ecommerce.inventory.application.service.ProductAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Stock seeding and inspection (admin/demo API)")
public class ProductController {

    private final ProductAdminService admin;

    public ProductController(ProductAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    @Operation(summary = "Create a product with initial stock")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = ProductResponse.from(
                admin.create(request.productId(), request.name(), request.initialStock()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.productId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/replenish")
    @Operation(summary = "Add stock to an existing product")
    public ProductResponse replenish(@PathVariable String id,
                                     @Valid @RequestBody ReplenishRequest request) {
        return ProductResponse.from(admin.replenish(id, request.quantity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product with available and reserved quantities")
    public ProductResponse get(@PathVariable String id) {
        return ProductResponse.from(admin.get(id));
    }

    @GetMapping
    @Operation(summary = "List all products")
    public List<ProductResponse> list() {
        return admin.list().stream().map(ProductResponse::from).toList();
    }
}
