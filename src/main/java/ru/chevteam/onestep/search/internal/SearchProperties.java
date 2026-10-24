package ru.chevteam.onestep.search.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * @param maxResults      сколько отелей показывать
 * @param budgetTolerance на сколько (доля) можно превысить бюджет, чтобы отель всё ещё показать как компромисс
 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(Integer maxResults, BigDecimal budgetTolerance) {

    public SearchProperties {
        maxResults = maxResults == null ? 10 : maxResults;
        budgetTolerance = budgetTolerance == null ? new BigDecimal("0.30") : budgetTolerance;
    }
}
