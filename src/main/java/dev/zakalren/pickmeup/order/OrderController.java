package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Checkout: turns the caller's entire cart into an order
    @PostMapping
    public ResponseEntity<OrderResponse> order(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        OrderResponse created = orderService.order(userDetails.getUsername());
        return ResponseEntity
                .created(URI.create("/api/orders/" + created.id()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> findMyOrders(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(orderService.findMyOrders(userDetails.getUsername()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> findMyOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.findMyOrder(userDetails.getUsername(), orderId));
    }

    // Cancel (not DELETE): the order isn't removed, it transitions to CANCELLED
    // and restocks each line. Returns the updated resource so the client needn't
    // re-GET. 404 if not found/not owned, 409 if already cancelled.
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.cancel(userDetails.getUsername(), orderId));
    }
}
