package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.cart.CartItemRepository;
import dev.zakalren.pickmeup.order.dto.OrderResponse;
import dev.zakalren.pickmeup.order.exception.EmptyCartException;
import dev.zakalren.pickmeup.order.exception.OrderAlreadyCancelledException;
import dev.zakalren.pickmeup.order.exception.OrderNotFoundException;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService unit test")
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Product chips;
    private Product pizza;

    @BeforeEach
    void setUp() {
        user = User.create(
                "21-12345678",
                "$hashedpassword$",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        chips = Product.create("Chips", "chips.jpg", 1000, "Snack", 10);
        ReflectionTestUtils.setField(chips, "id", 10L);
        pizza = Product.create("Pizza", "pizza.jpg", 5000, "Food", 10);
        ReflectionTestUtils.setField(pizza, "id", 5L);
    }

    @Nested
    @DisplayName("Order (checkout)")
    class Checkout {

        @Test
        @DisplayName("order success test")
        void order_success() {
            // given: 카트에 chips 1개(id 10), pizza 2개(id 5) — 의도적으로 정렬 안 된 순서
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            CartItem chipsItem = CartItem.create(user, chips, 1);
            CartItem pizzaItem = CartItem.create(user, pizza, 2);
            List<CartItem> cartItems = List.of(chipsItem, pizzaItem);
            given(cartItemRepository.findByUserIdWithProduct(1L))
                    .willReturn(cartItems);

            given(productRepository.decrementStock(5L, 2)).willReturn(1);
            given(productRepository.decrementStock(10L, 1)).willReturn(1);
            given(orderRepository.save(any(Order.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResponse response = orderService.order("21-12345678");

            // then: 총액과 스냅샷
            assertThat(response.totalPrice()).isEqualTo(11000L); // 1000*1 + 5000*2
            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).productName()).isEqualTo("Chips");
            assertThat(response.items().get(0).price()).isEqualTo(1000);
            assertThat(response.items().get(1).productName()).isEqualTo("Pizza");
            assertThat(response.items().get(1).quantity()).isEqualTo(2);

            // 재고 차감은 productId 오름차순 (데드락 방지): pizza(5) → chips(10)
            InOrder decrementOrder = inOrder(productRepository);
            decrementOrder.verify(productRepository).decrementStock(5L, 2);
            decrementOrder.verify(productRepository).decrementStock(10L, 1);

            // 주문 후 카트는 비워져야 함
            verify(cartItemRepository).deleteAll(cartItems);
        }

        @Test
        @DisplayName("order with empty cart test")
        void order_emptyCart() {
            // given
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));
            given(cartItemRepository.findByUserIdWithProduct(1L))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> orderService.order("21-12345678"))
                    .isInstanceOf(EmptyCartException.class);
            verify(productRepository, never()).decrementStock(anyLong(), anyInt());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("order insufficient stock test")
        void order_insufficientStock() {
            // given: 조건부 UPDATE가 0행 — 다른 주문이 먼저 재고를 가져간 상황
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            CartItem chipsItem = CartItem.create(user, chips, 3);
            given(cartItemRepository.findByUserIdWithProduct(1L))
                    .willReturn(List.of(chipsItem));

            given(productRepository.decrementStock(10L, 3)).willReturn(0);
            // 에러 메시지용 최신 재고는 스칼라 쿼리로 조회 (1차 캐시 우회)
            given(productRepository.findStockById(10L)).willReturn(Optional.of(1));

            // when & then: 트랜잭션 롤백으로 이어져야 하므로 카트/주문은 건드리면 안 됨
            assertThatThrownBy(() -> orderService.order("21-12345678"))
                    .isInstanceOf(InsufficientStockException.class);
            verify(cartItemRepository, never()).deleteAll(anyList());
            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Find my orders")
    class FindMyOrders {

        @Test
        @DisplayName("findMyOrders success test")
        void findMyOrders_success() {
            // given: two-query — 페이지의 Order 조회 후 그 id들로 items를 IN 조회해 그룹핑
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Order order = Order.place(user, List.of(CartItem.create(user, chips, 2)));
            ReflectionTestUtils.setField(order, "id", 100L);
            Pageable pageable = PageRequest.of(0, 20);
            given(orderRepository.findByUserId(1L, pageable))
                    .willReturn(new PageImpl<>(List.of(order)));
            given(orderItemRepository.findByOrderIdIn(List.of(100L)))
                    .willReturn(order.getItems());

            // when
            Page<OrderResponse> responses = orderService.findMyOrders("21-12345678", pageable);

            // then: 반환된 페이지의 id로 items를 조회하고, 알맞은 주문에 되붙임
            verify(orderItemRepository).findByOrderIdIn(List.of(100L));
            assertThat(responses.getContent()).hasSize(1);
            assertThat(responses.getContent().get(0).id()).isEqualTo(100L);
            assertThat(responses.getContent().get(0).totalPrice()).isEqualTo(2000L);
            assertThat(responses.getContent().get(0).items()).hasSize(1);
            assertThat(responses.getContent().get(0).items().get(0).productName()).isEqualTo("Chips");
        }

        @Test
        @DisplayName("findMyOrders empty page test")
        void findMyOrders_emptyPage() {
            // given: 주문이 0건인 페이지 — findByOrderIdIn(빈 리스트) 호출이 문제 없이 빈 결과
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));
            Pageable pageable = PageRequest.of(0, 20);
            given(orderRepository.findByUserId(1L, pageable))
                    .willReturn(Page.empty(pageable));
            given(orderItemRepository.findByOrderIdIn(List.of()))
                    .willReturn(List.of());

            // when
            Page<OrderResponse> responses = orderService.findMyOrders("21-12345678", pageable);

            // then
            assertThat(responses.getContent()).isEmpty();
            verify(orderItemRepository).findByOrderIdIn(List.of());
        }

        @Test
        @DisplayName("findMyOrder not found test")
        void findMyOrder_notFound() {
            // given: 없는 주문이든 남의 주문이든 empty — 동일하게 404로 이어져야 함
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));
            given(orderRepository.findByIdAndUserIdWithItems(999L, 1L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.findMyOrder("21-12345678", 999L))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Cancel order")
    class Cancel {

        @Test
        @DisplayName("cancel success test")
        void cancel_success() {
            // given: chips 1개(id 10), pizza 2개(id 5) 주문 — 취소 시 재입고
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Order placed = Order.place(user, List.of(
                    CartItem.create(user, chips, 1),
                    CartItem.create(user, pizza, 2)
            ));
            ReflectionTestUtils.setField(placed, "id", 100L);

            given(orderRepository.findByIdAndUserIdWithItems(100L, 1L))
                    .willReturn(Optional.of(placed));
            given(orderRepository.cancelIfPlaced(100L, 1L)).willReturn(1);
            given(productRepository.incrementStock(5L, 2)).willReturn(1);
            given(productRepository.incrementStock(10L, 1)).willReturn(1);

            // when
            OrderResponse response = orderService.cancel("21-12345678", 100L);

            // then: 상태는 CANCELLED (벌크 UPDATE가 영속성 컨텍스트를 우회하므로 DTO에서 명시),
            //       재입고는 productId 오름차순 (데드락 방지): pizza(5) → chips(10)
            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(response.items()).hasSize(2);
            InOrder restockOrder = inOrder(productRepository);
            restockOrder.verify(productRepository).incrementStock(5L, 2);
            restockOrder.verify(productRepository).incrementStock(10L, 1);
        }

        @Test
        @DisplayName("cancel already-cancelled test")
        void cancel_alreadyCancelled() {
            // given: 소유/존재는 확인되지만 조건부 UPDATE가 0행 — 이미 취소된 주문
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Order order = Order.place(user, List.of(CartItem.create(user, chips, 1)));
            ReflectionTestUtils.setField(order, "id", 100L);
            given(orderRepository.findByIdAndUserIdWithItems(100L, 1L))
                    .willReturn(Optional.of(order));
            given(orderRepository.cancelIfPlaced(100L, 1L)).willReturn(0);

            // when & then: 재입고는 일어나면 안 됨 (중복 취소로 재고 이중 증가 방지)
            assertThatThrownBy(() -> orderService.cancel("21-12345678", 100L))
                    .isInstanceOf(OrderAlreadyCancelledException.class);
            verify(productRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("cancel not found or not owned test")
        void cancel_notFound() {
            // given: 없는 주문이든 남의 주문이든 empty — 404, 조건부 UPDATE까지 가지 않음
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));
            given(orderRepository.findByIdAndUserIdWithItems(999L, 1L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.cancel("21-12345678", 999L))
                    .isInstanceOf(OrderNotFoundException.class);
            verify(orderRepository, never()).cancelIfPlaced(anyLong(), anyLong());
            verify(productRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("cancel skips restock for deleted product line test")
        void cancel_skipsDeletedProduct() {
            // given: 한 라인의 product가 삭제됨(getProduct() == null, FK ON DELETE SET NULL)
            given(userRepository.findByServiceNumber("21-12345678"))
                    .willReturn(Optional.of(user));

            Order order = Order.place(user, List.of(
                    CartItem.create(user, chips, 1),
                    CartItem.create(user, pizza, 2)
            ));
            ReflectionTestUtils.setField(order, "id", 100L);
            // pizza 라인(index 1)의 product를 null로 — 스냅샷 컬럼은 그대로 남아 있음
            ReflectionTestUtils.setField(order.getItems().get(1), "product", null);

            given(orderRepository.findByIdAndUserIdWithItems(100L, 1L))
                    .willReturn(Optional.of(order));
            given(orderRepository.cancelIfPlaced(100L, 1L)).willReturn(1);
            given(productRepository.incrementStock(10L, 1)).willReturn(1);

            // when
            orderService.cancel("21-12345678", 100L);

            // then: 살아있는 chips만 재입고, 삭제된 product 라인은 건너뜀 (NPE 아님)
            verify(productRepository).incrementStock(10L, 1);
            verify(productRepository, never()).incrementStock(eq(5L), anyInt());
        }
    }
}
