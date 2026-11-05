package com.ttuk.category;

import com.ttuk.category.dto.CategoryResponse;
import com.ttuk.global.auth.Login;
import com.ttuk.global.auth.LoginMember;
import com.ttuk.global.auth.RequireRole;
import com.ttuk.global.response.ApiResponse;
import com.ttuk.member.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(@Login LoginMember loginMember) {
        List<CategoryResponse> response = categoryService.getCategories(loginMember.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @RequireRole(MemberRole.BUYER)
    @PostMapping("/{categoryId}/subscriptions")
    public ResponseEntity<Void> subscribe(@Login LoginMember loginMember,
                                          @PathVariable Long categoryId){
        categoryService.subscribe(loginMember.memberId(), categoryId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}