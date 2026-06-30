package dev.zakalren.pickmeup.cart;

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
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("CartItemRepository slice test")
public class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

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
        chips = productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack"));
        pizza = productRepository.save(Product.create("Pizza", "pizza.jpg", 5000, "Food"));
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("해당 유저의 장바구니 아이템만 반환")
        void findByUserId_returnsOnlyTargetUsersItems() {
            // given
            User other = userRepository.save(User.create(
                    "20-99999999", "$pw$", "LEE", "ROKA", "Corporal",
                    LocalDate.of(2000, 1, 1), "010-9999-9999"
            ));
            cartItemRepository.save(CartItem.create(user, chips, 1));
            cartItemRepository.save(CartItem.create(user, pizza, 2));
            cartItemRepository.save(CartItem.create(other, chips, 3)); // 다른 유저 — 포함되면 안 됨

            // when
            List<CartItem> items = cartItemRepository.findByUserId(user.getId());

            // then
            assertThat(items).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByUserIdWithProduct (N+1 검증)")
    class FindByUserIdWithProduct {

        @Test
        @DisplayName("JOIN FETCH로 product 함께 로딩 — product 접근 시 추가 쿼리 없이 단일 쿼리")
        void findByUserIdWithProduct_noNPlus1() {
            // given
            cartItemRepository.save(CartItem.create(user, chips, 1));
            cartItemRepository.save(CartItem.create(user, pizza, 2));
            em.flush();
            em.clear(); // 1차 캐시 제거 — 쿼리가 실제로 실행되도록

            Statistics stats = em.getEntityManagerFactory()
                    .unwrap(SessionFactory.class)
                    .getStatistics();
            stats.clear();

            // when
            List<CartItem> items = cartItemRepository.findByUserIdWithProduct(user.getId());
            // LAZY 연관관계인 product 접근 — N+1이라면 여기서 추가 쿼리 발생
            items.forEach(item -> item.getProduct().getName());

            // then
            assertThat(items).hasSize(2);
            // JOIN FETCH 덕분에 SQL 1개만 실행됐어야 함
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProductId")
    class FindByUserIdAndProductId {

        @Test
        @DisplayName("존재하는 아이템 조회 시 반환")
        void findByUserIdAndProductId_found() {
            // given
            cartItemRepository.save(CartItem.create(user, chips, 3));

            // when
            Optional<CartItem> found = cartItemRepository.findByUserIdAndProductId(user.getId(), chips.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("없는 아이템 조회 시 empty 반환")
        void findByUserIdAndProductId_notFound() {
            // when
            Optional<CartItem> found = cartItemRepository.findByUserIdAndProductId(user.getId(), chips.getId());

            // then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndProductId")
    class ExistsByUserIdAndProductId {

        @Test
        @DisplayName("존재하면 true, 없으면 false")
        void existsByUserIdAndProductId() {
            // given
            cartItemRepository.save(CartItem.create(user, chips, 1));

            // when & then
            assertThat(cartItemRepository.existsByUserIdAndProductId(user.getId(), chips.getId())).isTrue();
            assertThat(cartItemRepository.existsByUserIdAndProductId(user.getId(), pizza.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteByUserIdAndProductId")
    class DeleteByUserIdAndProductId {

        @Test
        @DisplayName("삭제 후 조회 시 empty")
        void deleteByUserIdAndProductId_success() {
            // given
            cartItemRepository.save(CartItem.create(user, chips, 1));

            // when
            cartItemRepository.deleteByUserIdAndProductId(user.getId(), chips.getId());

            // then
            assertThat(cartItemRepository.findByUserIdAndProductId(user.getId(), chips.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Unique constraint (user + product)")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 유저+상품 조합은 중복 저장 불가")
        void duplicateUserProduct_throwsException() {
            // given
            cartItemRepository.save(CartItem.create(user, chips, 1));
            em.flush();

            // when & then
            assertThrows(Exception.class, () -> {
                cartItemRepository.save(CartItem.create(user, chips, 2));
                em.flush();
            });
        }
    }
}
