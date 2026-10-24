package ru.chevteam.onestep.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "ru.chevteam.onestep", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    /** Доменная логика разбора запроса не знает о Spring — её можно переиспользовать и тестировать без контекста. */
    @ArchTest
    static final ArchRule intentDomainIsFrameworkFree = noClasses()
            .that().resideInAnyPackage("ru.chevteam.onestep.intent", "ru.chevteam.onestep.intent.rules")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule controllersLiveInWebPackages = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule webDoesNotTouchRepositoriesDirectly = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().areAnnotatedWith(org.springframework.stereotype.Repository.class);
}
