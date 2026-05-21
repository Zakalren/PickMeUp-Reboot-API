package dev.zakalren.pickmeup.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이내여야 합니다.")
        String name,

        @Size(max = 500, message = "이미지 URL은 500자 이내여야 합니다.")
        String imageUrl,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Integer price,

        @NotBlank(message = "카테고리는 필수입니다.")
        @Size(max = 50)
        String category
) {
}
