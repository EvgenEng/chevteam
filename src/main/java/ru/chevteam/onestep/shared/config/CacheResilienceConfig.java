package ru.chevteam.onestep.shared.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Кэш — оптимизация, а не зависимость: если Redis недоступен, запрос выполняется без кэша
 * (ошибка логируется), а не падает с 500.
 */
@Configuration(proxyBeanMethods = false)
public class CacheResilienceConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
