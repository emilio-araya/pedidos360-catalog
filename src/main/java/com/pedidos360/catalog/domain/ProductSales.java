package com.pedidos360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Proyección de ventas por producto, alimentada por los eventos de pedido de
 * Kafka. No referencia a {@link Product}: el evento puede llegar antes que el
 * producto o después de borrarlo, y la estadística sigue siendo válida.
 */
@Entity
@Table(name = "product_sales")
public class ProductSales {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "product_id", nullable = false, updatable = false, length = 36)
    private UUID productId;

    @Column(name = "units_sold", nullable = false)
    private long unitsSold;

    @Column(name = "units_delivered", nullable = false)
    private long unitsDelivered;

    @Column(name = "units_cancelled", nullable = false)
    private long unitsCancelled;

    // Wrapper a propósito: null indica entidad nueva y Spring Data hace persist,
    // no merge. Dos consumidores sobre el mismo producto chocan por versión y el
    // error handler de Kafka reintenta el evento.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductSales() {
    }

    public ProductSales(UUID productId) {
        this.productId = productId;
    }

    public void recordSold(int quantity) {
        unitsSold += quantity;
    }

    public void recordDelivered(int quantity) {
        unitsDelivered += quantity;
    }

    public void recordCancelled(int quantity) {
        unitsCancelled += quantity;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getProductId() {
        return productId;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public long getUnitsDelivered() {
        return unitsDelivered;
    }

    public long getUnitsCancelled() {
        return unitsCancelled;
    }

    public long getNetUnits() {
        return unitsSold - unitsCancelled;
    }
}
