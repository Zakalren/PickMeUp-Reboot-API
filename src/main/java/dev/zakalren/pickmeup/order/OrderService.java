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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
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

    public List<OrderResponse> findMyOrders(String serviceNumber) {
        User user = findUser(serviceNumber);
        return orderRepository.findByUserIdWithItems(user.getId()).stream()
                .map(OrderResponse::from)
                .toList();
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
