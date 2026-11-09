package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.domain.ProductSales;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSalesRepository extends JpaRepository<ProductSales, UUID> {

    List<ProductSales> findAllByOrderByProductIdAsc();
}
