package com.kosta.darfin.dto.community;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
public class AnswerCreateRequest {

    @NotBlank(message = "답변 내용을 입력해주세요.")
    private String content;
}
