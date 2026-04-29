package com.czertainly.core.architecture;

import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.ExternalAuthorizationMissing;
import com.czertainly.core.security.authz.ProtocolEndpoint;
import com.czertainly.core.security.authz.SelfPrincipalEndpoint;
import com.czertainly.core.security.authz.UnauthenticatedEndpoint;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.czertainly.core.service", importOptions = ImportOption.DoNotIncludeTests.class)
public class ExternalServiceAuthorizationArchTest {

    private static final Set<String> AUTH_ANNOTATION_NAMES = Set.of(
            ExternalAuthorization.class.getName(),
            ExternalAuthorizationMissing.class.getName(),
            ProtocolEndpoint.class.getName(),
            SelfPrincipalEndpoint.class.getName(),
            UnauthenticatedEndpoint.class.getName()
    );

    @ArchTest
    static final ArchRule external_service_interfaces_must_not_extend_other_interfaces =
            classes()
                    .that().haveSimpleNameEndingWith("ExternalService")
                    .and().areInterfaces()
                    .should(new ArchCondition<JavaClass>("not extend other interfaces") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            if (!javaClass.getDirectInterfaces().isEmpty()) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getName() + " extends other interfaces; *ExternalService interfaces must be flat so the auth-annotation rule covers all callable methods"));
                            }
                        }
                    });

    @ArchTest
    static final ArchRule every_external_service_method_has_exactly_one_auth_annotation =
            methods()
                    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("ExternalService")
                    .and().areDeclaredInClassesThat().areInterfaces()
                    .should(new ArchCondition<JavaMethod>("carry exactly one authorization annotation") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            long count = method.getAnnotations().stream()
                                    .filter(a -> AUTH_ANNOTATION_NAMES.contains(a.getType().getName()))
                                    .count();
                            if (count != 1) {
                                events.add(SimpleConditionEvent.violated(method, String.format(
                                        "%s has %d authorization annotations, expected exactly 1",
                                        method.getFullName(), count)));
                            }
                        }
                    });
}
