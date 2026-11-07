package com.pedidos360.catalog.service;

import com.pedidos360.catalog.api.dto.ProductSalesResponse;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.domain.ProcessedOrderEvent;
import com.pedidos360.catalog.domain.ProductSales;
import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.Item;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.OrderStatus;
import com.pedidos360.catalog.repository.ProcessedOrderEventRepository;
import com.pedidos360.catalog.repository.ProductRepository;
import com.pedidos360.catalog.repository.ProductSalesRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyección de ventas por producto. {@link #apply} es idempotente por
 * `eventId`: el registro de evento procesado y los contadores se escriben en la
 * misma transacción, así que una reentrega no cambia nada.
 */
@Service
public class SalesProjectionService {

    /** Sólo se descuenta lo que antes se contó como vendido (ORDER_ACCEPTED). */
    private static final Set<OrderStatus> CANCELLABLE_AFTER_SALE =
            EnumSet.of(OrderStatus.ACEPTADO, OrderStatus.EN_PREPARACION, OrderStatus.DESPACHADO);

    private final ProductSalesRepository salesRepository;
    private final ProcessedOrderEventRepository processedRepository;
    private final ProductRepository productRepository;

    public SalesProjectionService(
            ProductSalesRepository salesRepository,
            ProcessedOrderEventRepository processedRepository,
            ProductRepository productRepository) {
        this.salesRepository = salesRepository;
        this.processedRepository = processedRepository;
        this.productRepository = productRepository;
    }

    /** @return {@code false} si el evento ya se había aplicado. */
    @Transactional
    public boolean apply(OrderDomainEvent event) {
        if (processedRepository.existsById(event.eventId())) {
            return false;
        }
        BiConsumer<ProductSales, Integer> counter = counterFor(event);
        if (counter != null) {
            for (Item item : event.items()) {
                ProductSales sales = salesRepository.findById(item.productId())
                        .orElseGet(() -> new ProductSales(item.productId()));
                counter.accept(sales, item.quantity());
                salesRepository.save(sales);
            }
        }
        // Un PK duplicado (dos entregas simultáneas) revierte todo y el reintento
        // ve el evento ya procesado.
        processedRepository.saveAndFlush(
                new ProcessedOrderEvent(event.eventId(), event.orderId(), event.eventType().name()));
        return true;
    }

    /**
     * Catálogo completo con estadísticas: los productos sin ventas aparecen en
     * cero. Las estadísticas de productos que ya no están en el catálogo se
     * conservan para no perder historia.
     */
    @Transactional(readOnly = true)
    public List<ProductSalesResponse> findAll() {
        Map<UUID, ProductSalesResponse> byProduct = new TreeMap<>();
        for (Product product : productRepository.findAll()) {
            byProduct.put(product.getId(), zeroed(product.getId()));
        }
        for (ProductSales sales : salesRepository.findAllByOrderByProductIdAsc()) {
            byProduct.put(sales.getProductId(), toResponse(sales));
        }
        return List.copyOf(byProduct.values());
    }

    @Transactional(readOnly = true)
    public ProductSalesResponse findByProductId(UUID productId) {
        return salesRepository.findById(productId)
                .map(SalesProjectionService::toResponse)
                .or(() -> productRepository.findById(productId).map(product -> zeroed(product.getId())))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PRODUCT_SALES_NOT_FOUND", "No hay ventas registradas para el producto: " + productId));
    }

    private static BiConsumer<ProductSales, Integer> counterFor(OrderDomainEvent event) {
        return switch (event.eventType()) {
            case ORDER_ACCEPTED -> ProductSales::recordSold;
            case ORDER_DELIVERED -> ProductSales::recordDelivered;
            case ORDER_CANCELLED -> CANCELLABLE_AFTER_SALE.contains(event.previousStatus())
                    ? ProductSales::recordCancelled
                    : null;
            default -> null;
        };
    }

    private static ProductSalesResponse zeroed(UUID productId) {
        return new ProductSalesResponse(productId, 0, 0, 0, 0);
    }

    private static ProductSalesResponse toResponse(ProductSales sales) {
        return new ProductSalesResponse(
                sales.getProductId(),
                sales.getUnitsSold(),
                sales.getUnitsDelivered(),
                sales.getUnitsCancelled(),
                sales.getNetUnits());
    }
}
