package com.ttuk.global.config;

import com.ttuk.category.dto.CategorySummaries;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORIES = "categories";


    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer(CacheVersion cacheVersion) {

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig().prefixCacheNameWith(cacheVersion.prefix());

        RedisCacheConfiguration categoryConfig =
                defaults.entryTtl(Duration.ofHours(1)).
                        serializeValuesWith(SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(CategorySummaries.class)));

        return builder -> builder.cacheDefaults(defaults).withCacheConfiguration(CATEGORIES, categoryConfig);

    }

}
