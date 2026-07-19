package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.cart.CartItemRepository;
import dev.zakalren.pickmeup.order.dto.OrderResponse;
import dev.zakalren.pickmeup.order.exception.OrderAlreadyCancelledException;
import dev.zakalren.pickmeup.order.exception.OrderNotFoundException;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse order(String serviceNumber) {
        User user = findUser(serviceNumber);
        List<CartItem> cartItems = cartItemRepository.findByUserIdWithProduct(user.getId());

        // Snapshot names/prices before touching stock: the bulk decrement
        // below leaves loaded Product entities stale, so nothing may read
        // product state after it. An empty cart fails here (EmptyCartException).
        Order order = Order.place(user, cartItems);

        // Ascending product-id order gives every concurrent checkout the same
        // lock-acquisition order, so orders sharing products cannot deadlock
        List<CartItem> byProductId = cartItems.stream()
                .sorted(Comparator.comparing(cartItem -> cartItem.getProduct().getId()))
                .toList();
        for (CartItem cartItem : byProductId) {
            Long productId = cartItem.getProduct().getId();
            int updated = productRepository.decrementStock(productId, cartItem.getQuantity());
            if (updated == 0) {
                // Rolls back the earlier decrements along with the whole order
                int available = productRepository.findStockById(productId).orElse(0);
                throw new InsufficientStockException(productId, cartItem.getQuantity(), available);
            }
        }

        cartItemRepository.deleteAll(cartItems);
        return OrderResponse.from(orderRepository.save(order));
    }

    // Two-query pagination: (1) a fetch-join-free paged Order query, then
    // (2) one IN query for that page's items, grouped in memory. Fixed 2
    // statements per page regardless of page size — avoids the JOIN FETCH +
    // Pageable in-memory-paging trap while staying N+1-free.
    public Page<OrderResponse> findMyOrders(String serviceNumber, Pageable pageable) {
        User user = findUser(serviceNumber);
        Page<Order> orders = orderRepository.findByUserId(user.getId(), pageable);

        List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orders.map(order -> OrderResponse.from(order, itemsByOrderId.getOrDefault(order.getId(), List.of())));
    }

    public OrderResponse findMyOrder(String serviceNumber, Long orderId) {
        User user = findUser(serviceNumber);
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(String serviceNumber, Long orderId) {
        User user = findUser(serviceNumber);
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 0 rows = the order is owned by the caller (existence/ownership was just
        // confirmed) but no longer PLACED — i.e. already cancelled → 409.
        int updated = orderRepository.cancelIfPlaced(orderId, user.getId());
        if (updated == 0) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        // Restock in ascending product-id order, same deadlock-avoidance rule as
        // checkout. A line whose product was deleted (getProduct() == null, FK
        // ON DELETE SET NULL) has nothing to restock into — skip it.
        List<OrderItem> byProductId = order.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (OrderItem item : byProductId) {
            productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
        }

        // Build the response with the now-known CANCELLED status: the bulk
        // UPDATE bypassed the persistence context, so re-reading the entity
        // would return the stale PLACED instance from the first-level cache.
        return OrderResponse.cancelled(order);
    }

    private User findUser(String serviceNumber) {
        return userRepository.findByServiceNumber(serviceNumber)
                .orElseThrow(() -> new UserNotFoundException(serviceNumber));
    }
}
