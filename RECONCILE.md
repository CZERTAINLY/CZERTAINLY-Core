# Reconcile before merging to main

Scope: `integration/spring-boot-4.1`. This file is deleted by the merge commit — if it
still exists on `main`, the merge skipped these checks.

Run them after **every sync from main**, not just before the merge.

## No CI runs on this branch

`build_pr.yml` triggers on `pull_request` into `[main*, feat/*, hotfix/*]`, `build.yml` on
`push` to `[main*]`, `codeql.yml` on both. This branch matches none of them, so a sub-PR
into it runs no compile, no tests, no Spotless, no Checkstyle, no CodeQL and no Sonar. The
merge to `main` is the first time the gate executes.

Run the whole gate locally on the branch head before opening that merge:

```bash
mvn -B -U -ntp spotless:check checkstyle:check
mvn -B -U -ntp test-compile -Dmaven.compiler.proc=full
python3 scripts/cbom/build_identity_tables.py --output src/main/resources/cbom/identity-tables.json
git diff --exit-code -- src/main/resources/cbom/identity-tables.json
mvn -B verify
```

## Version coordinate

`2.20.0-SBM-SNAPSHOT` is an integration label, not a release version.

```bash
grep -rn "SBM-SNAPSHOT" pom.xml ../interfaces-spring-boot/pom.xml ; # expect: no output at merge
```

Both poms must move together — this repo pins the interfaces artifact, so changing one
alone breaks the build.

## The interfaces dependency resolves from ~/.m2 only

`com.otilm:interfaces:2.20.0-SBM-SNAPSHOT` is published nowhere. It is 404 on Sonatype
snapshots, absent from Maven Central and absent from GitHub Packages. The only remotely
resolvable interfaces snapshot, `2.20.0-SNAPSHOT`, is built from interfaces `main` on the
Spring Boot 3.x parent and cannot satisfy this branch.

So this branch builds only on a machine that has installed it locally:

```bash
cd ../interfaces-spring-boot && git checkout integration/spring-boot-4.1 && mvn install -DskipTests
```

Built from interfaces `integration/spring-boot-4.1` at `0dd634ba71ae513fccb1de29dfcdb94d5b0d0ddb`.

Clearing this is OmniTrustILM/interfaces#806, whose "`3.0.0-SNAPSHOT` is published for
`core` to consume" criterion is still unchecked.
