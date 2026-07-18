package dev.zakalren.pickmeup.order.dto;

import dev.zakalren.pickmeup.order.Order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long totalPrice,
        LocalDateTime orderedAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
