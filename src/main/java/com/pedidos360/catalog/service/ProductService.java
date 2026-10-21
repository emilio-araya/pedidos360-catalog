package com.pedidos360.catalog.service;

import com.pedidos360.catalog.api.dto.ProductRequest;
import com.pedidos360.catalog.api.dto.PageResponse;
import com.pedidos360.catalog.api.dto.ProductResponse;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.domain.ReservationStatus;
import com.pedidos360.catalog.exception.ConflictException;
import com.pedidos360.catalog.exception.InvalidPageRequestException;
import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.repository.ProductRepository;
import com.pedidos360.catalog.repository.StockReservationRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;

    public ProductService(
            ProductRepository productRepository,
            StockReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(int page, int size) {
        Page<Product> products = productRepository.findAll(pageRequest(page, size));
        return PageResponse.from(products.map(this::toResponse));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidPageRequestException("El número de página no puede ser negativo");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageRequestException(
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        // Orden estable: sin ORDER BY, dos páginas pueden solaparse o saltear
        // filas, y el cliente ve un listado inconsistente.
        Sort sort = Sort.by("name").ascending().and(Sort.by("id"));
        return PageRequest.of(page, size, sort);
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
