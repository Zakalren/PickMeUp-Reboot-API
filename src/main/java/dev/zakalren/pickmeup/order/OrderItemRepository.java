package dev.zakalren.pickmeup.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Second query of the two-query pagination: loads all items for a page of
    // order ids in one statement, grouped back onto each order in the service.
    List<OrderItem> findByOrderIdIn(List<Long> orderIds);
}
