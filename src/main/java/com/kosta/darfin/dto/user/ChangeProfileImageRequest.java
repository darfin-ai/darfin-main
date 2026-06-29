package com.kosta.darfin.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
public class ChangeProfileImageRequest {

    @NotBlank(message = "이미지 URL을 입력해주세요.")
    private String imageUrl;
}
