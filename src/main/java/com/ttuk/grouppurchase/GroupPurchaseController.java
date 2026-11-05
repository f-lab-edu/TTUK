package com.ttuk.grouppurchase;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateRequest;
import com.ttuk.grouppurchase.dto.GroupPurchaseCreateResponse;
import com.ttuk.member.MemberRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/group-purchases")
public class GroupPurchaseController {

    private final GroupPurchaseService groupPurchaseService;

    @PostMapping
    @RequireRole(MemberRole.SELLER)
    public ResponseEntity<ApiResponse<GroupPurchaseCreateResponse>> create(@Login LoginMember loginMember,
                                                                           @Valid @RequestBody GroupPurchaseCreateRequest request) {

        GroupPurchaseCreateResponse response = groupPurchaseService.create(loginMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

}
