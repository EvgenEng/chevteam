package ru.chevteam.onestep.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Архитектурные правила в виде обычных JUnit-тестов — запускаются любым раннером без отдельного движка.
 */
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ru.chevteam.onestep");

    /** Доменная логика разбора запроса не знает о Spring — её можно переиспользовать и тестировать без контекста. */
    @Test
    void intentDomainIsFrameworkFree() {
        noClasses()
                .that().resideInAnyPackage("ru.chevteam.onestep.intent", "ru.chevteam.onestep.intent.rules")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(CLASSES);
    }

    /** Скоринг и цены — чистые функции без фреймворка. */
    @Test
    void scoringIsFrameworkFree() {
        noClasses()
                .that().haveSimpleNameStartingWith("MatchScorer")
                .or().haveSimpleNameStartingWith("PriceCalculator")
                .or().haveSimpleName("WalkingTime")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .check(CLASSES);
    }

    @Test
    void controllersLiveInWebPackages() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..web..")
                .check(CLASSES);
    }

    @Test
    void webDoesNotTouchRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                .check(CLASSES);
    }
}
