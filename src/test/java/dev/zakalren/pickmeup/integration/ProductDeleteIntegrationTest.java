package dev.zakalren.pickmeup.integration;

import dev.zakalren.pickmeup.cart.CartItem;
import dev.zakalren.pickmeup.cart.CartItemRepository;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.user.User;
import dev.zakalren.pickmeup.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ProductServiceTest의 delete_productInUse는 flush()가 던지는 예외를 Mockito로
// 흉내낸 것이라 try/catch 배선만 증명한다. 여기서는 실제 H2 FK 제약(CartItem.product는
// @ManyToOne(optional = false) — 진짜 NOT NULL FK)이 DataIntegrityViolationException을
// 던지는 경로 자체를 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@DisplayName("Product delete FK conflict integration test")
public class ProductDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("Deleting a product still referenced by a cart line returns 409, not 500")
    void delete_productInCart_conflict() throws Exception {
        // given: 상품 + 그 상품을 담은 장바구니 라인을 리포지토리로 직접 시드
        // (테스트 대상은 삭제 시 FK 충돌 처리이지 회원가입/카트 API 흐름이 아니므로)
        Product chips = productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack", 5));
        User user = userRepository.save(User.create(
                "21-12345678", passwordEncoder.encode("password1234"), "KIM", "ROKAF", "Private",
                LocalDate.of(2002, 11, 8), "010-1234-5678"
        ));
        cartItemRepository.save(CartItem.create(user, chips, 1));

        // 영속성 컨텍스트를 비워야 함: 방금 저장한 CartItem이 이 트랜잭션의 1차 캐시에
        // 그대로 남아 있으면, Hibernate가 flush 직전에 도는 자체 참조 무결성
        // 사전 검사(checkForTransientReferences)가 "삭제 대상 Product를 참조하는
        // managed CartItem"을 발견하고 실제 DB FK 위반보다 먼저
        // TransientPropertyValueException을 던져 버린다 — DataIntegrityViolationException이
        // 아니라서 ProductService의 catch에 걸리지 않고 500으로 샌다. 이건 이 테스트가
        // seed와 delete를 한 트랜잭션/영속성 컨텍스트에 몰아넣어 생긴 인위적 현상이고,
        // open-in-view: false인 실제 운영에서는 삭제 요청의 트랜잭션에 CartItem이
        // 로드될 일이 없어 재현되지 않는다. clear()로 그 실제 상황을 흉내낸다.
        em.flush();
        em.clear();

        // when & then: 실제 FK 제약 위반이 DataIntegrityViolationException을 던지고,
        // ProductService가 이를 잡아 500이 아닌 409로 변환해야 함
        mockMvc.perform(delete("/api/products/" + chips.getId())
                        .with(user("11-00000001").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_IN_USE"));

        // 삭제가 실제로 안 됐다는 별도 조회는 하지 않는다: FK 제약 위반 자체가 DB가
        // DELETE를 거부했다는 증거이고, 실패한 flush 이후 이 트랜잭션의 영속성
        // 컨텍스트로 추가 쿼리를 날리면(JPQL 기반 쿼리는 실행 전 auto-flush를 시도)
        // 같은 예외가 또 던져져 테스트 자체가 깨진다.
    }
}
