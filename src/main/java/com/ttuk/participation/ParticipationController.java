package com.ttuk.participation;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.dto.ParticipationCreateRequest;
import com.ttuk.participation.dto.ParticipationCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/group-purchases/{groupPurchaseId}/participants")
public class ParticipationController {

    private final ParticipationService participationService;

    @PostMapping
    @RequireRole(MemberRole.BUYER)
    public ResponseEntity<ApiResponse<ParticipationCreateResponse>> join(@PathVariable Long groupPurchaseId,
                                                                         @Valid @RequestBody ParticipationCreateRequest request,
                                                                         @Login LoginMember loginMember) {

        ParticipationCreateResponse response = participationService.join(groupPurchaseId, loginMember.memberId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));

    }


}
