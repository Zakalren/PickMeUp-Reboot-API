package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.cart.dto.CartItemResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
}
