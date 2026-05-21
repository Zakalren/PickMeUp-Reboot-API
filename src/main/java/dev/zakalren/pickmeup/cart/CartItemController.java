package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.cart.dto.AddCartItemRequest;
import dev.zakalren.pickmeup.cart.dto.CartItemResponse;
import dev.zakalren.pickmeup.cart.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/cart-items")
@RequiredArgsConstructor
public class CartItemController {

    private final CartItemService cartItemService;

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> findMyCart(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(cartItemService.findByUser(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<CartItemResponse> add(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemResponse created = cartItemService.add(
                userDetails.getUsername(),
                request
        );
        return ResponseEntity
                .created(URI.create("/api/cart-items/" + created.id()))
                .body(created);
    }

    @PutMapping("/${cartItemId}")
    public ResponseEntity<CartItemResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(
                cartItemService.update(userDetails.getUsername(), cartItemId, request)
        );
    }

    @DeleteMapping("/${cartItemId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable Long cartItemId
    ) {
        cartItemService.delete(userDetails.getUsername(), cartItemId);
        return ResponseEntity.noContent().build();
    }
}
