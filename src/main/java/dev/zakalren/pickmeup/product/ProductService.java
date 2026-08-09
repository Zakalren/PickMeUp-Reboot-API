package dev.zakalren.pickmeup.product;

import dev.zakalren.pickmeup.product.dto.ProductRequest;
import dev.zakalren.pickmeup.product.dto.ProductResponse;
import dev.zakalren.pickmeup.product.exception.ProductInUseException;
import dev.zakalren.pickmeup.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<ProductResponse> search(String keyword, String category, Pageable pageable) {
        return productRepository.search(normalize(keyword), normalize(category), pageable)
                .map(ProductResponse::from);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = Product.create(
                request.name(),
                request.imageUrl(),
                request.price(),
                request.category(),
                request.stock()
        );
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.update(
                request.name(),
                request.imageUrl(),
                request.price(),
                request.category(),
                request.stock()
        );
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        try {
            productRepository.deleteById(id);
            // Force the DELETE to execute now so a cart-referencing FK
            // violation surfaces here instead of at transaction commit,
            // after this method (and its try/catch) has already returned.
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProductInUseException(id);
        }
    }
}
