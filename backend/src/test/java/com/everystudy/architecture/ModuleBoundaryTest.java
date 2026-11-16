package com.everystudy.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.everystudy.EveryStudyApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** 운영 코드(src/main)가 모듈 경계 규칙을 지키는지 검사한다 (ARCHITECTURE 3장, ADR-0003). */
class ModuleBoundaryTest {

    private static final String ROOT = "com.everystudy";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages(ROOT);

    @Test
    @DisplayName("운영 코드를 읽어 왔고 테스트 코드는 섞이지 않았다")
    void importsProductionCodeOnly() {
        assertThat(PRODUCTION.contain(EveryStudyApplication.class)).isTrue();
        assertThat(PRODUCTION.contain(ModuleBoundaryTest.class)).isFalse();
    }

    @TestFactory
    @DisplayName("운영 코드는 모듈 경계 규칙을 지킨다")
    Stream<DynamicTest> productionCodeFollowsModuleBoundaries() {
        return ModuleBoundaryRules.forRoot(ROOT).all().stream()
                .map(rule -> dynamicTest(rule.getDescription(), () -> rule.check(PRODUCTION)));
    }
}
