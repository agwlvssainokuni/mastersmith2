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
    implementation(libs.logstash.logback.encoder)
    implementation(libs.opentelemetry.logback.appender)
    runtimeOnly(libs.h2)
    // 外部のサーブレットコンテナへ置く WAR としても使えるよう、組み込みの Tomcat は providedRuntime にする。
    providedRuntime(libs.spring.boot.starter.tomcat.runtime)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit.junit5)
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
        events("failed")
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

tasks.jacocoTestCoverageVerification {
    description = "カバレッジの下限（行 80%・分岐 70%）を検証する。"
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
    description = "SpotBugs の報告を読み、重大度 High（priority 1）の指摘があれば失敗させる。それ未満は警告として表示する。"
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
        for (i in 0 until bugs.length) {
            val bug = bugs.item(i) as org.w3c.dom.Element
            val priority = bug.getAttribute("priority")
            val type = bug.getAttribute("type")
            val sourceLine = bug.getElementsByTagName("SourceLine").item(0) as org.w3c.dom.Element?
            val where = sourceLine?.let { "${it.getAttribute("sourcepath")}:${it.getAttribute("start")}" } ?: "?"
            if (priority == "1") {
                high++
                logger.error("SpotBugs [High] $type $where")
            } else {
                logger.warn("SpotBugs [warning, priority $priority] $type $where")
            }
        }
        if (high > 0) {
            throw GradleException("SpotBugs で重大度 High の指摘が $high 件あります（${xml.path}）。")
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
