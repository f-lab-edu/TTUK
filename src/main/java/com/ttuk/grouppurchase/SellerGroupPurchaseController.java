package com.ttuk.grouppurchase;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.global.response.PageResponse;
import com.ttuk.grouppurchase.dto.SellerGroupPurchaseDetailResponse;
import com.ttuk.grouppurchase.dto.SellerGroupPurchaseListRequest;
import com.ttuk.grouppurchase.dto.SellerGroupPurchaseResponse;
import com.ttuk.member.MemberRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/group-purchases")
public class SellerGroupPurchaseController {

    private final GroupPurchaseQueryService groupPurchaseQueryService;

    @GetMapping
    @RequireRole(MemberRole.SELLER)
    public ResponseEntity<ApiResponse<PageResponse<SellerGroupPurchaseResponse>>> list(@Login LoginMember loginMember,
                                                                                       @Valid SellerGroupPurchaseListRequest request) {

        PageResponse<SellerGroupPurchaseResponse> response =
                groupPurchaseQueryService.findSellerList(loginMember.memberId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));

    }

    @GetMapping("/{groupPurchaseId}")
    @RequireRole(MemberRole.SELLER)
    public ResponseEntity<ApiResponse<SellerGroupPurchaseDetailResponse>> detail(@PathVariable Long groupPurchaseId,
                                                                                 @Login LoginMember loginMember) {

        SellerGroupPurchaseDetailResponse response = groupPurchaseQueryService.findSellerDetail(groupPurchaseId, loginMember.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));

    }

}