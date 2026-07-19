package dev.zakalren.pickmeup.order.dto;

import dev.zakalren.pickmeup.order.Order;
import dev.zakalren.pickmeup.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long totalPrice,
        LocalDateTime orderedAt,
        OrderStatus status,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }

    // Cancel endpoint: the order was just cancelled by a bulk UPDATE that
    // bypassed the persistence context, so the loaded entity's status is still
    // stale PLACED (re-reading the entity returns the cached instance). The
    // status is known here, so it's set explicitly instead of re-fetching.
    public static OrderResponse cancelled(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                OrderStatus.CANCELLED,
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
