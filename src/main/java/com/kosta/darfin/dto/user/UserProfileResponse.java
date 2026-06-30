package com.kosta.darfin.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private String email;
    private String nickname;
    private String name;
    private String profileImage;
    private String provider;
    private String subscriptionLevel;
}
