package com.otilm.core.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSignatureTest {

    private Path write(Path dir, String name, String src) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, src);
        return f;
    }

    @Test
    void parsesImportsMocksProfilesPropsAndDirties(@TempDir Path dir) throws IOException {
        Path f = write(dir, "SampleITest.java", """
                package x;
                @Import({ProducerMocks.class, PollMocks.class})
                @ActiveProfiles(value = {"messaging-int-test"}, inheritProfiles = false)
                @TestPropertySource(properties = {"a=1"})
                @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
                class SampleITest extends BaseSpringBootTest {
                    @MockitoBean private OpaClient opaClient;
                    @MockitoSpyBean ActionProducer actionProducer;
                }
                """);
        TestClassTaxonomy.ContextTokens t = TestClassTaxonomy.annotationTokens(f);
        assertThat(t.imports()).containsExactlyInAnyOrder("ProducerMocks", "PollMocks");
        assertThat(t.mocks()).containsExactlyInAnyOrder("OpaClient", "ActionProducer");
        assertThat(t.profiles()).contains("messaging-int-test", "inheritProfiles=false");
        assertThat(t.props()).isNotEmpty();
        assertThat(t.dirties()).contains("BEFORE_CLASS");
    }

    @Test
    void signatureUnionsAncestorChainAndIsOrderInsensitiveForImports(@TempDir Path dir) throws IOException {
        write(dir, "BaseSpringBootTest.java", """
                @SpringBootTest class BaseSpringBootTest {
                    @MockitoBean OpaClient opaClient;
                    @MockitoBean AuditLogsProducer auditLogsProducer;
                }
                """);
        write(dir, "AbITest.java", """
                @Import({ProducerMocks.class, PollMocks.class})
                class AbITest extends BaseSpringBootTest {}
                """);
        write(dir, "BaITest.java", """
                @Import({PollMocks.class, ProducerMocks.class})
                class BaITest extends BaseSpringBootTest {}
                """);
        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("AbITest", graph, byName))
                .isEqualTo(ContextSignature.of("BaITest", graph, byName));
    }

    @Test
    void nestedTestConfigurationForksSignatureAndIsCaptured(@TempDir Path dir) throws IOException {
        write(dir, "BaseSpringBootTest.java", """
                @SpringBootTest class BaseSpringBootTest {}
                """);
        Path a = write(dir, "AuditLogAspectITest.java", """
                class AuditLogAspectITest extends BaseSpringBootTest {
                    @TestConfiguration
                    static class AuditConfig {}
                }
                """);
        write(dir, "UpdateTrustedCaMarkITest.java", """
                class UpdateTrustedCaMarkITest extends BaseSpringBootTest {
                    @TestConfiguration
                    static class TrustedCaConfig {}
                }
                """);
        TestClassTaxonomy.ContextTokens ta = TestClassTaxonomy.annotationTokens(a);
        assertThat(ta.configs()).containsExactly("AuditConfig");

        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("AuditLogAspectITest", graph, byName))
                .isNotEqualTo(ContextSignature.of("UpdateTrustedCaMarkITest", graph, byName));
    }

    @Test
    void springBootTestArgumentsForkSignature(@TempDir Path dir) throws IOException {
        write(dir, "RandomPortITest.java", """
                @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
                class RandomPortITest {}
                """);
        write(dir, "DefaultEnvITest.java", """
                @SpringBootTest
                class DefaultEnvITest {}
                """);
        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("RandomPortITest", graph, byName))
                .isNotEqualTo(ContextSignature.of("DefaultEnvITest", graph, byName));
    }

    @Test
    void autoConfigureAnnotationForksSignature(@TempDir Path dir) throws IOException {
        write(dir, "MockMvcITest.java", """
                @SpringBootTest
                @AutoConfigureMockMvc
                class MockMvcITest {}
                """);
        write(dir, "PlainITest.java", """
                @SpringBootTest
                class PlainITest {}
                """);
        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("MockMvcITest", graph, byName))
                .isNotEqualTo(ContextSignature.of("PlainITest", graph, byName));
    }

    @Test
    void typeExcludeFiltersForkSignature(@TempDir Path dir) throws IOException {
        Path filtered = write(dir, "FilteredITest.java", """
                @SpringBootTest
                @Import(ProducerMocks.class)
                @TypeExcludeFilters(ProducerMocks.MockedProducersTypeExcludeFilter.class)
                class FilteredITest {}
                """);
        write(dir, "UnfilteredITest.java", """
                @SpringBootTest
                @Import(ProducerMocks.class)
                class UnfilteredITest {}
                """);
        assertThat(TestClassTaxonomy.annotationTokens(filtered).typeExcludeFilters())
                .containsExactly("ProducerMocks.MockedProducersTypeExcludeFilter");

        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("FilteredITest", graph, byName))
                .isNotEqualTo(ContextSignature.of("UnfilteredITest", graph, byName));
    }

    @Test
    void typeExcludeFilterOrderIsNotASignatureAxis(@TempDir Path dir) throws IOException {
        // TypeExcludeFiltersContextCustomizer's equals/hashCode are the filter SET, so order cannot fork the context.
        write(dir, "AbFilterITest.java", """
                @SpringBootTest
                @TypeExcludeFilters({ProducerMocks.MockedProducersTypeExcludeFilter.class, PollMocks.PollFilter.class})
                class AbFilterITest {}
                """);
        write(dir, "BaFilterITest.java", """
                @SpringBootTest
                @TypeExcludeFilters({PollMocks.PollFilter.class, ProducerMocks.MockedProducersTypeExcludeFilter.class})
                class BaFilterITest {}
                """);
        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("AbFilterITest", graph, byName))
                .isEqualTo(ContextSignature.of("BaFilterITest", graph, byName));
    }

    @Test
    void typeExcludeFiltersKeepTheirOuterClassQualifier(@TempDir Path dir) throws IOException {
        // Two modules may each declare a same-named nested filter; dropping the qualifier would collapse them.
        Path a = write(dir, "AModuleITest.java", """
                @SpringBootTest
                @TypeExcludeFilters(AMocks.ExcludeFilter.class)
                class AModuleITest {}
                """);
        write(dir, "BModuleITest.java", """
                @SpringBootTest
                @TypeExcludeFilters(BMocks.ExcludeFilter.class)
                class BModuleITest {}
                """);
        assertThat(TestClassTaxonomy.annotationTokens(a).typeExcludeFilters())
                .containsExactly("AMocks.ExcludeFilter");

        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("AModuleITest", graph, byName))
                .isNotEqualTo(ContextSignature.of("BModuleITest", graph, byName));
    }

    @Test
    void typeExcludeFiltersAreUnionedAcrossTheInheritanceChain(@TempDir Path dir) throws IOException {
        // @TypeExcludeFilters is @Inherited, so a base class's filters apply to every subclass.
        write(dir, "BaseFilteredTest.java", """
                @SpringBootTest
                @TypeExcludeFilters(ProducerMocks.MockedProducersTypeExcludeFilter.class)
                abstract class BaseFilteredTest {}
                """);
        write(dir, "ChildITest.java", """
                class ChildITest extends BaseFilteredTest {}
                """);
        write(dir, "StandaloneITest.java", """
                @SpringBootTest
                @TypeExcludeFilters(ProducerMocks.MockedProducersTypeExcludeFilter.class)
                class StandaloneITest {}
                """);
        Map<String, String> graph = TestClassTaxonomy.parseExtends(dir);
        Map<String, Path> byName = ContextSignature.filesBySimpleName(dir);
        assertThat(ContextSignature.of("ChildITest", graph, byName))
                .isEqualTo(ContextSignature.of("StandaloneITest", graph, byName));
    }

    @Test
    void textBlockFixtureContentIsNotParsedAsRealAnnotations(@TempDir Path dir) throws IOException {
        // A meta-test whose only "annotations" live inside a text block (fixture data), like this very
        // class. code() strips text blocks, so these must not be misread as real context annotations.
        String content = """
                package x;
                class MetaTest {
                    String fixture = \"""
                            @Import({ProducerMocks.class, PollMocks.class})
                            @ActiveProfiles("messaging-int-test")
                            @TestConfiguration static class Nested {}
                            class Sample extends BaseSpringBootTest {}
                            \""";
                }
                """;
        Path f = write(dir, "MetaTest.java", content);
        TestClassTaxonomy.ContextTokens t = TestClassTaxonomy.annotationTokens(f);
        assertThat(t.imports()).isEmpty();
        assertThat(t.profiles()).isEmpty();
        assertThat(t.configs()).isEmpty();
    }
}
