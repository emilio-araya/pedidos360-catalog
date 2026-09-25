package com.pedidos360.catalog.web;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.StockReservationResponse;
import com.pedidos360.catalog.service.StockReservationService;
import com.pedidos360.catalog.service.StockReservationService.ReservationResult;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({
        "/api/internal/catalog/stock/reservations",
        "/aws/api/internal/catalog/stock/reservations",
        "/internal/catalog/stock/reservations"
})
public class StockReservationController {

    private final StockReservationService reservationService;

    public StockReservationController(StockReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<StockReservationResponse> reserve(
            @Valid @RequestBody ReservationCreateRequest request) {
        ReservationResult result = reservationService.reserve(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.reservation());
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> release(@PathVariable UUID orderId) {
        reservationService.release(orderId);
        return ResponseEntity.noContent().build();
    }
}
