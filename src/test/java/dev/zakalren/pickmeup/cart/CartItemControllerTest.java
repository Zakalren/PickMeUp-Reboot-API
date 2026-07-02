package dev.zakalren.pickmeup.cart;

import dev.zakalren.pickmeup.cart.dto.AddCartItemRequest;
import dev.zakalren.pickmeup.cart.dto.CartItemResponse;
import dev.zakalren.pickmeup.cart.dto.UpdateCartItemRequest;
import dev.zakalren.pickmeup.cart.exception.CartItemNotFoundException;
import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.user.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartItemController.class)
@Import(SecurityConfig.class)
@DisplayName("CartItemController slice test")
public class CartItemControllerTest {

    private static final String SERVICE_NUMBER = "21-12345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartItemService cartItemService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private CartItemResponse chipsResponse(int quantity) {
        return new CartItemResponse(
                1L,
                10L,
                "Chips",
                "chips.jpg",
                1000,
                quantity,
                1000L * quantity,
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Find my cart test")
    void findMyCart_success() throws Exception {
        // given
        given(cartItemService.findByUser(SERVICE_NUMBER))
                .willReturn(List.of(chipsResponse(2)));

        // when & then
        mockMvc.perform(get("/api/cart-items")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Chips"))
                .andExpect(jsonPath("$[0].totalPrice").value(2000));
    }

    @Test
    @DisplayName("Unauthenticated cart call test")
    void findMyCart_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/cart-items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Add cart item test")
    void add_success() throws Exception {
        // given
        AddCartItemRequest request = new AddCartItemRequest(10L, 2);

        given(cartItemService.add(eq(SERVICE_NUMBER), any(AddCartItemRequest.class)))
                .willReturn(chipsResponse(2));

        // when & then
        mockMvc.perform(post("/api/cart-items")
                        .with(user(SERVICE_NUMBER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/cart-items/1"))
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    @DisplayName("Add cart item validation failed test")
    void add_validationFailed() throws Exception {
        // given: quantity가 1 미만
        AddCartItemRequest request = new AddCartItemRequest(10L, 0);

        // when & then
        mockMvc.perform(post("/api/cart-items")
                        .with(user(SERVICE_NUMBER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    @DisplayName("Update cart item quantity test")
    void update_success() throws Exception {
        // given
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);

        given(cartItemService.update(eq(SERVICE_NUMBER), eq(1L), any(UpdateCartItemRequest.class)))
                .willReturn(chipsResponse(5));

        // when & then: 경로 변수({cartItemId}) 바인딩 회귀 검증 포함
        mockMvc.perform(put("/api/cart-items/1")
                        .with(user(SERVICE_NUMBER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.totalPrice").value(5000));
    }

    @Test
    @DisplayName("Update missing cart item test")
    void update_notFound() throws Exception {
        // given
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);

        given(cartItemService.update(eq(SERVICE_NUMBER), eq(999L), any(UpdateCartItemRequest.class)))
                .willThrow(new CartItemNotFoundException(999L));

        // when & then
        mockMvc.perform(put("/api/cart-items/999")
                        .with(user(SERVICE_NUMBER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("Delete cart item test")
    void delete_success() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/cart-items/1")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isNoContent());

        verify(cartItemService).delete(SERVICE_NUMBER, 1L);
    }

    @Test
    @DisplayName("Delete missing cart item test")
    void delete_notFound() throws Exception {
        // given
        willThrow(new CartItemNotFoundException(999L))
                .given(cartItemService).delete(SERVICE_NUMBER, 999L);

        // when & then
        mockMvc.perform(delete("/api/cart-items/999")
                        .with(user(SERVICE_NUMBER).roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }
}
