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
@DisplayName("Cart -> Checkout integration test")
public class OrderCheckoutIntegrationTest {

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
        // 상품은 관리자 API 대신 리포지토리로 시드 (테스트 대상은 주문 흐름)
        chips = productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack", 5));

        UserSignupRequest signupRequest = new UserSignupRequest(
                "21-12345678",
                "password1234",
                "KIM",
                "ROKAF",
                "Private",
                LocalDate.of(2002, 11, 8),
                "010-1234-5678"
        );
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("21-12345678", "password1234");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession();
    }

    @Test
    @DisplayName("Add to cart -> checkout -> stock decremented, cart emptied, history readable")
    void checkout_fullFlow() throws Exception {
        // 1. 카트에 3개 담기
        AddCartItemRequest addRequest = new AddCartItemRequest(chips.getId(), 3);
        mockMvc.perform(post("/api/cart-items")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated());

        // 2. 주문
        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(3000))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Chips"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andReturn();

        // 3. 재고 차감 확인 — 카트 삭제가 아직 flush 전이므로 먼저 flush하고
        //    (clear만 하면 pending 삭제가 유실됨), 벌크 UPDATE가 우회한
        //    1차 캐시를 비운 뒤 다시 조회
        em.flush();
        em.clear();
        assertThat(productRepository.findById(chips.getId()).orElseThrow().getStock())
                .isEqualTo(2);

        // 4. 카트는 비워졌어야 함
        mockMvc.perform(get("/api/cart-items")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 5. 주문 이력 조회 (목록 + 상세) — 목록은 PagedModel(content + page) 포맷
        mockMvc.perform(get("/api/orders")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].totalPrice").value(3000))
                .andExpect(jsonPath("$.content[0].status").value("PLACED"));

        String location = orderResult.getResponse().getHeader("Location");
        mockMvc.perform(get(location)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].price").value(1000));
    }

    @Test
    @DisplayName("Order list is paged: size=1 returns one order per page with correct metadata")
    void orderList_paginated() throws Exception {
        // 1. 주문 2건 생성 — 체크아웃이 카트를 비우므로 담기→주문을 두 번 반복
        checkoutQuantity(2); // 첫 주문 (id 작음)
        checkoutQuantity(1); // 둘째 주문 (id 큼, 기본 정렬 id DESC라 첫 페이지)

        // 2. size=1 첫 페이지 — 최신 주문 1건 + 전체 2건 메타데이터
        mockMvc.perform(get("/api/orders")
                        .param("size", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].totalPrice").value(1000))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        // 3. 둘째 페이지 — 그 다음(오래된) 주문
        mockMvc.perform(get("/api/orders")
                        .param("size", "1")
                        .param("page", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].totalPrice").value(2000))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    private void checkoutQuantity(int quantity) throws Exception {
        AddCartItemRequest addRequest = new AddCartItemRequest(chips.getId(), quantity);
        mockMvc.perform(post("/api/cart-items")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders")
                        .session(session))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Checkout with empty cart returns 400")
    void checkout_emptyCart() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    @DisplayName("Checkout after stock dropped below cart quantity returns 409, cart intact")
    void checkout_insufficientStock() throws Exception {
        // 1. 재고 5개 중 3개를 카트에 담기
        AddCartItemRequest addRequest = new AddCartItemRequest(chips.getId(), 3);
        mockMvc.perform(post("/api/cart-items")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated());

        // 2. 담은 뒤 재고가 2개로 줄어든 상황 (재고는 예약되지 않음)
        chips.update("Chips", "chips.jpg", 1000, "Snack", 2);
        em.flush();

        // 3. 주문은 409, 카트는 그대로여야 함
        mockMvc.perform(post("/api/orders")
                        .session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(get("/api/cart-items")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
