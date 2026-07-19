package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("OrderRepository slice test")
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Product chips;
    private Product pizza;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(
                "21-12345678", "$encodedpassword$", "KIM", "ROKAF", "Private",
                LocalDate.of(2002, 11, 8), "010-1234-5678"
        ));
        chips = productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack", 100));
        pizza = productRepository.save(Product.create("Pizza", "pizza.jpg", 5000, "Food", 100));
    }

    @Nested
    @DisplayName("decrementStock (조건부 원자 차감)")
    class DecrementStock {

        @Test
        @DisplayName("재고가 충분하면 1행 갱신, 재고 감소")
        void decrementStock_success() {
            // when
            int updated = productRepository.decrementStock(chips.getId(), 3);

            // then: 벌크 UPDATE는 1차 캐시를 우회하므로 clear 후 다시 읽어야 실제 값
            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                    .isEqualTo(97);
        }

        @Test
        @DisplayName("재고보다 많으면 0행 — 재고는 그대로 (오버셀 불가)")
        void decrementStock_insufficient() {
            // when
            int updated = productRepository.decrementStock(chips.getId(), 101);

            // then
            assertThat(updated).isEqualTo(0);
            em.clear();
            assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("재고 전량 차감(경계값)은 성공, 재고 0")
        void decrementStock_exactBoundary() {
            // when
            int updated = productRepository.decrementStock(chips.getId(), 100);

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                    .isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByUserId + findByOrderIdIn (two-query 페이지네이션, N+1 검증)")
    class Pagination {

        @Test
        @DisplayName("페이지 조회 + items IN 조회 = 정확히 2 statement — 주문 수와 무관")
        void twoQueryPagination_exactly2Statements() {
            // given: 주문 3건, 각 2 라인 — fetch join 없이 페이지 조회 후 items를 IN으로 로딩
            for (int i = 0; i < 3; i++) {
                orderRepository.save(Order.place(user, List.of(
                        CartItem.create(user, chips, 1),
                        CartItem.create(user, pizza, 2)
                )));
            }
            em.flush();
            em.clear(); // 1차 캐시 제거 — 쿼리가 실제로 실행되도록

            Statistics stats = em.getEntityManagerFactory()
                    .unwrap(SessionFactory.class)
                    .getStatistics();
            stats.clear();

            // when: (1) fetch join 없는 페이지 쿼리 — items는 lazy로 남김
            //       (2) 그 페이지의 order id들로 items를 IN 한 번에 조회
            Page<Order> orders = orderRepository.findByUserId(user.getId(), PageRequest.of(0, 20));
            List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
            List<OrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);
            // 스냅샷 컬럼만 읽는다 — product 프록시를 건드리지 않아야 함
            items.forEach(OrderItem::getProductName);

            // then: 페이지가 꽉 차지 않아 count 쿼리는 생략됨 — 페이지 쿼리 1 + IN 쿼리 1 = 2
            assertThat(orders.getContent()).hasSize(3);
            assertThat(items).hasSize(6);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("cancelIfPlaced (조건부 원자 취소)")
    class CancelIfPlaced {

        @Test
        @DisplayName("PLACED이고 소유자가 맞으면 1행 갱신, 상태 CANCELLED")
        void cancelIfPlaced_success() {
            // given
            Order order = orderRepository.save(
                    Order.place(user, List.of(CartItem.create(user, chips, 1))));
            em.flush();

            // when
            int updated = orderRepository.cancelIfPlaced(order.getId(), user.getId());

            // then: 벌크 UPDATE는 1차 캐시를 우회하므로 clear 후 다시 읽어야 실제 값
            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 CANCELLED면 0행 — 상태 유지 (중복 취소 불가)")
        void cancelIfPlaced_alreadyCancelled() {
            // given
            Order order = orderRepository.save(
                    Order.place(user, List.of(CartItem.create(user, chips, 1))));
            em.flush();
            orderRepository.cancelIfPlaced(order.getId(), user.getId());
            em.clear();

            // when: 두 번째 취소
            int updated = orderRepository.cancelIfPlaced(order.getId(), user.getId());

            // then
            assertThat(updated).isEqualTo(0);
        }

        @Test
        @DisplayName("소유자가 아니면 0행 — 상태 유지")
        void cancelIfPlaced_wrongOwner() {
            // given
            User other = userRepository.save(User.create(
                    "20-99999999", "$pw$", "LEE", "ROKA", "Corporal",
                    LocalDate.of(2000, 1, 1), "010-9999-9999"
            ));
            Order order = orderRepository.save(
                    Order.place(user, List.of(CartItem.create(user, chips, 1))));
            em.flush();

            // when
            int updated = orderRepository.cancelIfPlaced(order.getId(), other.getId());

            // then
            assertThat(updated).isEqualTo(0);
            em.clear();
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PLACED);
        }

        @Test
        @DisplayName("없는 주문 id면 0행")
        void cancelIfPlaced_nonexistent() {
            // when
            int updated = orderRepository.cancelIfPlaced(999_999L, user.getId());

            // then
            assertThat(updated).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("incrementStock (조건부 원자 재입고)")
    class IncrementStock {

        @Test
        @DisplayName("재고를 정확히 더함, 1행 갱신")
        void incrementStock_success() {
            // when
            int updated = productRepository.incrementStock(chips.getId(), 5);

            // then: 벌크 UPDATE는 1차 캐시를 우회하므로 clear 후 다시 읽어야 실제 값
            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                    .isEqualTo(105);
        }

        @Test
        @DisplayName("없는 상품 id면 0행 — 갱신 없음")
        void incrementStock_nonexistent() {
            // when
            int updated = productRepository.incrementStock(999_999L, 5);

            // then
            assertThat(updated).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findByIdAndUserIdWithItems (소유자 스코프)")
    class FindByIdAndUserIdWithItems {

        @Test
        @DisplayName("본인 주문은 조회되고 타인 주문은 empty — 404와 동일하게 처리 가능")
        void ownerScoping() {
            // given
            User other = userRepository.save(User.create(
                    "20-99999999", "$pw$", "LEE", "ROKA", "Corporal",
                    LocalDate.of(2000, 1, 1), "010-9999-9999"
            ));
            Order mine = orderRepository.save(
                    Order.place(user, List.of(CartItem.create(user, chips, 1))));
            em.flush();

            // when
            Optional<Order> foundAsOwner =
                    orderRepository.findByIdAndUserIdWithItems(mine.getId(), user.getId());
            Optional<Order> foundAsStranger =
                    orderRepository.findByIdAndUserIdWithItems(mine.getId(), other.getId());

            // then
            assertThat(foundAsOwner).isPresent();
            assertThat(foundAsStranger).isEmpty();
        }
    }
}
