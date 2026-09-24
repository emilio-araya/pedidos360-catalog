package com.pedidos360.catalog.service;

import com.pedidos360.catalog.api.dto.ProductRequest;
import com.pedidos360.catalog.api.dto.ProductResponse;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.domain.ReservationStatus;
import com.pedidos360.catalog.exception.ConflictException;
import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.repository.ProductRepository;
import com.pedidos360.catalog.repository.StockReservationRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;

    public ProductService(
            ProductRepository productRepository,
            StockReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        ensureSkuAvailable(sku, null);
        Product product = new Product(
                sku,
                request.name().trim(),
                normalizeDescription(request.description()),
                request.price(),
                request.stock(),
                request.active());
        try {
            return toResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "DUPLICATE_SKU", "Ya existe un producto con el SKU " + sku);
        }
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getProductForUpdate(id);
        String sku = normalizeSku(request.sku());
        ensureSkuAvailable(sku, id);
        product.update(
                sku,
                request.name().trim(),
                normalizeDescription(request.description()),
                request.price(),
                request.stock(),
                request.active());
        try {
            return toResponse(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "DUPLICATE_SKU", "Ya existe un producto con el SKU " + sku);
        }
    }

    @Transactional
    public ProductResponse updateStock(UUID id, int stock) {
        Product product = getProductForUpdate(id);
        product.setStock(stock);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = getProductForUpdate(id);
        long activeReservations = reservationRepository.countByStatusAndItemsProductId(
                ReservationStatus.RESERVED, id);
        if (activeReservations > 0) {
            throw new ConflictException(
                    "PRODUCT_HAS_ACTIVE_RESERVATIONS",
                    "No se puede eliminar un producto con reservas de stock activas");
        }
        productRepository.delete(product);
        productRepository.flush();
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PRODUCT_NOT_FOUND", "Producto no encontrado: " + id));
    }

    private Product getProductForUpdate(UUID id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PRODUCT_NOT_FOUND", "Producto no encontrado: " + id));
    }

    private void ensureSkuAvailable(String sku, UUID currentId) {
        productRepository.findBySku(sku)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "DUPLICATE_SKU", "Ya existe un producto con el SKU " + sku);
                });
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
