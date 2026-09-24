package com.pedidos360.catalog.service;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.ReservationItemRequest;
import com.pedidos360.catalog.api.dto.ReservationItemResponse;
import com.pedidos360.catalog.api.dto.StockReservationResponse;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.domain.ReservationStatus;
import com.pedidos360.catalog.domain.StockReservation;
import com.pedidos360.catalog.domain.StockReservation.ReservationLine;
import com.pedidos360.catalog.domain.StockReservationItem;
import com.pedidos360.catalog.exception.BadRequestException;
import com.pedidos360.catalog.exception.ConflictException;
import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.repository.ProductRepository;
import com.pedidos360.catalog.repository.StockReservationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {

    private final StockReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public StockReservationService(
            StockReservationRepository reservationRepository,
            ProductRepository productRepository) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ReservationResult reserve(ReservationCreateRequest request) {
        List<ReservationItemRequest> requestedItems = validateAndSortItems(request);
        Map<UUID, Integer> requestedQuantities = toQuantityMap(requestedItems);

        StockReservation existing = reservationRepository.findByOrderIdForUpdate(request.orderId())
                .orElse(null);
        if (existing != null) {
            return replay(existing, requestedQuantities);
        }

        List<UUID> productIds = requestedItems.stream()
                .map(ReservationItemRequest::productId)
                .toList();
        List<Product> lockedProducts = productRepository.findAllByIdInForUpdate(productIds);
        if (lockedProducts.size() != productIds.size()) {
            UUID missingId = findMissingProductId(productIds, lockedProducts);
            throw new ResourceNotFoundException(
                    "PRODUCT_NOT_FOUND", "Producto no encontrado: " + missingId);
        }

        
        existing = reservationRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (existing != null) {
            return replay(existing, requestedQuantities);
        }

        Map<UUID, Product> productsById = new HashMap<>();
        for (Product product : lockedProducts) {
            productsById.put(product.getId(), product);
        }

        validateProductsAndAvailability(requestedItems, productsById);
        for (ReservationItemRequest item : requestedItems) {
            productsById.get(item.productId()).adjustStock(-item.quantity());
        }

        List<ReservationLine> lines = requestedItems.stream()
                .map(item -> new ReservationLine(item.productId(), item.quantity()))
                .toList();
        StockReservation reservation = new StockReservation(request.orderId(), lines);
        StockReservation saved = reservationRepository.saveAndFlush(reservation);
        return new ReservationResult(toResponse(saved), true);
    }

    @Transactional
    public boolean release(UUID orderId) {
        StockReservation reservation = reservationRepository.findByOrderIdForUpdate(orderId)
                .orElse(null);
        if (reservation == null || reservation.getStatus() == ReservationStatus.RELEASED) {
            return false;
        }

        List<UUID> productIds = reservation.getItems().stream()
                .map(StockReservationItem::getProductId)
                .sorted()
                .toList();
        List<Product> lockedProducts = productRepository.findAllByIdInForUpdate(productIds);
        if (lockedProducts.size() != productIds.size()) {
            throw new ConflictException(
                    "RESERVATION_RELEASE_CONFLICT",
                    "No se puede liberar la reserva porque uno de sus productos ya no existe");
        }

        Map<UUID, Product> productsById = new HashMap<>();
        for (Product product : lockedProducts) {
            productsById.put(product.getId(), product);
        }
        for (StockReservationItem item : reservation.getItems()) {
            Product product = productsById.get(item.getProductId());
            try {
                product.adjustStock(item.getQuantity());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new ConflictException(
                        "STOCK_OVERFLOW",
                        "No se puede liberar la reserva: el stock excedería el límite permitido");
            }
        }

        reservation.release();
        reservationRepository.saveAndFlush(reservation);
        return true;
    }

    private ReservationResult replay(
            StockReservation existing,
            Map<UUID, Integer> requestedQuantities) {
        Map<UUID, Integer> existingQuantities = new HashMap<>();
        for (StockReservationItem item : existing.getItems()) {
            existingQuantities.put(item.getProductId(), item.getQuantity());
        }
        if (!existingQuantities.equals(requestedQuantities)) {
            throw new ConflictException(
                    "RESERVATION_IDEMPOTENCY_CONFLICT",
                    "El orderId ya tiene una reserva con productos o cantidades diferentes");
        }
        return new ReservationResult(toResponse(existing), false);
    }

    private List<ReservationItemRequest> validateAndSortItems(ReservationCreateRequest request) {
        List<ReservationItemRequest> sorted = new ArrayList<>(request.items());
        sorted.sort(Comparator.comparing(ReservationItemRequest::productId));
        Set<UUID> uniqueIds = new HashSet<>();
        for (ReservationItemRequest item : sorted) {
            if (!uniqueIds.add(item.productId())) {
                throw new BadRequestException(
                        "DUPLICATE_RESERVATION_PRODUCT",
                        "Cada producto debe aparecer una sola vez en la reserva");
            }
        }
        return List.copyOf(sorted);
    }

    private Map<UUID, Integer> toQuantityMap(List<ReservationItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (ReservationItemRequest item : items) {
            quantities.put(item.productId(), item.quantity());
        }
        return quantities;
    }

    private void validateProductsAndAvailability(
            List<ReservationItemRequest> items,
            Map<UUID, Product> productsById) {
        for (ReservationItemRequest item : items) {
            Product product = productsById.get(item.productId());
            if (!product.isActive()) {
                throw new ConflictException(
                        "PRODUCT_INACTIVE",
                        "El producto " + item.productId() + " está inactivo");
            }
            if (product.getStock() < item.quantity()) {
                throw new ConflictException(
                        "INSUFFICIENT_STOCK",
                        "Stock insuficiente para el producto " + item.productId()
                                + ". Disponible: " + product.getStock()
                                + ", solicitado: " + item.quantity());
            }
        }
    }

    private UUID findMissingProductId(List<UUID> productIds, List<Product> products) {
        Set<UUID> found = new HashSet<>();
        products.forEach(product -> found.add(product.getId()));
        return productIds.stream().filter(id -> !found.contains(id)).findFirst().orElse(null);
    }

    private StockReservationResponse toResponse(StockReservation reservation) {
        List<ReservationItemResponse> items = reservation.getItems().stream()
                .map(item -> new ReservationItemResponse(item.getProductId(), item.getQuantity()))
                .toList();
        return new StockReservationResponse(
                reservation.getOrderId(),
                reservation.getStatus(),
                items,
                reservation.getCreatedAt(),
                reservation.getReleasedAt());
    }

    public record ReservationResult(StockReservationResponse reservation, boolean created) {
    }
}
