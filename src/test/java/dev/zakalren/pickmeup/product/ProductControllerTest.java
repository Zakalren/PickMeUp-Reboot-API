package dev.zakalren.pickmeup.product;

import dev.zakalren.pickmeup.config.SecurityConfig;
import dev.zakalren.pickmeup.product.dto.ProductRequest;
import dev.zakalren.pickmeup.product.dto.ProductResponse;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductController slice test")
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final ProductResponse chips = new ProductResponse(
            1L, "Chips", "chips.jpg", 1000, "Snack",
            LocalDateTime.now(), LocalDateTime.now()
    );

    @Test
    @DisplayName("Find all products without login test")
    void findAll_public() throws Exception {
        // given
        given(productService.findAll()).willReturn(List.of(chips));

        // when & then: 상품 조회는 비로그인도 허용
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Chips"));
    }

    @Test
    @DisplayName("Find missing product test")
    void findById_notFound() throws Exception {
        // given
        given(productService.findById(999L)).willThrow(new ProductNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Path variable type mismatch test")
    void findById_typeMismatch() throws Exception {
        // when & then: Long 변환 실패(/api/products/abc)도 통일된 ErrorResponse 포맷으로 400
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("Create product as admin test")
    void create_asAdmin() throws Exception {
        // given
        ProductRequest request = new ProductRequest("Chips", "chips.jpg", 1000, "Snack");

        given(productService.create(any(ProductRequest.class))).willReturn(chips);

        // when & then
        mockMvc.perform(post("/api/products")
                        .with(user("11-00000001").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/1"))
                .andExpect(jsonPath("$.name").value("Chips"));
    }

    @Test
    @DisplayName("Create product as plain user test")
    void create_asUser_forbidden() throws Exception {
        // given
        ProductRequest request = new ProductRequest("Chips", "chips.jpg", 1000, "Snack");

        // when & then: 일반 사용자는 상품 관리 불가
        mockMvc.perform(post("/api/products")
                        .with(user("21-12345678").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Create product without login test")
    void create_unauthenticated() throws Exception {
        // given
        ProductRequest request = new ProductRequest("Chips", "chips.jpg", 1000, "Snack");

        // when & then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Delete product as plain user test")
    void delete_asUser_forbidden() throws Exception {
        mockMvc.perform(delete("/api/products/1")
                        .with(user("21-12345678").roles("USER")))
                .andExpect(status().isForbidden());
    }
}