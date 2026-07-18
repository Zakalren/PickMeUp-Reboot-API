package dev.zakalren.pickmeup.order;

import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.order.dto.OrderItemResponse;
import dev.zakalren.pickmeup.order.dto.OrderResponse;
import dev.zakalren.pickmeup.order.exception.EmptyCartException;
import dev.zakalren.pickmeup.order.exception.OrderNotFoundException;
import dev.zakalren.pickmeup.product.exception.InsufficientStockException;
import dev.zakalren.pickmeup.user.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderController slice test")
public class OrderControllerTest {

    private static final String SERVICE_NUMBER = "21-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private OrderResponse orderResponse() {
        return new OrderResponse(
                1L,
                11000,
                LocalDateTime.now(),
                List.of(
                        new OrderItemResponse("Chips", 1000, 1),
                        new OrderItemResponse("Pizza", 5000, 2)
                )
        );
    }

    @Test
    @DisplayName("Order (checkout) test")
    void order_success() throws Exception {
        // given
        given(orderService.order(SERVICE_NUMBER))
                .willReturn(orderResponse());

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/1"))
                .andExpect(jsonPath("$.totalPrice").value(11000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Chips"));
    }

    @Test
    @DisplayName("Unauthenticated order test")
    void order_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Order with empty cart test")
    void order_emptyCart() throws Exception {
        // given: 빈 카트는 400 — 재시도로 해소될 수 있는 상태 충돌(409)이 아님
        given(orderService.order(SERVICE_NUMBER))
                .willThrow(new EmptyCartException());

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    @DisplayName("Order insufficient stock test")
    void order_insufficientStock() throws Exception {
        // given: 결제 시점에 다른 주문이 재고를 선점한 상황
        given(orderService.order(SERVICE_NUMBER))
                .willThrow(new InsufficientStockException(10L, 3, 1));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("Find my orders test")
    void findMyOrders_success() throws Exception {
        // given
        given(orderService.findMyOrders(SERVICE_NUMBER))
                .willReturn(List.of(orderResponse()));

        // when & then
        mockMvc.perform(get("/api/orders")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalPrice").value(11000));
    }

    @Test
    @DisplayName("Find my order detail test")
    void findMyOrder_success() throws Exception {
        // given
        given(orderService.findMyOrder(SERVICE_NUMBER, 1L))
                .willReturn(orderResponse());

        // when & then
        mockMvc.perform(get("/api/orders/1")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.items[1].quantity").value(2));
    }

    @Test
    @DisplayName("Find missing order test")
    void findMyOrder_notFound() throws Exception {
        // given: 남의 주문 id도 동일하게 404 (주문 id 열거 방지)
        given(orderService.findMyOrder(SERVICE_NUMBER, 999L))
                .willThrow(new OrderNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/orders/999")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
