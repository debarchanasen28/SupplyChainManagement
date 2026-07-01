package com.supplychain.integration_hub;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByDirection(Direction direction);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusIn(List<OrderStatus> statuses);
    List<Order> findByDirectionAndStatus(Direction direction, OrderStatus status);
    List<Order> findByDirectionAndStatusIn(Direction direction, List<OrderStatus> statuses);
    List<Order> findBySystemId(SystemId systemId);
    Page<Order> findBySystemId(SystemId systemId, Pageable pageable);

    List<Order> findBySystemIdAndDirection(
        SystemId systemId,
        Direction direction
    );
    Page<Order> findBySystemIdAndDirection(
        SystemId systemId,
        Direction direction,
        Pageable pageable
    );
    List<Order> findBySystemIdAndDirectionAndStatus(
        SystemId systemId,
        Direction direction,
        OrderStatus status
    );
    Page<Order> findBySystemIdAndDirectionAndStatus(
        SystemId systemId,
        Direction direction,
        OrderStatus status,
        Pageable pageable
    );
    List<Order> findBySystemIdAndStatus(SystemId systemId, OrderStatus status);
    Page<Order> findBySystemIdAndStatusIn(
        SystemId systemId,
        List<OrderStatus> statuses,
        Pageable pageable
    );
    List<Order> findBySystemIdAndDirectionAndStatusAndBuyerResponseIsNull(
        SystemId systemId,
        Direction direction,
        OrderStatus status
    );
    long countBySystemIdAndDirectionAndBuyerResponseIsNotNull(
        SystemId systemId,
        Direction direction
    );
    long countBySystemIdAndBuyerResponseIsNotNull(SystemId systemId);

    List<Order> findBySystemIdAndDirectionAndStatusIn(  
        SystemId systemId,
        Direction direction,
        List<OrderStatus> statuses
);
    Page<Order> findBySystemIdAndDirectionAndStatusIn(
        SystemId systemId,
        Direction direction,
        List<OrderStatus> statuses,
        Pageable pageable
    );

    @Query("{ 'systemId': ?0, 'direction': ?1, '$or': ["
        + "{ 'status': { '$in': ?2 } }, "
        + "{ 'buyerResponse': 'YES', 'vendorFinalDecision': 'CONFIRMED', "
        + "  'status': { '$nin': ['CANCELLED', 'REJECTED', 'VENDOR_REJECTED', 'BUYER_REJECTED'] } } ] }")
    List<Order> findVendorShippingOrders(
        SystemId systemId,
        Direction direction,
        List<OrderStatus> statuses
    );
    @Query("{ 'systemId': ?0, 'direction': ?1, '$or': ["
        + "{ 'status': { '$in': ?2 } }, "
        + "{ 'buyerResponse': 'YES', 'vendorFinalDecision': 'CONFIRMED', "
        + "  'status': { '$nin': ['CANCELLED', 'REJECTED', 'VENDOR_REJECTED', 'BUYER_REJECTED'] } } ] }")
    Page<Order> findVendorShippingOrders(
        SystemId systemId,
        Direction direction,
        List<OrderStatus> statuses,
        Pageable pageable
    );

    // --- CPI bridge lookups (Phase 1) ---
    Order findByOrderId(String orderId);
    Order findBySystemIdAndOrderIdAndDirection(SystemId systemId, String orderId, Direction direction);
    Order findBySystemIdAndDirectionAndCorrelationId(
        SystemId systemId,
        Direction direction,
        String correlationId
    );
    @Query("{ 'correlationId': ?0, 'systemId': 'SYSTEM1' }")
    Order findByCorrelationId(String correlationId);
    Order findByCorrelationIdAndSystemId(String correlationId, SystemId systemId);
    Order findByIdempotencyKey(String idempotencyKey);

    // --- Hardening: open-PO cap ---
    long countByPoStatus(PoStatus poStatus);

    // --- Simulator: per-tenant open-PO cap ---
    long countBySystemIdAndDirectionAndStatus(SystemId systemId, Direction direction, OrderStatus status);
}
