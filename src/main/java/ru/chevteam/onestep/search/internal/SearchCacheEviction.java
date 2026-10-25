package ru.chevteam.onestep.search.internal;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import ru.chevteam.onestep.enrichment.HotelFactsUpdated;

/** Пайплайн пересчитал характеристики — закэшированная выдача больше не верна. */
@Component
class SearchCacheEviction {

    @CacheEvict(cacheNames = SearchCacheConfig.CACHE, allEntries = true)
    @org.springframework.context.event.EventListener
    void on(HotelFactsUpdated event) {
        // тело не нужно: всю работу делает @CacheEvict
    }
}
