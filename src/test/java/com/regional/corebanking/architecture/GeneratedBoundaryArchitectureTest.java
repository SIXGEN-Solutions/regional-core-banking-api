package com.regional.corebanking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class GeneratedBoundaryArchitectureTest {

    private final JavaClasses classes =
            new ClassFileImporter().importPackages("com.regional.corebanking");

    @Test
    void generatedTransportMustNotDependOnRegionalDomainOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("com.regional.corebanking.generated..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.regional.corebanking..domain..",
                        "com.regional.corebanking..infrastructure.."
                )
                .check(classes);
    }

    @Test
    void domainMustNotDependOnGeneratedTransport() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.regional.corebanking.generated..")
                .check(classes);
    }
}
