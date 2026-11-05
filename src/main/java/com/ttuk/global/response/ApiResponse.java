package com.ttuk.global.response;

public record ApiResponse <T> (T data, ErrorBody error) {

    public static <T> ApiResponse <T> success (T data){
        return new ApiResponse<>(data, null);
    }

    public static ApiResponse<Void> fail(String code, String message){
        return new ApiResponse<>(null, new ErrorBody(code, message));
    }

    public record ErrorBody (String code, String message){

    }
}
