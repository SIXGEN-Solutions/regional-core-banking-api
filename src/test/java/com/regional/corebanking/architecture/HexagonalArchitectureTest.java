package com.regional.corebanking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.regional.corebanking");

    @Test
    void domainMustNotDependOnApiApplicationOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnApiOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void productionCodeMustNotDependOnSixpayPackages() {
        noClasses()
                .that().resideInAPackage("com.regional.corebanking..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.sixpay..")
                .check(classes);
    }
}
