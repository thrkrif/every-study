package com.everystudy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 모듈 경계 규칙 (ARCHITECTURE 3장 "의존 방향 규칙", ADR-0003).
 *
 * <p>패키지 모양은 {@code <루트>.<모듈>.<계층>} 이다. 루트 바로 아래 패키지 하나가 모듈이고, {@code common} 은
 * 모든 모듈이 쓰는 공용 패키지라 기능 모듈로 세지 않는다. 루트를 인자로 받는 이유는 같은 규칙을 위반 예시
 * 패키지에도 적용해 규칙이 실제로 실패하는지 확인하기 위해서다 ({@code ModuleBoundaryRulesTest}).
 *
 * <p>아직 없는 모듈·계층에 대한 규칙은 검사할 클래스가 없어도 통과해야 하므로 {@code allowEmptyShould(true)} 를
 * 붙인다.
 */
final class ModuleBoundaryRules {

    private static final String COMMON = "common";

    /** 다른 모듈에 공개하는 패키지. 나머지(domain, repository, web 등)는 모듈 내부다. */
    private static final Set<String> PUBLIC_LAYERS = Set.of("service", "event");

    private final String rootPackage;

    private ModuleBoundaryRules(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    static ModuleBoundaryRules forRoot(String rootPackage) {
        return new ModuleBoundaryRules(rootPackage);
    }

    List<ArchRule> all() {
        return List.of(
                otherModulesAreUsedOnlyThroughServiceAndEvent(),
                webDoesNotUseRepository(),
                serviceDoesNotDependOnWeb(),
                domainAndRepositoryDoNotDependOnWebOrService(),
                commonDoesNotDependOnModules(),
                modulesAreFreeOfCycles(),
                studyDoesNotDependOnChat());
    }

    ArchRule otherModulesAreUsedOnlyThroughServiceAndEvent() {
        return classes()
                .should(useOtherModulesOnlyThroughPublicLayers())
                .as("다른 모듈은 service·event 패키지로만 참조한다 (domain·repository 등 내부 참조 금지)")
                .allowEmptyShould(true);
    }

    ArchRule webDoesNotUseRepository() {
        return noClasses().that(areInLayer("web"))
                .should().dependOnClassesThat(areInLayer("repository"))
                .as("web 은 repository 를 직접 쓰지 않는다 (web → service → domain/repository)")
                .allowEmptyShould(true);
    }

    ArchRule serviceDoesNotDependOnWeb() {
        return noClasses().that(areInLayer("service"))
                .should().dependOnClassesThat(areInLayer("web"))
                .as("service 는 web 에 의존하지 않는다")
                .allowEmptyShould(true);
    }

    ArchRule domainAndRepositoryDoNotDependOnWebOrService() {
        return noClasses().that(areInLayer("domain", "repository"))
                .should().dependOnClassesThat(areInLayer("web", "service"))
                .as("domain·repository 는 web·service 에 의존하지 않는다")
                .allowEmptyShould(true);
    }

    ArchRule commonDoesNotDependOnModules() {
        return noClasses().that().resideInAPackage(rootPackage + "." + COMMON + "..")
                .should().dependOnClassesThat(areInAFeatureModule())
                .as("common 은 기능 모듈에 의존하지 않는다")
                .allowEmptyShould(true);
    }

    ArchRule modulesAreFreeOfCycles() {
        return slices().matching(rootPackage + ".(*)..")
                .should().beFreeOfCycles()
                .as("모듈 간 의존이 순환하지 않는다")
                .allowEmptyShould(true);
    }

    /** 채팅방 생성·폐쇄는 chat 이 study 의 이벤트를 받아서 한다. 공개 service 를 거치는 참조도 금지다 (PLAN X-8). */
    ArchRule studyDoesNotDependOnChat() {
        return noClasses().that().resideInAPackage(rootPackage + ".study..")
                .should().dependOnClassesThat().resideInAPackage(rootPackage + ".chat..")
                .as("study 는 chat 에 의존하지 않는다 (방향은 chat → study)")
                .allowEmptyShould(true);
    }

    private ArchCondition<JavaClass> useOtherModulesOnlyThroughPublicLayers() {
        return new ArchCondition<>("use other modules only through " + PUBLIC_LAYERS) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> originModule = featureModuleOf(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    Optional<String> targetModule = featureModuleOf(target);
                    boolean otherModule = targetModule.isPresent() && !targetModule.equals(originModule);
                    if (otherModule && !PUBLIC_LAYERS.contains(layerOf(target))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private DescribedPredicate<JavaClass> areInLayer(String... layers) {
        Set<String> wanted = Set.of(layers);
        return DescribedPredicate.describe(
                "are in layer " + wanted,
                javaClass -> featureModuleOf(javaClass).isPresent() && wanted.contains(layerOf(javaClass)));
    }

    private DescribedPredicate<JavaClass> areInAFeatureModule() {
        return DescribedPredicate.describe(
                "are in a feature module", javaClass -> featureModuleOf(javaClass).isPresent());
    }

    /** 루트 바로 아래 패키지 이름. 루트 밖이거나 루트에 직접 있거나 common 이면 비어 있다. */
    private Optional<String> featureModuleOf(JavaClass javaClass) {
        List<String> segments = segmentsBelowRoot(javaClass);
        if (segments.isEmpty() || segments.get(0).equals(COMMON)) {
            return Optional.empty();
        }
        return Optional.of(segments.get(0));
    }

    /** 모듈 바로 아래 패키지 이름 (web, service, domain, repository, event …). 없으면 빈 문자열. */
    private String layerOf(JavaClass javaClass) {
        List<String> segments = segmentsBelowRoot(javaClass);
        return segments.size() < 2 ? "" : segments.get(1);
    }

    private List<String> segmentsBelowRoot(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = rootPackage + ".";
        if (!packageName.startsWith(prefix)) {
            return List.of();
        }
        return List.of(packageName.substring(prefix.length()).split("\\."));
    }
}
