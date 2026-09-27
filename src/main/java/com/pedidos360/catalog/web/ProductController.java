package com.pedidos360.catalog.web;

import com.pedidos360.catalog.api.dto.ProductRequest;
import com.pedidos360.catalog.api.dto.ProductResponse;
import com.pedidos360.catalog.api.dto.StockUpdateRequest;
import com.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/catalog/products", "/aws/api/catalog/products"})
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            HttpServletRequest httpRequest
    ) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(
                URI.create(httpRequest.getRequestURI() + "/" + created.id())
        ).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse updateStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockUpdateRequest request) {
        return productService.updateStock(id, request.stock());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
