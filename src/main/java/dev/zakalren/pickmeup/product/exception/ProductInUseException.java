package dev.zakalren.pickmeup.product.exception;

public class ProductInUseException extends RuntimeException {
    public ProductInUseException(Long id) {
        super("장바구니에 담긴 상품은 삭제할 수 없습니다. id=" + id);
    }
}
