package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.cart.dto.AddCartItemRequest;
import dev.zakalren.pickmeup.cart.dto.CartItemResponse;
import dev.zakalren.pickmeup.cart.exception.CartItemConflictException;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemService unit test")
public class CartItemServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartItemService cartItemService;

    @Nested
    @DisplayName("Find By User")
    class FindByUser {

        @Test
        @DisplayName("findByUser successful test")
        void findByUser_success() {
            // given
            User user = User.create(
                    "21-12345678",
                    "$hashedpassword$",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            // Inject user.id so Mock's stubbing on findByUserIdWithProduct(1L) matches.
            ReflectionTestUtils.setField(user, "id", 1L);
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Product chips = Product.create(
                    "Chips",
                    "chips.jpg",
                    1000,
                    "Snack"
            );
            Product pizza = Product.create(
                    "Pizza",
                    "pizza.jpg",
                    5000,
                    "Food"
            );

            CartItem cartItem1 = CartItem.create(user, chips, 1);
            CartItem cartItem2 = CartItem.create(user, pizza, 2);

            given(cartItemRepository.findByUserIdWithProduct(1L))
                    .willReturn(List.of(cartItem1, cartItem2));

            // when
            List<CartItemResponse> responses = cartItemService.findByUser("21-12345678");

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).productName()).isEqualTo("Chips");
            assertThat(responses.get(0).quantity()).isEqualTo(1);
            assertThat(responses.get(0).totalPrice()).isEqualTo(1000);

            assertThat(responses.get(1).productName()).isEqualTo("Pizza");
            assertThat(responses.get(1).quantity()).isEqualTo(2);
            assertThat(responses.get(1).totalPrice()).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("Add")
    class Add {

        @Test
        @DisplayName("add new cart item successful test")
        void add_newCartItem_successful() {
            // given
            User user = User.create(
                    "21-12345678",
                    "$hashedpassword$",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            ReflectionTestUtils.setField(user, "id", 1L);
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Product product = Product.create(
                    "Chips",
                    "chips.jpg",
                    1000,
                    "Snack"
            );
            ReflectionTestUtils.setField(product, "id", 10L);
            given(productRepository.findById(10L))
                    .willReturn(Optional.of(product));

            given(cartItemRepository.findByUserIdAndProductId(1L, 10L))
                    .willReturn(Optional.empty());

            CartItem savedItem = CartItem.create(
                    user,
                    product,
                    2
            );
            given(cartItemRepository.save(any(CartItem.class)))
                    .willReturn(savedItem);

            AddCartItemRequest request = new AddCartItemRequest(10L, 2);

            // when
            CartItemResponse response = cartItemService.add("21-12345678", request);

            // then
            assertThat(response.productName()).isEqualTo("Chips");
            assertThat(response.quantity()).isEqualTo(2);
            assertThat(response.totalPrice()).isEqualTo(2000);

            verify(cartItemRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("add existing cart item successful test")
        void add_existingCartItem_successful() {
            // given
            User user = User.create(
                    "21-12345678",
                    "$hashedpassword$",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            ReflectionTestUtils.setField(user, "id", 1L);
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Product product = Product.create(
                    "Chips",
                    "chips.jpg",
                    1000,
                    "Snack"
            );
            ReflectionTestUtils.setField(product, "id", 10L);
            given(productRepository.findById(10L))
                    .willReturn(Optional.of(product));

            CartItem existing = CartItem.create(user, product, 1);
            given(cartItemRepository.findByUserIdAndProductId(1L, 10L))
                    .willReturn(Optional.of(existing));

            AddCartItemRequest request = new AddCartItemRequest(10L, 2);

            // when
            CartItemResponse response = cartItemService.add("21-12345678", request);

            // then
            assertThat(response.quantity()).isEqualTo(3);
            assertThat(response.totalPrice()).isEqualTo(3000L);

            verify(cartItemRepository, never()).save(any(CartItem.class));
        }

        @Test
        @DisplayName("add concurrent insert race test")
        void add_concurrentInsert_conflict() {
            // given: 동시 add 레이스 — 둘 다 조회에서 빈 결과를 받고,
            // 늦은 쪽의 insert가 unique(user, product) 인덱스에 걸리는 상황
            User user = User.create(
                    "21-12345678",
                    "$hashedpassword$",
                    "KIM",
                    "ROKAF",
                    "Private",
                    LocalDate.of(2002, 11, 8),
                    "010-1234-5678"
            );
            ReflectionTestUtils.setField(user, "id", 1L);
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Product product = Product.create(
                    "Chips",
                    "chips.jpg",
                    1000,
                    "Snack"
            );
            ReflectionTestUtils.setField(product, "id", 10L);
            given(productRepository.findById(10L))
                    .willReturn(Optional.of(product));

            given(cartItemRepository.findByUserIdAndProductId(1L, 10L))
                    .willReturn(Optional.empty());
            given(cartItemRepository.save(any(CartItem.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate key"));

            AddCartItemRequest request = new AddCartItemRequest(10L, 2);

            // when & then: 인프라 예외가 도메인 예외(409 매핑)로 변환되어야 함
            assertThatThrownBy(() -> cartItemService.add("21-12345678", request))
                    .isInstanceOf(CartItemConflictException.class);
        }
    }
}
