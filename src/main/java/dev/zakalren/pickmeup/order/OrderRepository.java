package dev.zakalren.pickmeup.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Snapshots make a product join unnecessary; one query loads everything.
    // DISTINCT keeps Hibernate from duplicating orders across joined item rows.
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.user.id = :userId ORDER BY o.id DESC")
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    // Owner-scoped lookup: someone else's order id yields empty → 404,
    // indistinguishable from a nonexistent id (no order-id enumeration)
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("orderId") Long orderId, @Param("userId") Long userId);

    // Atomic conditional cancel: ownership check + status guard folded into one
    // row-locked UPDATE (mirrors ProductRepository.decrementStock). 0 rows is
    // ambiguous by design (already-cancelled vs not-owned vs nonexistent) — the
    // service disambiguates 404 vs 409 via a prior findByIdAndUserIdWithItems.
    // Bulk update bypasses the persistence context; re-fetch to read fresh status.
    @Modifying
    @Query("UPDATE Order o SET o.status = 'CANCELLED' WHERE o.id = :id AND o.user.id = :userId AND o.status = 'PLACED'")
    int cancelIfPlaced(@Param("id") Long id, @Param("userId") Long userId);
}
