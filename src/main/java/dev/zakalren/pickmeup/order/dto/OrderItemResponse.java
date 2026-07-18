package dev.zakalren.pickmeup.order.dto;

import dev.zakalren.pickmeup.order.OrderItem;

// Reads only the snapshot columns — never the product association, which may
// be null after a catalog deletion
public record OrderItemResponse(
        String productName,
        Integer price,
        Integer quantity
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getProductName(),
                orderItem.getPrice(),
                orderItem.getQuantity()
        );
    }
}
