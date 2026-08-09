package dev.zakalren.pickmeup.product;

import dev.zakalren.pickmeup.product.dto.ProductRequest;
import dev.zakalren.pickmeup.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<PagedModel<ProductResponse>> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            // Unbounded findAll() grows with the catalog; default sort keeps
            // the page order deterministic across identical requests
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        // PagedModel keeps the JSON shape stable; serializing PageImpl
        // directly is unsupported (its structure may change between releases)
        return ResponseEntity.ok(new PagedModel<>(productService.search(keyword, category, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
