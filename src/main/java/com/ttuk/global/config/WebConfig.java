package com.ttuk.global.config;

import com.ttuk.global.auth.LoginCheckInterceptor;
import com.ttuk.global.auth.LoginMemberArgumentResolver;
import com.ttuk.global.auth.RoleCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginCheckInterceptor loginCheckInterceptor;
    private final RoleCheckInterceptor roleCheckInterceptor;
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginCheckInterceptor)
                .order(1)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                  "/api/members",
                  "/api/auth/login"
                );

        registry.addInterceptor(roleCheckInterceptor)
                .order(2)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers){
        resolvers.add(loginMemberArgumentResolver);
    }

}
