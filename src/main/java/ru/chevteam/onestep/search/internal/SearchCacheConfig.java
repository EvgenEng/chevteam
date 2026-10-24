package ru.chevteam.onestep.search.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import ru.chevteam.onestep.search.SearchResponse;

import java.time.Duration;

/**
 * Кэш результатов поиска в Redis.
 * <p>
 * Сериализатор типизирован конкретным классом: универсальный GenericJackson2JsonRedisSerializer
 * не пишет type-info для final-классов (records), и при чтении вернулся бы LinkedHashMap.
 * Кэш экономит и время, и деньги: повторный запрос не идёт в LLM.
 */
@Configuration(proxyBeanMethods = false)
class SearchCacheConfig {

    static final String CACHE = "search";

    @Bean
    RedisCacheManagerBuilderCustomizer searchCacheCustomizer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<SearchResponse> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, SearchResponse.class);
        return builder -> builder.withCacheConfiguration(CACHE, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .prefixCacheNameWith("onestep:v6:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }
}
