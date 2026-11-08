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
    // メールの描画と送信（Intent 260925-user-management の U1）。送信の部品は Spring Boot のメールの自動設定で作る。
    // テンプレートの描画は自前の Mustache のエンジン（composite build。settings.gradle.kts を参照）。
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.cherry.mustache.core)
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
    // メールの送信の結合テストで、JVM の中で起動するテスト用の SMTP の受け手（U1。コンテナは使わない）。
    testImplementation(libs.subethasmtp)
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
    // テストの JVM のヒープの上限（Gradle の既定は 512MB）。構造の検査（ArchUnit）の各クラスがアプリのクラスの読み込みの結果を
    // 静的に持ち続けるため、クラスが増えると（Intent 260923-dsl-schema-loader の U4）、10MB を超える DSL を作る U3 のテストで
    // ヒープが尽きた。テストの件数・カバレッジの下限は変えていない。
    maxHeapSize = "1g"
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    // 内部DB（H2 2.4.240）の閉じるときの全体の詰め直しのスレッドを 1 本に固定する（本番の Dockerfile の ENTRYPOINT と同じ）。
    // 並べて行う（CPU の数 / 4 本）と H2 の中の競合に当たり、assert が有効なテストの JVM では詰め直しが中断されてファイルが縮まない
    // （H2CompactionByPoolSuspensionIT が CPU 8 の PC で落ちた）。assert は切らない。H2 を上げるときは、この指定が要るかを見直す。
    systemProperty("h2.compactThreads", "1")
    systemProperty("user.timezone", "Asia/Tokyo")
    // 結合テストで Host ヘッダーを指定して、エラー応答の type の URL の組み立てを確かめるため。
    systemProperty("jdk.httpclient.allowRestrictedHeaders", "host")
    // テストのアプリ（Tomcat の RANDOM_PORT）を IPv4 の全アドレスで待ち受けさせる（Intent 260925-storage-memory-fixes の FR3）。
    // colima は Testcontainers のコンテナの番号を PC の IPv4 の全アドレスで転送するが、Java の既定（IPv6 の全アドレス）では macOS で
    // 同じ番号を重ねて取れてしまい、localhost への要求がコンテナへ届いて切られる（AccessTokenApiIT の一時的な失敗と同じ文言）。
    // IPv4 では重ねて取れない（LoopbackPortCollisionTest）。
    systemProperty("java.net.preferIPv4Stack", "true")
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
    // 単体テストの JVM だけヒープの上限を 1.5g（1536m。Gradle は小数の表記を読めない）にする（結合テストは上の共通の 1g の
    // まま）。構造の検査の各クラスが読み込みの結果を静的に持ち続けるため、Intent 261004-role-menu の B7 で navigation の本体の
    // クラスが増えると、大きな YAML を読む RoleTransferReaderTest でヒープが尽きた（依頼者の決定 A）。テストの件数・カバレッジの
    // 下限は変えていない。
    maxHeapSize = "1536m"
    filter {
        includeTestsMatching("*Test")
    }
}

// ---- 結合テストの絞り込みと並べ（Intent 261107-verify-quick-local の FR2・FR4.2） ----
// -PitTests=<形>[,<形>...]: 結合テストを、当たるクラスだけに絞る（Gradle のテストの絞り込みと同じワイルドカード）。統合の前の入口
// verifyQuick に付けたときだけ、結合テストが 6 の段の位置に入る（ルートの build.gradle.kts）。verify に付けると失敗にする（ルート）。
// -PitForks=<N>: 結合テストを N 個の JVM に分けて並べて流す。付けないときは今までどおり1つの JVM で流す。
// どちらも付けないときの integrationTest の設定は、この Intent の前と同じ。

/** -PitTests の値をカンマで分けた形の一覧。付けていなければ null。空・空白・カンマだけの値は、何も流れないまま通らないよう失敗にする。 */
val itTestPatterns: List<String>? =
    providers.gradleProperty("itTests").orNull?.let { raw ->
        val patterns = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty()) {
            throw GradleException(
                "-PitTests に結合テストのクラスの形がありません（値: '$raw'）。" +
                    "例: -PitTests=AuditTraceIdIT、複数はカンマで区切る（-PitTests=AuditTraceIdIT,*LoginIT）。",
            )
        }
        patterns
    }

/** -PitForks の値（結合テストを並べる JVM の数）。付けていなければ null。1 以上の整数でなければ失敗にする。 */
val itForks: Int? =
    providers.gradleProperty("itForks").orNull?.let { raw ->
        raw.trim().toIntOrNull()?.takeIf { it >= 1 }
            ?: throw GradleException("-PitForks は 1 以上の整数で指定してください（値: '$raw'）。例: -PitForks=2")
    }

val integrationTest = tasks.register<Test>("integrationTest") {
    description =
        "結合テスト（*IT、Spring と組み込みの H2 を起動する）を実行する。-PitTests=<形> で当たるクラスだけに絞り、" +
            "-PitForks=<N> で N 個の JVM に分けて並べる。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        if (itTestPatterns == null) {
            includeTestsMatching("*IT")
        } else {
            itTestPatterns.forEach { includeTestsMatching(it) }
            // 当たるテストが1つも無ければ失敗にする（Gradle の既定と同じ値を明示する。打ち間違いで何も流れないまま通らないように）。
            isFailOnNoMatchingTests = true
        }
    }
    if (itTestPatterns != null) {
        // 絞り込みの形が単体テスト（*Test）に当たっても流さないよう、読むクラスのファイルを結合テスト（入れ子のクラスを含む）に限る。
        include("**/*IT.class", "**/*IT$*.class")
        // 絞った結合テストでは、コンテナの実行環境が無いときに対象DB のテストを飛ばさず失敗にする（CI と同じ扱い。ContainerRuntimeCheck）。
        // verifyQuick はバックエンドのカバレッジの下限を判定せず、飛ばしても失敗にならないため（コードのレビューの R-01）。
        systemProperty("mastersmith.requireContainerRuntime", "true")
    }
    if (itForks != null) {
        maxParallelForks = itForks
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

// ---- 統合の前の入口 verifyQuick のカバレッジの参考値（Intent 261107-verify-quick-local の FR3） ----
// 単体テストの実行記録（test.exec）だけから報告を作り、全体の行・分岐の割合を参考値として表示する。下限の判定はしない。
// -PitTests で流した結合テストの記録も、前の回の verify が残した integrationTest.exec も読まない。報告は verify の報告
// （reports/jacoco/test/）を上書きしないよう、別の置き場（reports/jacoco/quick/）に作る。test への依存は付けず、順だけを決める
// （verify の jacocoTestReport と同じ）。test.exec が無ければ、JaCoCo の既定どおり飛ばされる。

val quickReportDir = layout.buildDirectory.dir("reports/jacoco/quick")

tasks.register<JacocoReport>("jacocoQuickReport") {
    description = "単体テストの実行記録（test.exec）だけからカバレッジの報告を作り、参考値を表示する（下限の判定はしない。verifyQuick 用）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    mustRunAfter(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(sourceSets.main.get().allJava.srcDirs)
    reports {
        xml.required = true
        xml.outputLocation = quickReportDir.map { it.file("jacocoQuickReport.xml") }
        html.required = true
        html.outputLocation = quickReportDir.map { it.dir("html") }
    }
    // 参考値の表示は報告を作った後の処理で行うため、UP-TO-DATE・キャッシュで飛ばさず、毎回作り直して表示する（数秒）。
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    val xmlReport = reports.xml.outputLocation
    doLast {
        val xml = xmlReport.get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        // JaCoCo の XML は DOCTYPE で report.dtd を指すが、読みに行かない（外部の参照を読まない）。
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val root = factory.newDocumentBuilder().parse(xml).documentElement
        // 全体の値は、根（report）の直下の counter にある（パッケージ・クラスの counter は数えない）。
        val totals = mutableMapOf<String, Pair<Long, Long>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Element && node.tagName == "counter") {
                totals[node.getAttribute("type")] =
                    node.getAttribute("covered").toLong() to node.getAttribute("missed").toLong()
            }
        }
        fun ratio(type: String): String {
            val (covered, missed) = totals[type] ?: return "$type の数がありません"
            val total = covered + missed
            if (total == 0L) return "0 / 0"
            return "%.1f%%（%d / %d）".format(covered * 100.0 / total, covered, total)
        }
        logger.lifecycle(
            "JaCoCo の参考値（単体テストの記録 test.exec だけ。下限の判定はしない）: 行 ${ratio("LINE")}、分岐 ${ratio("BRANCH")}。" +
                "報告: ${quickReportDir.get().asFile.path}。下限（行 80%・分岐 70%）の判定は、全件の ./gradlew verify と CI で行う。",
        )
    }
}

/**
 * パッケージごとの下限を当てない既存のパッケージ（全体の合計で判定する）。U1（Intent 260923-dsl-schema-loader の最初の Bolt）で
 * 既存のパッケージを実測したところ、単独で下限を下回るもの（audit.service の行 77.2%、common.health の行 79.2%、
 * auth.repository の分岐 50.0%）があったため、team.md の Testing Posture に従い、パッケージごとの下限は新しく作るパッケージ
 * だけに当てる。この一覧は増やさない（新しく作るパッケージは、一覧に無いので自動で下限の対象になる）。
 *
 * 手を入れた Bolt で、そのパッケージの下限を満たして一覧から外し、パッケージごとの下限の対象に戻す（一度外したものは戻さない）。
 * Intent 260925-user-management の B2（U2）で audit.domain・audit.service・auth.service・auth.web・user.domain・
 * user.repository・user.service を外した。
 * Intent 260925-user-management の U8 で common.security・config・access.web を外した（差し込み口の order の説明文の書き直し）。
 * Intent 260930-user-admin の B1（U1）で auth.domain・auth.repository・access.domain を外した（利用停止の区分・伏せ字・まとめての
 * 無効化）。
 * Intent 260930-user-admin の B4（U3）で common.error.web・common.observability を外した（行の排他の失敗の連なりを、追跡と想定外の
 * 誤りのログでクラスの名前だけにする中央の手当て）。
 */
val packagesJudgedByTotal = listOf(
    "cherry.mastersmith.access.service",
    "cherry.mastersmith.audit.repository",
    "cherry.mastersmith.common.error.domain",
    "cherry.mastersmith.common.error.service",
    "cherry.mastersmith.common.health",
    "cherry.mastersmith.common.i18n.domain",
    "cherry.mastersmith.common.web",
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
    // メールのテンプレート（U1、BR2.5）。ヘッダーは Mustache のコメント `{{! ... }}` の形で、ヘッダーの後の最初の行
    // （<!DOCTYPE）を区切りとする。本番の置き場（B1 では0件）とテスト用の置き場を対象にする。
    format("mailTemplates") {
        target(
            "src/main/resources/mail/templates/*.html",
            "src/test/resources/mail/test-templates/**/*.html",
            "src/test/resources/mail/test-templates-invalid/**/*.html",
        )
        licenseHeader(rootProject.extra["mailTemplateLicenseHeader"] as String, "<!DOCTYPE")
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
        "SpotBugs の報告を読み、重大度 High（priority 1）の指摘と、SQL インジェクション系（パターン名が SQL_ で始まる）・予測できる" +
            "乱数（PREDICTABLE_RANDOM）・メールのヘッダーへの差し込み（SMTP_HEADER_INJECTION）の指摘があれば priority によらず" +
            "失敗させる。それ以外は警告として表示する。"
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
        var always = 0
        // priority によらず止めるパターン（team.md の Code Style、Intent 260925-user-management の U1 の NFR2.8・NFR2.9）。
        val alwaysFatal = setOf("PREDICTABLE_RANDOM", "SMTP_HEADER_INJECTION")
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
                type in alwaysFatal -> {
                    always++
                    logger.error("SpotBugs [$type, priority $priority] $where")
                }
                priority == "1" -> {
                    high++
                    logger.error("SpotBugs [High] $type $where")
                }
                else -> logger.warn("SpotBugs [warning, priority $priority] $type $where")
            }
        }
        if (high > 0 || sql > 0 || always > 0) {
            throw GradleException(
                "SpotBugs で統合を止める指摘があります（重大度 High $high 件、SQL インジェクション系 $sql 件、" +
                    "PREDICTABLE_RANDOM・SMTP_HEADER_INJECTION $always 件。${xml.path}）。",
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

// ---- DSL の JSON Schema の公開（U2。Intent 261004-role-menu の U2 dsl-v2 で書式の版 2 にした） ----
// 正本は src/main/resources/dsl/dsl-schema-v2.json の1つ（検証もこれを読む）。画面の静的なファイルの置き場（static/dsl/）へ
// ビルドで複写し、ログインなしの /dsl/dsl-schema-v2.json で配る。WAR には WEB-INF/classes/static/dsl/ として入る。

val dslSchemaSource = layout.projectDirectory.file("src/main/resources/dsl/dsl-schema-v2.json")

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
            for (entryName in listOf("WEB-INF/classes/dsl/dsl-schema-v2.json", "WEB-INF/classes/static/dsl/dsl-schema-v2.json")) {
                val entry = zip.getEntry(entryName) ?: throw GradleException("WAR に $entryName がありません。")
                val actual = zip.getInputStream(entry).use { it.readBytes() }
                if (!actual.contentEquals(expected)) {
                    throw GradleException("WAR の $entryName が正本（${dslSchemaSource.asFile.path}）と違います。")
                }
            }
        }
    }
}

// frontend/public/ の素材（アイコン・favicon）が WAR の static/ に同じ内容で入っていることを確かめる（Intent 261102-timezone-ui-polish の U6）。
// 素材が WAR に入らないと、/favicon.ico は 404 でなく index.html（HTML）を 200 で返し、サーバーは気づかない。
// 素材の名前は書かず、public/ の下のすべてのファイル（再帰）を比べる。public/ が空でも失敗する。
val faviconSourceDir: Directory = rootProject.layout.projectDirectory.dir("frontend/public")

tasks.register("verifyFaviconInWar") {
    description = "実行可能 WAR の static/ に、frontend/public/ の素材がすべて同じ内容で入っていることを確かめる。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.bootWar)
    val war = tasks.bootWar.flatMap { it.archiveFile }
    inputs.file(war)
    inputs.dir(faviconSourceDir)
    doLast {
        val root = faviconSourceDir.asFile
        val sources = root.walkTopDown().filter { it.isFile }.toList()
        if (sources.isEmpty()) {
            throw GradleException("${root.path} が空です。素材（アイコン・favicon）を置いてください。")
        }
        ZipFile(war.get().asFile).use { zip ->
            for (source in sources) {
                val relative = source.relativeTo(root).invariantSeparatorsPath
                val entryName = "WEB-INF/classes/static/$relative"
                val entry = zip.getEntry(entryName) ?: throw GradleException("WAR に $entryName がありません。")
                val actual = zip.getInputStream(entry).use { it.readBytes() }
                if (!actual.contentEquals(source.readBytes())) {
                    throw GradleException("WAR の $entryName が ${source.path} と違います。")
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
