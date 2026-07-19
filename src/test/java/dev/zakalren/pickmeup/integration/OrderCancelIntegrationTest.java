package dev.zakalren.pickmeup.integration;

import dev.zakalren.pickmeup.auth.dto.LoginRequest;
import dev.zakalren.pickmeup.cart.dto.AddCartItemRequest;
import dev.zakalren.pickmeup.product.Product;
import dev.zakalren.pickmeup.product.ProductRepository;
import dev.zakalren.pickmeup.user.dto.UserSignupRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@DisplayName("Checkout -> Cancel integration test")
public class OrderCancelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @PersistenceContext
    private EntityManager em;

    private MockHttpSession session;
    private Product chips;

    @BeforeEach
    void setUp() throws Exception {
        chips = productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack", 5));
        session = signupAndLogin("21-12345678", "KIM");
    }

    @Test
    @DisplayName("Checkout -> cancel restores stock and marks order CANCELLED")
    void cancel_fullFlow() throws Exception {
        // 1. 카트에 3개 담고 주문 — 재고 5 → 2
        String orderLocation = addToCartAndCheckout(3);
        em.flush();
        em.clear();
        assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                .isEqualTo(2);

        // 2. 취소 — 200 + status CANCELLED
        mockMvc.perform(post(orderLocation + "/cancel")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. 재고는 체크아웃 이전 값(5)으로 복원
        em.flush();
        em.clear();
        assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                .isEqualTo(5);

        // 4. 상세 조회도 CANCELLED로 보임
        mockMvc.perform(get(orderLocation)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Double cancel returns 409 and does not double-restock")
    void cancel_double() throws Exception {
        String orderLocation = addToCartAndCheckout(3);

        // 1. 첫 취소 성공
        mockMvc.perform(post(orderLocation + "/cancel")
                        .session(session))
                .andExpect(status().isOk());

        // 2. 두 번째 취소는 409
        mockMvc.perform(post(orderLocation + "/cancel")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"));

        // 3. 재고는 5로 한 번만 복원 (이중 재입고 아님)
        em.flush();
        em.clear();
        assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Cancelling another user's order returns 404")
    void cancel_otherUsersOrder() throws Exception {
        String orderLocation = addToCartAndCheckout(3);

        // 다른 사용자로 로그인해 남의 주문 취소 시도 — 404 (주문 id 열거 방지)
        MockHttpSession otherSession = signupAndLogin("20-99999999", "LEE");
        mockMvc.perform(post(orderLocation + "/cancel")
                        .session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // 원 주문의 재고는 그대로 (차감된 2) — 복원되면 안 됨
        em.flush();
        em.clear();
        assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                .isEqualTo(2);
    }

    private String addToCartAndCheckout(int quantity) throws Exception {
        AddCartItemRequest addRequest = new AddCartItemRequest(chips.getId(), quantity);
        mockMvc.perform(post("/api/cart-items")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated());

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn();
        return orderResult.getResponse().getHeader("Location");
    }

    private MockHttpSession signupAndLogin(String serviceNumber, String name) throws Exception {
        UserSignupRequest signupRequest = new UserSignupRequest(
                serviceNumber, "password1234", name, "ROKAF", "Private",
                LocalDate.of(2002, 11, 8), "010-1234-5678"
        );
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(serviceNumber, "password1234");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession();
    }
}
