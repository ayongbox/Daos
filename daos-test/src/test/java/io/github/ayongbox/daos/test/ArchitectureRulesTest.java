package io.github.ayongbox.daos.test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 依赖方向与分层约束。违反即构建失败，不依赖人工评审。
 */
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("io.github.ayongbox.daos");
    }

    @Test
    void coreMustNotDependOnUpperModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.github.ayongbox.daos.runtime..", "io.github.ayongbox.daos.web..", "io.github.ayongbox.daos.license..");
        rule.check(classes);
    }

    @Test
    void coreMustStayFreeOfWebAndBoot() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.core..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.boot..",
                        "jakarta.servlet..",
                        "jakarta.persistence..");
        rule.check(classes);
    }

    @Test
    void runtimeMustNotDependOnWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.runtime..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.github.ayongbox.daos.web..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void licenseMustNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.license..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.github.ayongbox.daos.runtime..", "io.github.ayongbox.daos.web..");
        rule.check(classes);
    }

    @Test
    void engineMustNotDependOnStore() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.runtime.engine..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.github.ayongbox.daos.runtime.store..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void coreMustNotDependOnExtensionModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("io.github.ayongbox.daos.core..")
                .should().dependOnClassesThat()
                .resideInAPackage("io.github.ayongbox.daos.ext..");
        rule.check(classes);
    }
}
