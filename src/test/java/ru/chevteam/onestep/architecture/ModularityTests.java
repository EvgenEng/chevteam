package ru.chevteam.onestep.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import ru.chevteam.onestep.OneStepApplication;

/**
 * Границы модулей проверяются тестом, а не договорённостями.
 * Побочный эффект — актуальные диаграммы модулей в target/spring-modulith-docs.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(OneStepApplication.class);

    @Test
    void modulesRespectBoundaries() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
