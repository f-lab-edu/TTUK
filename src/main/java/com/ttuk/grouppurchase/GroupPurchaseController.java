package com.ttuk.grouppurchase;

import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.global.response.PageResponse;
import com.ttuk.grouppurchase.dto.*;
import com.ttuk.member.MemberRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/group-purchases")
public class GroupPurchaseController {

    private final GroupPurchaseService groupPurchaseService;
    private final GroupPurchaseQueryService groupPurchaseQueryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole(MemberRole.SELLER)
    public ResponseEntity<ApiResponse<GroupPurchaseCreateResponse>> create(
            @Login LoginMember loginMember,
            @Valid @RequestPart("request") GroupPurchaseCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        GroupPurchaseCreateResponse response = groupPurchaseService.create(loginMember.memberId(), request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GroupPurchaseSummaryResponse>>> list(
            @Valid GroupPurchaseListRequest request ){

        PageResponse<GroupPurchaseSummaryResponse> response = groupPurchaseQueryService.findOpenList(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{groupPurchaseId}")
    public ResponseEntity<ApiResponse<GroupPurchaseDetailResponse>> detail(@PathVariable Long groupPurchaseId,
                                                                           @Login LoginMember loginMember){
        return ResponseEntity.ok(ApiResponse.success(groupPurchaseQueryService.findDetail(groupPurchaseId, loginMember.memberId())));
    }

}