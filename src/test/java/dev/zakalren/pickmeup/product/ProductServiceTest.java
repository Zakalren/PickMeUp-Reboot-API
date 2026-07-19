package dev.zakalren.pickmeup.product;

import dev.zakalren.pickmeup.product.dto.ProductRequest;
import dev.zakalren.pickmeup.product.dto.ProductResponse;
import dev.zakalren.pickmeup.product.exception.ProductInUseException;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService unit test")
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product chips() {
        Product product = Product.create("Chips", "chips.jpg", 1000, "Snack", 50);
        ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }

    @Nested
    @DisplayName("Find All")
    class FindAll {

        @Test
        @DisplayName("findAll maps entities to a response page")
        void findAll_mapsToResponsePage() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            given(productRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(chips()), pageable, 1));

            // when
            Page<ProductResponse> result = productService.findAll(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Chips");
            assertThat(result.getContent().get(0).price()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Find By Id")
    class FindById {

        @Test
        @DisplayName("findById successful test")
        void findById_success() {
            // given
            given(productRepository.findById(1L)).willReturn(Optional.of(chips()));

            // when
            ProductResponse response = productService.findById(1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Chips");
        }

        @Test
        @DisplayName("findById missing product test")
        void findById_notFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.findById(999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("create successful test")
        void create_success() {
            // given
            ProductRequest request = new ProductRequest("Chips", "chips.jpg", 1000, "Snack", 50);
            given(productRepository.save(any(Product.class))).willReturn(chips());

            // when
            ProductResponse response = productService.create(request);

            // then
            assertThat(response.name()).isEqualTo("Chips");
            verify(productRepository).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("update successful test")
        void update_success() {
            // given
            given(productRepository.findById(1L)).willReturn(Optional.of(chips()));

            ProductRequest request = new ProductRequest("Premium Chips", "premium.jpg", 1500, "Snack", 30);

            // when
            ProductResponse response = productService.update(1L, request);

            // then: 조회한 영속 엔티티를 변경 — dirty checking에 맡기고 save는 호출하지 않음
            assertThat(response.name()).isEqualTo("Premium Chips");
            assertThat(response.price()).isEqualTo(1500);
            assertThat(response.stock()).isEqualTo(30);
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("update missing product test")
        void update_notFound() {
            // given
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            ProductRequest request = new ProductRequest("Chips", "chips.jpg", 1000, "Snack", 50);

            // when & then
            assertThatThrownBy(() -> productService.update(999L, request))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("delete successful test")
        void delete_success() {
            // given
            given(productRepository.existsById(1L)).willReturn(true);

            // when
            productService.delete(1L);

            // then
            verify(productRepository).deleteById(1L);
        }

        @Test
        @DisplayName("delete missing product test")
        void delete_notFound() {
            // given
            given(productRepository.existsById(999L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> productService.delete(999L))
                    .isInstanceOf(ProductNotFoundException.class);
            verify(productRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("delete product still referenced by a cart line test")
        void delete_productInUse() {
            // given
            given(productRepository.existsById(1L)).willReturn(true);
            doThrow(new DataIntegrityViolationException("FK violation"))
                    .when(productRepository).flush();

            // when & then
            assertThatThrownBy(() -> productService.delete(1L))
                    .isInstanceOf(ProductInUseException.class);
        }
    }
}
