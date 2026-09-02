package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Keys every component of an external corpus and writes the result, so two revisions can be diffed row by row.
 *
 * <p>
 * An instrument rather than an assertion: it pins nothing and cannot fail on its own. What it answers is the question
 * the vector suite structurally cannot -- whether a rule change moves a key on real producer output, and whether the
 * partition merges or splits -- because the vectors wrap each component in a document of its own and so see neither
 * cross-component reference resolution nor document-scoped refutation.
 *
 * <p>
 * It is committed because the alternative is a number nobody can reproduce. core#2165's costing rests on runs of this
 * over the 2026-08-31 corpus (196 documents, 8 048 components): 2 rows moved, 4 836 distinct keys before and after,
 * identical group-size histograms. A reviewer who wants to check that has to be able to re-run it.
 *
 * <p>
 * The corpus is not in this repository -- it carries real third-party documents, some with secret-scanner findings in
 * them -- so the test is skipped unless it is told where one is:
 *
 * <pre>
 * mvn4 -o -B -Dtest=CorpusKeySnapshotTest -Dsurefire.failIfNoSpecifiedTests=false \
 *      -Dcorpus.dir=/path/to/corpora -Dcorpus.out=$PWD/keys.tsv test
 * </pre>
 *
 * <p>
 * Each row is {@code document<TAB>component index<TAB>chain step<TAB>identity key}, sorted, so two runs diff with
 * {@code diff} and a moved row names itself. A component that cannot be keyed records the exception class rather than
 * failing the run, because a crash is exactly the kind of movement worth diffing -- that is how the surrogate defect in
 * this branch was first priced.
 */
@EnabledIfSystemProperty(named = "corpus.dir", matches = ".+")
class CorpusKeySnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void snapshot() throws IOException {
        Path corpora = Path.of(System.getProperty("corpus.dir"));
        // Defaulted, because only corpus.dir gates the run: reading a second required property would meet anyone who
        // followed the skip condition alone with an NPE from Path.of(null) instead of a snapshot.
        Path out = Path.of(System.getProperty("corpus.out", "target/corpus-keys.tsv"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        AssetNormalizer normalizer = new AssetNormalizer(IdentityTables.load());
        CryptoAssetIdentity identity = new CryptoAssetIdentity(normalizer);
        List<String> rows = new ArrayList<>();
        List<Path> documents;
        try (Stream<Path> walk = Files.walk(corpora)) {
            documents = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path document : documents) {
            JsonNode root;
            try {
                root = MAPPER.readTree(document.toFile());
            } catch (IOException e) {
                continue;
            }
            if (!root.isObject()) {
                continue;
            }
            DocumentScope scope = DocumentScope.of(root, normalizer);
            List<JsonNode> components = DocumentScope.walk(root);
            for (int i = 0; i < components.size(); i++) {
                JsonNode component = components.get(i);
                String key;
                String step;
                try {
                    CryptoAssetIdentity.Identity built = identity.of(component, scope, Set.of());
                    key = built == null ? "null" : built.key();
                    step = built == null ? "null" : built.step();
                } catch (RuntimeException e) {
                    key = "THROWN:" + e.getClass().getSimpleName();
                    step = "THROWN";
                }
                rows.add(corpora.relativize(document) + "\t" + i + "\t" + step + "\t" + key);
            }
        }
        rows.sort(Comparator.naturalOrder());
        Files.write(out, rows, StandardCharsets.UTF_8);
    }
}
