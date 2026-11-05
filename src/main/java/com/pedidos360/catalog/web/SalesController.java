package com.pedidos360.catalog.web;

import com.pedidos360.catalog.api.dto.ProductSalesResponse;
import com.pedidos360.catalog.service.SalesProjectionService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de la proyección de ventas; restringida a `Admin` y `Operador`. */
@RestController
@RequestMapping({"/api/catalog/sales", "/aws/api/catalog/sales"})
public class SalesController {

    private final SalesProjectionService salesService;

    public SalesController(SalesProjectionService salesService) {
        this.salesService = salesService;
    }

    @GetMapping
    public List<ProductSalesResponse> findAll() {
        return salesService.findAll();
    }

    @GetMapping("/products/{productId}")
    public ProductSalesResponse findByProductId(@PathVariable UUID productId) {
        return salesService.findByProductId(productId);
    }
}
