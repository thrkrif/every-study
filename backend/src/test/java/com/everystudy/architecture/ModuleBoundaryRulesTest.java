package com.everystudy.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 규칙이 실제로 위반을 잡는지, 올바른 배치는 통과시키는지를 예시 패키지(fixture)로 확인한다. */
class ModuleBoundaryRulesTest {

    private static final String CLEAN = "com.everystudy.architecture.fixture.clean";
    private static final String VIOLATING = "com.everystudy.architecture.fixture.violating";

    private final ModuleBoundaryRules rules = ModuleBoundaryRules.forRoot(VIOLATING);
    private final JavaClasses violating = new ClassFileImporter().importPackages(VIOLATING);

    @Test
    @DisplayName("규칙을 지킨 배치는 모든 규칙을 통과한다")
    void cleanLayoutPassesEveryRule() {
        JavaClasses clean = new ClassFileImporter().importPackages(CLEAN);

        assertThat(clean).isNotEmpty();
        for (ArchRule rule : ModuleBoundaryRules.forRoot(CLEAN).all()) {
            assertThat(rule.evaluate(clean).getFailureReport().getDetails())
                    .as(rule.getDescription())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("다른 모듈의 domain 과 repository 를 참조하면 실패한다")
    void referencingOtherModulesDomainOrRepositoryFails() {
        List<String> violations = violationsOf(rules.otherModulesAreUsedOnlyThroughServiceAndEvent());

        assertThat(violations).hasSize(2);
        assertThat(violations).anySatisfy(v -> assertThat(v).contains("ChatReader", "study.domain.Study"));
        assertThat(violations).anySatisfy(v -> assertThat(v).contains("ChatReader", "study.repository.StudyRepository"));
    }

    @Test
    @DisplayName("web 이 repository 를 직접 쓰면 실패한다")
    void webUsingRepositoryFails() {
        List<String> violations = violationsOf(rules.webDoesNotUseRepository());

        assertThat(violations).singleElement().asString().contains("StudyController", "StudyRepository");
    }

    @Test
    @DisplayName("service 가 web 에 의존하면 실패한다")
    void serviceDependingOnWebFails() {
        List<String> violations = violationsOf(rules.serviceDoesNotDependOnWeb());

        assertThat(violations).singleElement().asString().contains("StudyService", "StudyResponse");
    }

    @Test
    @DisplayName("domain 이 service 에 의존하면 실패한다")
    void domainDependingOnServiceFails() {
        List<String> violations = violationsOf(rules.domainAndRepositoryDoNotDependOnWebOrService());

        assertThat(violations).singleElement().asString().contains("domain.Study", "StudyService");
    }

    @Test
    @DisplayName("common 이 기능 모듈에 의존하면 실패한다")
    void commonDependingOnModuleFails() {
        List<String> violations = violationsOf(rules.commonDoesNotDependOnModules());

        assertThat(violations).singleElement().asString().contains("AuditLogger", "StudyService");
    }

    @Test
    @DisplayName("모듈이 서로 의존해 순환하면 실패한다")
    void moduleCycleFails() {
        List<String> violations = violationsOf(rules.modulesAreFreeOfCycles());

        assertThat(violations).singleElement().asString().contains("study", "chat");
    }

    @Test
    @DisplayName("study 가 chat 에 의존하면 공개 service 를 거쳐도 실패한다")
    void studyDependingOnChatFails() {
        List<String> violations = violationsOf(rules.studyDoesNotDependOnChat());

        assertThat(violations).singleElement().asString().contains("StudyService", "ChatRoomService");
    }

    private List<String> violationsOf(ArchRule rule) {
        EvaluationResult result = rule.evaluate(violating);
        return result.getFailureReport().getDetails();
    }
}
