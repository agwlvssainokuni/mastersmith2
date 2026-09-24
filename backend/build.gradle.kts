/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import java.util.zip.ZipFile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// バックエンド（Spring Boot）のビルド。成果物はフロントエンドのビルド結果を同梱した実行可能 WAR。

plugins {
    java
    war
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

// 依存関係の版を lockfile（backend/gradle.lockfile）で固定する。
// 更新するときは ./gradlew :backend:resolveAndLockAll --write-locks を実行する。
dependencyLocking {
    lockAllConfigurations()
}

// 組み込みの Tomcat の版を、脆弱性の修正を含む版にそろえる（gradle/libs.versions.toml の tomcat の説明を参照）。
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.tomcat.embed") {
            useVersion(libs.versions.tomcat.get())
            because("Spring Boot が管理する Tomcat 11.0.24 の重大度 High 以上の脆弱性を避けるため")
        }
    }
}

dependencies {
    val bom = platform(libs.spring.boot.bom)
    implementation(bom)
    providedRuntime(bom)
    testImplementation(bom)
    testRuntimeOnly(bom)

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.aspectj)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.opentelemetry.logback.appender)
    // DSL（U2）の YAML の安全な読み込みと JSON Schema の検証。networknt の推移依存の YAML の読み込み（Jackson の YAML と
    // snakeyaml-engine）は、別名を展開しない・重複キーを上書きするため使わず、依存から外す（DslBoundaryArchitectureTest でも守る）。
    implementation(libs.snakeyaml)
    implementation(libs.networknt.json.schema.validator) {
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
        exclude(group = "org.snakeyaml", module = "snakeyaml-engine")
    }
    runtimeOnly(libs.h2)
    // 対象DB（U1）の JDBC ドライバー。読み取り専用の接続で、スキーマのメタデータを読むだけに使う。
    runtimeOnly(libs.mysql.connector.j)
    runtimeOnly(libs.mariadb.java.client)
    runtimeOnly(libs.postgresql)
    // 外部のサーブレットコンテナへ置く WAR としても使えるよう、組み込みの Tomcat は providedRuntime にする。
    providedRuntime(libs.spring.boot.starter.tomcat.runtime)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    spotbugsPlugins(libs.findsecbugs.plugin)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// ---- テスト ----
// 単体テスト（名前が Test で終わるクラス）と結合テスト（名前が IT で終わるクラス）を、
// 同じソースの組（src/test/java）から名前で分けて実行する。

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    systemProperty("user.timezone", "Asia/Tokyo")
    // 結合テストで Host ヘッダーを指定して、エラー応答の type の URL の組み立てを確かめるため。
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "host")
    testLogging {
        // 対象DB のテストがコンテナの実行環境の無さで飛ばされたとき、黙って飛ばさないよう SKIPPED も出す（NFR12.3）。
        events("failed", "skipped")
        // jqwik・fast-check の失敗時の乱数の種を出力に残すため、失敗の詳細をすべて出す。
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

tasks.test {
    description = "単体テスト（*Test）を実行する。"
    filter {
        includeTestsMatching("*Test")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "結合テスト（*IT、Spring と組み込みの H2 を起動する）を実行する。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("*IT")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

// ---- カバレッジ（JaCoCo） ----
// 計測から外すのは、起動クラスと設定値だけのクラス（@ConfigurationProperties の record）に限る。

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageExclusions = listOf(
    "cherry/mastersmith/MastersmithApplication*",
    "cherry/mastersmith/**/*Properties.class",
    "cherry/mastersmith/**/*Properties$*.class",
)

val coverageExecutionData = fileTree(layout.buildDirectory.dir("jacoco")) {
    include("test.exec", "integrationTest.exec")
}

val coverageClassDirectories = files(
    sourceSets.main.get().output.classesDirs.map { dir ->
        fileTree(dir) {
            exclude(coverageExclusions)
        }
    },
)

tasks.jacocoTestReport {
    description = "単体テストと結合テストの実行記録を合わせてカバレッジの報告を作る。"
    mustRunAfter(tasks.test, integrationTest)
    executionData.setFrom(coverageExecutionData)
    classDirectories.setFrom(coverageClassDirectories)
    reports {
        xml.required = true
        html.required = true
    }
}

/**
 * パッケージごとの下限を当てない既存のパッケージ（全体の合計で判定する）。U1（Intent 260923-dsl-schema-loader の最初の Bolt）で
 * 既存のパッケージを実測したところ、単独で下限を下回るもの（audit.service の行 77.2%、common.health の行 79.2%、
 * auth.repository の分岐 50.0%）があったため、team.md の Testing Posture に従い、パッケージごとの下限は新しく作るパッケージ
 * だけに当てる。この一覧は増やさない（新しく作るパッケージは、一覧に無いので自動で下限の対象になる）。
 */
val packagesJudgedByTotal = listOf(
    "cherry.mastersmith.access.domain",
    "cherry.mastersmith.access.service",
    "cherry.mastersmith.access.web",
    "cherry.mastersmith.audit.domain",
    "cherry.mastersmith.audit.repository",
    "cherry.mastersmith.audit.service",
    "cherry.mastersmith.auth.domain",
    "cherry.mastersmith.auth.repository",
    "cherry.mastersmith.auth.service",
    "cherry.mastersmith.auth.web",
    "cherry.mastersmith.common.error.domain",
    "cherry.mastersmith.common.error.service",
    "cherry.mastersmith.common.error.web",
    "cherry.mastersmith.common.health",
    "cherry.mastersmith.common.i18n.domain",
    "cherry.mastersmith.common.observability",
    "cherry.mastersmith.common.security",
    "cherry.mastersmith.common.web",
    "cherry.mastersmith.config",
    "cherry.mastersmith.user.domain",
    "cherry.mastersmith.user.repository",
    "cherry.mastersmith.user.service",
)

tasks.jacocoTestCoverageVerification {
    description = "カバレッジの下限（行 80%・分岐 70%）を、全体の合計と、新しく作るパッケージごとに検証する。"
    mustRunAfter(tasks.test, integrationTest, tasks.jacocoTestReport)
    executionData.setFrom(coverageExecutionData)
    classDirectories.setFrom(coverageClassDirectories)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
        // パッケージごとの下限（team.md の Testing Posture）。既存のパッケージは上の一覧で外し、全体の合計で判定する。
        rule {
            element = "PACKAGE"
            excludes = packagesJudgedByTotal
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// ---- フォーマットとライセンスヘッダー（Spotless） ----

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        licenseHeader(rootProject.extra["licenseHeaderBlock"] as String)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---- 静的解析（SpotBugs + FindSecBugs） ----
// 見つかったものは報告に出し、統合を止めるかどうかは spotbugsGate で決める（重大度 High で失敗）。

spotbugs {
    toolVersion = libs.versions.spotbugs.tool.get()
    effort = Effort.MAX
    reportLevel = Confidence.LOW
    ignoreFailures = true
    excludeFilter = file("config/spotbugs-exclude.xml")
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("xml") { required = true }
    reports.create("html") { required = true }
}

tasks.named("spotbugsTest") {
    // テストのコードは静的解析の関門の対象外とする。
    enabled = false
}

tasks.register("spotbugsGate") {
    description =
        "SpotBugs の報告を読み、重大度 High（priority 1）の指摘と、SQL インジェクション系（パターン名が SQL_ で始まる）の指摘が" +
            "あれば priority によらず失敗させる。それ以外は警告として表示する。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.named("spotbugsMain"))
    val report = layout.buildDirectory.file("reports/spotbugs/main.xml")
    inputs.file(report)
    doLast {
        val xml = report.get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(xml)
        val bugs = document.getElementsByTagName("BugInstance")
        var high = 0
        var sql = 0
        for (i in 0 until bugs.length) {
            val bug = bugs.item(i) as org.w3c.dom.Element
            val priority = bug.getAttribute("priority")
            val type = bug.getAttribute("type")
            val sourceLine = bug.getElementsByTagName("SourceLine").item(0) as org.w3c.dom.Element?
            val where = sourceLine?.let { "${it.getAttribute("sourcepath")}:${it.getAttribute("start")}" } ?: "?"
            when {
                // SQL インジェクション系は priority によらず止める（team.md の Code Style、U1 の NFR6.3）。
                type.startsWith("SQL_") -> {
                    sql++
                    logger.error("SpotBugs [SQL, priority $priority] $type $where")
                }
                priority == "1" -> {
                    high++
                    logger.error("SpotBugs [High] $type $where")
                }
                else -> logger.warn("SpotBugs [warning, priority $priority] $type $where")
            }
        }
        if (high > 0 || sql > 0) {
            throw GradleException(
                "SpotBugs で統合を止める指摘があります（重大度 High $high 件、SQL インジェクション系 $sql 件。${xml.path}）。",
            )
        }
    }
}

// ---- 成果物（実行可能 WAR） ----

tasks.war {
    // 実行可能 WAR（bootWar）だけを作る。
    enabled = false
}

tasks.bootWar {
    archiveFileName = "mastersmith.war"
    // フロントエンドのビルド結果（frontend/dist）を、画面の静的なファイルとして同梱する。
    dependsOn(":frontendBuild")
    from(rootProject.layout.projectDirectory.dir("frontend/dist")) {
        into("WEB-INF/classes/static")
    }
}

// ---- DSL の JSON Schema の公開（U2） ----
// 正本は src/main/resources/dsl/dsl-schema-v1.json の1つ（検証もこれを読む）。画面の静的なファイルの置き場（static/dsl/）へ
// ビルドで複写し、ログインなしの /dsl/dsl-schema-v1.json で配る。WAR には WEB-INF/classes/static/dsl/ として入る。

val dslSchemaSource = layout.projectDirectory.file("src/main/resources/dsl/dsl-schema-v1.json")

tasks.processResources {
    from(dslSchemaSource) {
        into("static/dsl")
    }
}

tasks.register("verifyDslSchemaInWar") {
    description = "実行可能 WAR の中の JSON Schema（検証用と公開用の2つ）が、正本と同じ内容であることを確かめる。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.bootWar)
    val war = tasks.bootWar.flatMap { it.archiveFile }
    inputs.file(war)
    inputs.file(dslSchemaSource)
    doLast {
        val expected = dslSchemaSource.asFile.readBytes()
        ZipFile(war.get().asFile).use { zip ->
            for (entryName in listOf("WEB-INF/classes/dsl/dsl-schema-v1.json", "WEB-INF/classes/static/dsl/dsl-schema-v1.json")) {
                val entry = zip.getEntry(entryName) ?: throw GradleException("WAR に $entryName がありません。")
                val actual = zip.getInputStream(entry).use { it.readBytes() }
                if (!actual.contentEquals(expected)) {
                    throw GradleException("WAR の $entryName が正本（${dslSchemaSource.asFile.path}）と違います。")
                }
            }
        }
    }
}

// ---- 依存関係の固定の更新 ----

tasks.register("resolveAndLockAll") {
    description = "すべての構成を解決して lockfile を書き直す（--write-locks と一緒に使う）。"
    notCompatibleWithConfigurationCache("構成をまとめて解決するため")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "--write-locks を付けて実行してください。" }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
