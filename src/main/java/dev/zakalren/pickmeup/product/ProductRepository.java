package dev.zakalren.pickmeup.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(String category);

    boolean existsByName(String name);

    // Both filters are optional (null skips the corresponding condition) so the
    // catalog listing and keyword/category search share a single query path.
    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:category IS NULL OR p.category = :category)")
    Page<Product> search(@Param("keyword") String keyword, @Param("category") String category, Pageable pageable);

    // Atomic conditional decrement: the WHERE guard makes overselling
    // impossible under concurrency (the row lock serializes writers), and
    // 0 updated rows signals insufficient stock. Bulk update bypasses the
    // persistence context — already-loaded Product entities keep a stale
    // stock value, so callers must not read stock afterwards in the same
    // transaction.
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    // Restock mirror of decrementStock (order cancellation). No stock guard —
    // an increment can't go negative. Same bulk-update caveat: callers must not
    // read stock from a loaded entity afterwards in the same transaction.
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    // Scalar query: reads the current database value directly, unlike findById
    // which would return the stale entity from the first-level cache
    @Query("SELECT p.stock FROM Product p WHERE p.id = :id")
    Optional<Integer> findStockById(@Param("id") Long id);
}
