package dev.zakalren.pickmeup.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("ProductRepository slice test")
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    private void seed() {
        productRepository.save(Product.create("Chips", "chips.jpg", 1000, "Snack", 50));
        productRepository.save(Product.create("Chocolate Chip Cookie", "cookie.jpg", 1500, "Snack", 30));
        productRepository.save(Product.create("Cola", "cola.jpg", 2000, "Drink", 20));
    }

    @Test
    @DisplayName("search with no filters returns everything")
    void search_noFilters() {
        // given
        seed();

        // when
        Page<Product> result = productRepository.search(null, null, PageRequest.of(0, 20));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("search by keyword matches name case-insensitively and partially")
    void search_byKeyword() {
        // given
        seed();

        // when
        Page<Product> result = productRepository.search("chip", null, PageRequest.of(0, 20));

        // then: "Chips" and "Chocolate Chip Cookie" both contain "chip" (case-insensitive), "Cola" doesn't
        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Chips", "Chocolate Chip Cookie");
    }

    @Test
    @DisplayName("search by category is an exact match")
    void search_byCategory() {
        // given
        seed();

        // when
        Page<Product> result = productRepository.search(null, "Drink", PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).extracting(Product::getName).containsExactly("Cola");
    }

    @Test
    @DisplayName("search combines keyword and category with AND")
    void search_byKeywordAndCategory() {
        // given
        seed();

        // when: keyword matches Chips/Chocolate Chip Cookie, but category narrows to Snack only — still both
        Page<Product> result = productRepository.search("chip", "Snack", PageRequest.of(0, 20));

        // then
        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Chips", "Chocolate Chip Cookie");
    }

    @Test
    @DisplayName("search with no matches returns an empty page")
    void search_noMatches() {
        // given
        seed();

        // when
        Page<Product> result = productRepository.search("nonexistent", null, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
    }
}
