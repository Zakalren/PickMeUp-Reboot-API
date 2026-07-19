package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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
    public ResponseEntity<PagedModel<OrderResponse>> findMyOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            // Newest-first by default; history grows unbounded, so it's paged
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(orderService.findMyOrders(userDetails.getUsername(), pageable)));
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
