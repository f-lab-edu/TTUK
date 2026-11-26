package com.ttuk.participation;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.global.response.PageResponse;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.dto.MyParticipationListRequest;
import com.ttuk.participation.dto.MyParticipationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/participations")
public class MyParticipationController {

    private final ParticipationQueryService participationQueryService;

    @GetMapping
    @RequireRole(MemberRole.BUYER)
    public ResponseEntity<ApiResponse<PageResponse<MyParticipationResponse>>> list(@Login LoginMember loginMember,
                                                                                   @Valid MyParticipationListRequest request) {

        PageResponse<MyParticipationResponse> response =
                participationQueryService.findMyList(loginMember.memberId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));

    }

}
