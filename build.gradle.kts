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
// ルートのビルド。1コマンドの検査（verify）とフロントエンドの npm の呼び出しを置く。

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless)
}

/** ライセンスヘッダーのひな形（config/license-header.txt）を `/* ... */` の形にしたもの。 */
val licenseHeaderBlock: String =
    file("config/license-header.txt")
        .readLines()
        .joinToString(separator = "\n", prefix = "/*\n", postfix = "\n */") { line ->
            if (line.isEmpty()) " *" else " * $line"
        }

// backend のビルドからも同じヘッダーを使う。
extra["licenseHeaderBlock"] = licenseHeaderBlock

// verify で backend のタスクの順番を決めるため、backend を先に評価する。
evaluationDependsOn(":backend")

spotless {
    // ルートと backend の Gradle の Kotlin DSL のファイル。ヘッダーの有無と形だけを確かめる。
    kotlinGradle {
        target("*.gradle.kts", "backend/*.gradle.kts")
        // 説明のコメントを残すため、ヘッダーでも空行でもない最初の行を区切りとする。
        licenseHeader(licenseHeaderBlock, "(?! \\*|/\\*|$)")
    }
}

// ---- 外部の道具とフロントエンド ----
// npm・Gitleaks・OSV-Scanner は PC に入っている実行ファイルを呼ぶ。見つからなければ入れ方を示して失敗させる（黙って飛ばさない）。

val frontendDir = layout.projectDirectory.dir("frontend")
val vendorDir = layout.projectDirectory.dir("vendor/make-you-chic-ui")

/** 道具の入れ方の案内（README の「前提の道具」と同じ内容）。 */
val toolGuides =
    mapOf(
        "node" to "Node.js 24 を入れてください（例: brew install node@24、または nvm install 24）。",
        "npm" to "Node.js 24 に付属する npm を使います（例: brew install node@24）。",
        "gitleaks" to "Gitleaks を入れてください（例: brew install gitleaks）。",
        "osv-scanner" to "OSV-Scanner を入れてください（例: brew install osv-scanner）。",
        "git" to "Git を入れてください。",
    )

/** 実行ファイルが PATH の上にあることを確かめる。無ければ入れ方を示して失敗させる。 */
fun requireTool(name: String) {
    val found =
        (System.getenv("PATH") ?: "").split(File.pathSeparator).any { dir -> File(dir, name).canExecute() }
    if (!found) {
        throw GradleException("$name が見つかりません。${toolGuides[name] ?: ""}")
    }
}

val checkToolchain =
    tasks.register("checkToolchain") {
        description = "Node.js 24 と npm・git があることを確かめる。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        doLast {
            listOf("node", "npm", "git").forEach(::requireTool)
            val version = providers.exec { commandLine("node", "--version") }.standardOutput.asText.get().trim()
            if (!version.startsWith("v24.")) {
                throw GradleException("Node.js 24 が必要です（現在: $version）。${toolGuides["node"]}")
            }
        }
    }

val vendorInstall =
    tasks.register<Exec>("vendorInstall") {
        description = "make-you-chic-ui（サブモジュール）の依存関係を lockfile どおりに入れる。"
        group = "build"
        dependsOn(checkToolchain)
        workingDir = vendorDir.asFile
        commandLine("npm", "ci", "--no-audit", "--no-fund")
        inputs.file(vendorDir.file("package-lock.json"))
        outputs.file(vendorDir.file("node_modules/.package-lock.json"))
    }

val vendorBuild =
    tasks.register<Exec>("vendorBuild") {
        description = "make-you-chic-ui をビルドする（packages/make-you-chic-ui/dist。組み込みガイドの手順 A）。"
        group = "build"
        dependsOn(vendorInstall)
        workingDir = vendorDir.asFile
        commandLine("npm", "run", "build")
        inputs.dir(vendorDir.dir("packages/make-you-chic-ui/src"))
        inputs.file(vendorDir.file("package-lock.json"))
        outputs.dir(vendorDir.dir("packages/make-you-chic-ui/dist"))
    }

val vendorUnchanged =
    tasks.register<Exec>("vendorUnchanged") {
        description = "サブモジュールの追跡されるファイルが変わっていないことを確かめる（vendor/ は変更しない）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(vendorBuild)
        val output = ByteArrayOutputStream()
        commandLine("git", "-C", vendorDir.asFile.path, "status", "--porcelain")
        standardOutput = output
        doLast {
            val changes = output.toString(Charsets.UTF_8).trim()
            if (changes.isNotEmpty()) {
                throw GradleException("vendor/make-you-chic-ui の追跡されるファイルが変わっています:\n$changes")
            }
        }
    }

val frontendInstall =
    tasks.register<Exec>("frontendInstall") {
        description = "画面の依存関係を lockfile どおりに入れる（npm ci）。"
        group = "build"
        dependsOn(checkToolchain)
        mustRunAfter(vendorBuild)
        workingDir = frontendDir.asFile
        commandLine("npm", "ci", "--no-audit", "--no-fund")
        inputs.file(frontendDir.file("package-lock.json"))
        outputs.file(frontendDir.file("node_modules/.package-lock.json"))
    }

/** frontend の npm のスクリプトを呼ぶタスクを作る。 */
fun npmScript(taskName: String, text: String, script: String, configure: Exec.() -> Unit = {}) =
    tasks.register<Exec>(taskName) {
        description = text
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(frontendInstall)
        workingDir = frontendDir.asFile
        commandLine("npm", "run", script)
        configure()
    }

val frontendFormatCheck = npmScript("frontendFormatCheck", "画面のフォーマットを確かめる（Prettier）。", "format:check")
val frontendLint = npmScript("frontendLint", "画面のリンタ（oxlint・ESLint）を実行する。", "lint")
val frontendLintCss = npmScript("frontendLintCss", "画面の CSS のリンタ（Stylelint）を実行する。", "lint:css")
val frontendLicenseCheck =
    npmScript("frontendLicenseCheck", "画面のファイルのライセンスヘッダーを確かめる。", "license:check")
val frontendTypecheck =
    npmScript("frontendTypecheck", "画面の型を検査する（tsc --noEmit）。", "typecheck") { dependsOn(vendorBuild) }
val frontendBuild =
    npmScript("frontendBuild", "画面をビルドする（frontend/dist）。実行可能 WAR に同梱する。", "build") {
        group = "build"
        dependsOn(vendorBuild)
        inputs.dir(frontendDir.dir("src"))
        inputs.files(
            frontendDir.file("index.html"),
            frontendDir.file("package.json"),
            frontendDir.file("package-lock.json"),
            frontendDir.file("vite.config.ts"),
        )
        outputs.dir(frontendDir.dir("dist"))
    }
val frontendTest = npmScript("frontendTest", "画面のテスト（Vitest）を実行する。", "test") { dependsOn(vendorBuild) }
val frontendCoverage =
    npmScript("frontendCoverage", "画面のカバレッジを測り、下限（行 80%・分岐 70%）を検証する。", "test:coverage") {
        dependsOn(vendorBuild)
    }
val frontendBundleSize =
    npmScript("frontendBundleSize", "初回の読み込みの JavaScript の量を測る（500KB を超えたら警告だけ）。", "bundle:size") {
        dependsOn(frontendBuild)
    }

// ---- 安全の検査 ----

val gitleaksScan =
    tasks.register<Exec>("gitleaksScan") {
        description = "リポジトリの履歴全体の秘密情報を Gitleaks で検出する（見つかれば失敗）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        doFirst { requireTool("gitleaks") }
        commandLine("gitleaks", "git", "--redact", "--no-banner", "--config", ".gitleaks.toml", "--exit-code", "1", ".")
    }

val osvReport = layout.buildDirectory.file("reports/osv-scanner/osv.json")
val npmBuildTools = layout.projectDirectory.file("config/npm-build-tools.txt")
val osvLockfiles =
    listOf("backend/gradle.lockfile", "frontend/package-lock.json", "vendor/make-you-chic-ui/package-lock.json")

/**
 * npm の lockfile から、パッケージ（名前と版）が実行時の依存関係かを判定する表を作る。
 * lockfile の各項目の dev・devOptional の印を使い、同じ名前と版の項目が1つでも実行時なら実行時とする。
 */
fun npmRuntimeTable(lockfile: File): Map<Pair<String, String>, Boolean> {
    @Suppress("UNCHECKED_CAST")
    val packages = (JsonSlurper().parse(lockfile) as Map<String, Any?>)["packages"] as Map<String, Map<String, Any?>>
    val table = mutableMapOf<Pair<String, String>, Boolean>()
    for ((path, entry) in packages) {
        val name = (entry["name"] as String?) ?: path.substringAfterLast("node_modules/")
        val version = entry["version"] as String? ?: continue
        val runtime = entry["dev"] != true && entry["devOptional"] != true
        val key = name to version
        table[key] = (table[key] ?: false) || runtime
    }
    return table
}

val osvScan =
    tasks.register<Exec>("osvScan") {
        description =
            "依存関係の脆弱性を OSV-Scanner で検査する（Gradle の lockfile・frontend と make-you-chic-ui の package-lock.json）。" +
                "判定の決まりは README の「依存関係の脆弱性の判定」を参照。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        val args = mutableListOf("osv-scanner", "scan", "source", "--format", "json")
        osvLockfiles.forEach { args += listOf("--lockfile", it) }
        commandLine(args)
        isIgnoreExitValue = true
        inputs.files(osvLockfiles, npmBuildTools)
        outputs.file(osvReport)
        doFirst {
            requireTool("osv-scanner")
            osvLockfiles.forEach { path ->
                if (!file(path).isFile) {
                    throw GradleException("検査の対象の lockfile がありません: $path")
                }
            }
            val report = osvReport.get().asFile
            report.parentFile.mkdirs()
            standardOutput = FileOutputStream(report)
        }
        doLast {
            val exitValue = executionResult.get().exitValue
            if (exitValue != 0 && exitValue != 1) {
                throw GradleException("OSV-Scanner の実行に失敗しました（終了コード $exitValue）。")
            }
            // 判定の決まり（依頼者の決定）:
            // - Gradle（バックエンド）: 重大度 High 以上（CVSS 7.0 以上）で失敗。
            // - npm の実行時の依存関係（make-you-chic-ui の実行時の依存関係を含む）: High 以上で失敗。
            // - npm の開発用の依存関係: 警告だけ。ただし、成果物を作る道具（config/npm-build-tools.txt）の High 以上は失敗。
            // - 悪意のあるパッケージ（OSV の ID が MAL- で始まる）: 重大度によらず失敗。
            // - 重大度が分からないもの: 警告（失敗させる条件に当たるものは上の決まりで判定する）。
            val buildTools =
                npmBuildTools.asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            fun isBuildTool(name: String) =
                buildTools.any { pattern ->
                    if (pattern.endsWith("*")) name.startsWith(pattern.dropLast(1)) else name == pattern
                }
            val runtimeTables =
                osvLockfiles.filter { it.endsWith("package-lock.json") }.associateWith { npmRuntimeTable(file(it)) }

            @Suppress("UNCHECKED_CAST")
            val root = JsonSlurper().parse(osvReport.get().asFile) as Map<String, Any?>
            val failures = mutableListOf<String>()
            var warnings = 0
            for (result in (root["results"] as List<Map<String, Any?>>?).orEmpty()) {
                val source = ((result["source"] as Map<String, Any?>)["path"] as String).replace('\\', '/')
                val lockfile = osvLockfiles.firstOrNull { source.endsWith(it) }
                    ?: throw GradleException("OSV-Scanner の結果の lockfile を判定できません: $source")
                for (pkg in (result["packages"] as List<Map<String, Any?>>?).orEmpty()) {
                    val info = pkg["package"] as Map<String, Any?>
                    val name = info["name"] as String
                    val version = info["version"] as String
                    val kind =
                        when {
                            lockfile.endsWith("gradle.lockfile") -> "Gradle"
                            // lockfile で見つからないものは、黙って飛ばさず実行時の依存関係として扱う。
                            runtimeTables.getValue(lockfile)[name to version] ?: true -> "npm 実行時"
                            isBuildTool(name) -> "npm 開発用（成果物を作る道具）"
                            else -> "npm 開発用"
                        }
                    for (group in (pkg["groups"] as List<Map<String, Any?>>?).orEmpty()) {
                        @Suppress("UNCHECKED_CAST")
                        val ids = (group["ids"] as List<String>?).orEmpty() + (group["aliases"] as List<String>?).orEmpty()
                        val severity = (group["max_severity"] as String?)?.toDoubleOrNull()
                        val high = severity != null && severity >= 7.0
                        val malicious = ids.any { it.startsWith("MAL-") }
                        val fatal = malicious || (high && kind != "npm 開発用")
                        val label = "[$kind, CVSS ${severity ?: "不明"}${if (malicious) ", 悪意のあるパッケージ" else ""}] " +
                            "${info["ecosystem"]} $name@$version ${group["ids"]} ($lockfile)"
                        if (fatal) {
                            failures += label
                            logger.error("OSV-Scanner [失敗] $label")
                        } else {
                            warnings++
                            logger.warn("OSV-Scanner [警告] $label")
                        }
                    }
                }
            }
            logger.lifecycle("OSV-Scanner: 失敗の条件に当たるもの ${failures.size} 件、警告 $warnings 件")
            if (failures.isNotEmpty()) {
                throw GradleException(
                    "依存関係の脆弱性の検査に失敗しました（${failures.size} 件。${osvReport.get().asFile}）:\n" +
                        failures.joinToString("\n"),
                )
            }
        }
    }

// ---- 1コマンドの検査（verify） ----
// 次の順に実行し、1つでも失敗したら後ろの段は実行しない。CI も同じタスクを呼ぶ。

val backendProject = project(":backend")

/** 検査の段（名前、説明、含めるタスク）。 */
val verifyStages: List<Triple<String, String, List<TaskProvider<*>>>> =
    listOf(
        Triple(
            "verifyPrepare",
            "0 準備（道具の確認、make-you-chic-ui のビルド、依存関係の取得）",
            listOf(checkToolchain, vendorInstall, vendorBuild, vendorUnchanged, frontendInstall),
        ),
        Triple(
            "verifyFormat",
            "1 フォーマット（Spotless・Prettier）",
            listOf(tasks.named("spotlessCheck"), backendProject.tasks.named("spotlessCheck"), frontendFormatCheck),
        ),
        Triple("verifyLint", "2 リンタ（oxlint・ESLint・Stylelint）", listOf(frontendLint, frontendLintCss)),
        Triple(
            "verifyLicense",
            "3 ライセンスヘッダー（画面は check-license-header.mjs。Java と Gradle の Kotlin DSL は、同じ Spotless の検査が 1 の段でヘッダーも確かめる）",
            listOf(frontendLicenseCheck),
        ),
        Triple(
            "verifyBuild",
            "4 ビルド（Java のコンパイル、tsc --noEmit、Vite のビルド）",
            listOf(
                backendProject.tasks.named("compileJava"),
                backendProject.tasks.named("compileTestJava"),
                frontendTypecheck,
                frontendBuild,
            ),
        ),
        Triple("verifyUnitTest", "5 単体テスト（JUnit・Vitest）", listOf(backendProject.tasks.named("test"), frontendTest)),
        Triple("verifyIntegrationTest", "6 結合テスト（組み込みの H2 と対象DB のコンテナ）", listOf(backendProject.tasks.named("integrationTest"))),
        Triple(
            "verifyCoverage",
            "7 カバレッジの下限（JaCoCo・@vitest/coverage-v8、行 80%・分岐 70%）",
            listOf(
                backendProject.tasks.named("jacocoTestReport"),
                backendProject.tasks.named("jacocoTestCoverageVerification"),
                frontendCoverage,
            ),
        ),
        Triple(
            "verifySecurity",
            "8 安全の検査（SpotBugs＋FindSecBugs、OSV-Scanner、Gitleaks。重大度 High 以上で失敗）",
            listOf(backendProject.tasks.named("spotbugsGate"), osvScan, gitleaksScan),
        ),
        Triple(
            "verifyArtifact",
            "9 成果物と量の確認（dist を同梱した実行可能 WAR、初回の読み込みの量）",
            listOf(backendProject.tasks.named("bootWar"), frontendBundleSize),
        ),
    )

val stageTasks =
    verifyStages.mapIndexed { index, (name, text, members) ->
        val earlier = verifyStages.take(index).flatMap { it.third }
        members.forEach { member -> member.configure { mustRunAfter(earlier) } }
        tasks.register(name) {
            description = text
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            dependsOn(members)
        }
    }

stageTasks.forEachIndexed { index, stage -> stage.configure { mustRunAfter(stageTasks.take(index)) } }

tasks.register("verify") {
    description = "統合の前の関門。フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → 安全の検査 → 成果物の順に実行する。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(stageTasks)
}

tasks.register<Exec>("e2eTest") {
    description =
        "ビルドした WAR を起動し、Playwright で画面を確かめる（./gradlew verify と CI には入れない。計画の P2 の決定）。" +
            "事前に npx playwright install chromium でブラウザを入れておく。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(":backend:bootWar")
    workingDir = frontendDir.asFile
    commandLine("npx", "playwright", "test", "e2e")
}
