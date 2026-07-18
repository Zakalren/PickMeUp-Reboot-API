package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.order.exception.EmptyCartException;
import dev.zakalren.pickmeup.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Immutable once placed — no updatedAt and no mutating methods; cancellation
// (with restocking) is a deliberate follow-up, tracked in docs/IMPROVEMENTS.md.
@Entity
@Table(name = "orders") // ORDER is a MySQL reserved word
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Long: a sum of int prices × int quantities can exceed Integer.MAX_VALUE
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC") // Deterministic line order across H2 dev and MySQL prod
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Snapshots each cart line's product name and price so the order reads
    // the same forever, regardless of later catalog edits or deletions.
    // The empty-cart invariant lives here (architectural decision #4), not in
    // the service — OrderExceptionHandler maps it to 400 EMPTY_CART.
    public static Order place(User user, List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }
        Order order = new Order();
        order.user = user;
        long total = 0;
        for (CartItem cartItem : cartItems) {
            OrderItem item = OrderItem.from(order, cartItem);
            order.items.add(item);
            total += (long) item.getPrice() * item.getQuantity();
        }
        order.totalPrice = total;
        return order;
    }
}
