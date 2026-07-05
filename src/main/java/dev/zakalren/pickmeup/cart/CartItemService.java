package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.cart.dto.AddCartItemRequest;
import dev.zakalren.pickmeup.cart.dto.CartItemResponse;
import dev.zakalren.pickmeup.cart.dto.UpdateCartItemRequest;
import dev.zakalren.pickmeup.cart.exception.CartItemConflictException;
import dev.zakalren.pickmeup.cart.exception.CartItemNotFoundException;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public List<CartItemResponse> findByUser(String serviceNumber) {
        User user = userRepository.findByServiceNumber(serviceNumber)
                .orElseThrow(() -> new UserNotFoundException(serviceNumber));

        return cartItemRepository.findByUserIdWithProduct(user.getId()).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    @Transactional
    public CartItemResponse add(String serviceNumber, AddCartItemRequest request) {
        User user = userRepository.findByServiceNumber(serviceNumber)
                .orElseThrow(() -> new UserNotFoundException(serviceNumber));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        return cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .map(existing -> {
                    existing.increaseQuantity(request.quantity());
                    return CartItemResponse.from(existing);
                })
                .orElseGet(() -> {
                    CartItem cartItem = CartItem.create(user, product, request.quantity());
                    try {
                        // IDENTITY ids flush the insert here, so a concurrent
                        // add racing past the find above hits the unique
                        // (user, product) index at this point
                        CartItem saved = cartItemRepository.save(cartItem);
                        return CartItemResponse.from(saved);
                    } catch (DataIntegrityViolationException e) {
                        // The transaction is already rollback-only; retrying the
                        // increase here cannot commit, so surface a 409 and let
                        // the client retry (which then takes the increase path)
                        throw new CartItemConflictException(product.getId());
                    }
                });
    }

    @Transactional
    public CartItemResponse update(String serviceNumber, Long cartItemId, UpdateCartItemRequest request) {
        CartItem cartItem = findOwnedCartItem(serviceNumber, cartItemId);
        cartItem.updateQuantity(request.quantity());
        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public void delete(String serviceNumber, Long cartItemId) {
        CartItem cartItem = findOwnedCartItem(serviceNumber, cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private CartItem findOwnedCartItem(String serviceNumber, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        if (!cartItem.getUser().getServiceNumber().equals(serviceNumber)) {
            throw new CartItemNotFoundException(cartItemId);
        }

        return cartItem;
    }
}
