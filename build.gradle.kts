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

/**
 * ライセンスヘッダーのひな形を、メールのテンプレート（Mustache）のコメント `{{! ... }}` の形にしたもの（U1、BR2.5）。
 * HTML のコメントは送るメールに含まれ受け手に届くため使わない。Mustache のコメントは描いた本文に出ない。
 */
val mailTemplateLicenseHeader: String =
    file("config/license-header.txt")
        .readLines()
        .joinToString(separator = "\n", prefix = "{{!\n", postfix = "\n}}") { line ->
            if (line.isEmpty()) "" else "  $line"
        }

extra["mailTemplateLicenseHeader"] = mailTemplateLicenseHeader

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

val mustacheVendorDir = layout.projectDirectory.dir("vendor/java-mustache-processor")

val mustacheVendorUnchanged =
    tasks.register<Exec>("mustacheVendorUnchanged") {
        description =
            "java-mustache-processor（サブモジュール）の追跡されるファイルが変わっていないことを確かめる（project.md の Forbidden。" +
                "Intent 260925-user-management の U1 の計画の P3）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(checkToolchain)
        val output = ByteArrayOutputStream()
        commandLine("git", "-C", mustacheVendorDir.asFile.path, "status", "--porcelain")
        standardOutput = output
        doLast {
            val changes = output.toString(Charsets.UTF_8).trim()
            if (changes.isNotEmpty()) {
                throw GradleException("vendor/java-mustache-processor の追跡されるファイルが変わっています:\n$changes")
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
        // make-you-chic-ui の成果物（file: の依存で画面に同梱される）。固定先を変えると作り直す
        // （Intent 260925-user-management の B4 で、固定先の更新の後も UP-TO-DATE になる抜けを直した）。
        inputs.dir(vendorDir.dir("packages/make-you-chic-ui/dist"))
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

// ---- perf の伏せ字の道具（perf/redact-k6-evidence.mjs） ----
// 道具は frontend/ の外にあるため、frontend/package.json を変えずに、画面と同じ設定のファイルで Prettier と oxlint を呼ぶ
// （Intent 261029-reference-lookup-cleanup の U4。対象は道具のファイルだけで、ほかの perf/*.mjs には広げない）。
val perfRedactorTool = "perf/redact-k6-evidence.mjs"
val perfToolFormatCheck =
    tasks.register<Exec>("perfToolFormatCheck") {
        description = "perf の伏せ字の道具のフォーマットを確かめる（Prettier。画面と同じ設定）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(frontendInstall)
        commandLine(
            frontendDir.file("node_modules/.bin/prettier").asFile.absolutePath,
            "--check",
            "--config",
            "frontend/.prettierrc.json",
            perfRedactorTool,
        )
    }
val perfToolLint =
    tasks.register<Exec>("perfToolLint") {
        description = "perf の伏せ字の道具のリンタを実行する（oxlint。画面と同じ設定）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(frontendInstall)
        commandLine(
            frontendDir.file("node_modules/.bin/oxlint").asFile.absolutePath,
            "-c",
            "frontend/.oxlintrc.json",
            perfRedactorTool,
        )
    }

// ---- 安全の検査 ----

val gitleaksScan =
    tasks.register<Exec>("gitleaksScan") {
        description = "リポジトリの履歴全体の秘密情報を Gitleaks で検出する（見つかれば失敗）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        doFirst { requireTool("gitleaks") }
        commandLine("gitleaks", "git", "--redact", "--no-banner", "--config", ".gitleaks.toml", "--exit-code", "1", ".")
    }

val perfEvidenceScan =
    tasks.register<Exec>("perfEvidenceScan") {
        description = "aidlc/ の下の記録（evidence/）に JWT の形などが残っていないかを調べる（見つかれば失敗）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(checkToolchain)
        // 入力・出力を宣言しない（毎回流れる）。終わりのコード 0 以外（2 入力の誤り・4 検出など）で失敗する。
        commandLine("node", perfRedactorTool, "--scan", "aidlc")
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
            "0 準備（道具の確認、make-you-chic-ui のビルド、サブモジュールを変えていないことの確認、依存関係の取得）",
            listOf(checkToolchain, vendorInstall, vendorBuild, vendorUnchanged, mustacheVendorUnchanged, frontendInstall),
        ),
        Triple(
            "verifyFormat",
            "1 フォーマット（Spotless・Prettier。Prettier は画面と perf の伏せ字の道具）",
            listOf(
                tasks.named("spotlessCheck"),
                backendProject.tasks.named("spotlessCheck"),
                frontendFormatCheck,
                perfToolFormatCheck,
            ),
        ),
        Triple(
            "verifyLint",
            "2 リンタ（oxlint・ESLint・Stylelint。oxlint は画面と perf の伏せ字の道具）",
            listOf(frontendLint, frontendLintCss, perfToolLint),
        ),
        Triple(
            "verifyLicense",
            "3 ライセンスヘッダー（画面と perf/ の .mjs・.js は check-license-header.mjs。Java と Gradle の Kotlin DSL と" +
                "バックエンドのメールのテンプレート（Mustache のコメントの形）は、同じ Spotless の検査が 1 の段でヘッダーも確かめる）",
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
            "8 安全の検査（SpotBugs＋FindSecBugs・OSV-Scanner・Gitleaks は重大度 High 以上で失敗。" +
                "記録（evidence/）の JWT の形の検査は 1 件でも失敗）",
            listOf(backendProject.tasks.named("spotbugsGate"), osvScan, gitleaksScan, perfEvidenceScan),
        ),
        Triple(
            "verifyArtifact",
            "9 成果物と量の確認（dist を同梱した実行可能 WAR、WAR の中の DSL の JSON Schema と正本の一致、初回の読み込みの量）",
            listOf(backendProject.tasks.named("bootWar"), backendProject.tasks.named("verifyDslSchemaInWar"), frontendBundleSize),
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

/** 手元でメールを見る受け手（Mailpit）の API の URL。E2E の前提の確かめに使う（Intent 260925-user-management の U1）。 */
// 同じ場所を E2E の側の定数 MAILPIT_API_URL（frontend/e2e/support/mailpit.ts）も持つ。変えるときは両方をそろえる（U6）。
val mailpitInfoUrl = "http://127.0.0.1:8025/api/v1/info"

// ---- E2E の見本の PostgreSQL（Intent 261016-business-data-views の U3、基盤の設計 cicd-pipeline.md 3.2）----
// 業務データの代表の流れ（180）は、手元の見本の PostgreSQL（compose の targetdb-postgres。配備したアプリと共有）の中に、実行ごとの
// スキーマ e2e_biz_<印> と2つの一時のロール mastersmith_e2e_r_<印>・mastersmith_e2e_w_<印> を作って使い、終わったら消す。
// - 見本の PG は上乗せ docker/e2e/compose.targetdb-port.yaml で 127.0.0.1:${E2E_TARGETDB_PORT:-15432} に開けておく（e2eTest は
//   起動・停止しない）。管理者は docker compose exec でコンテナの中のローカルのソケットから入り、管理者のパスワードを読まない（Q3: A）。
// - 触るのは接頭辞 e2e_biz_ のスキーマと mastersmith_e2e_ のロールだけ。
// - 一時のロールのパスワードはこのビルドの中で乱数で作り、WAR と Playwright へは e2eTest のプロセスの環境変数だけで渡す
//   （ファイル・ログ・webServer.env に置かない）。片付けには名前だけを渡す（R-04）。
// - psql の誤りの出力（標準エラー）は、失敗した文に一時のパスワードが入りうるため Gradle のログに流さない。段の名前と SQLSTATE と
//   件数だけを出す（D8: A）。見本の PG のサーバーのログ（docker logs）には失敗した文が残りうる（2 時間で失効する使い捨ての値）。
// - docker の呼び出しは、このプロセスの環境変数（colima の DOCKER_HOST など）を引き継ぐ。

/** 見本の PG の公開の番号（上乗せの compose と同じ既定） */
val e2eTargetDbPort: String = providers.environmentVariable("E2E_TARGETDB_PORT").getOrElse("15432")

/** 見本の PG のサービス名と、管理者で入るときの共通の引数 */
val e2eTargetDbService = "targetdb-postgres"

/** この実行で作ったスキーマとロールの名前（準備のタスクが入れ、片付けのタスクが読む。パスワードは入れない） */
val e2eTargetDbRun: MutableMap<String, String> = mutableMapOf()

/** コマンドの結果（標準エラーは呼び出し側がログに出さずに SQLSTATE だけを取り出す） */
data class E2eCommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/** コマンドを流す（標準入力を渡せる。標準出力と標準エラーは別のスレッドで読む）。 */
fun runE2eCommand(
    command: List<String>,
    stdin: String? = null,
    extraEnv: Map<String, String> = emptyMap(),
    timeoutSeconds: Long = 120,
): E2eCommandResult {
    val builder = ProcessBuilder(command).directory(rootDir)
    builder.environment().putAll(extraEnv)
    val process = builder.start()
    val stdoutBuffer = ByteArrayOutputStream()
    val stderrBuffer = ByteArrayOutputStream()
    val stdoutReader = Thread { process.inputStream.use { it.copyTo(stdoutBuffer) } }
    val stderrReader = Thread { process.errorStream.use { it.copyTo(stderrBuffer) } }
    stdoutReader.start()
    stderrReader.start()
    process.outputStream.use { out -> if (stdin != null) out.write(stdin.toByteArray(Charsets.UTF_8)) }
    if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw GradleException("コマンドが ${timeoutSeconds} 秒で終わりませんでした: ${command.take(4).joinToString(" ")} …")
    }
    stdoutReader.join()
    stderrReader.join()
    return E2eCommandResult(process.exitValue(), stdoutBuffer.toString(Charsets.UTF_8), stderrBuffer.toString(Charsets.UTF_8))
}

/** 標準エラーから SQLSTATE（VERBOSITY sqlstate の形）だけを取り出す。値や文は返さない。 */
fun sqlStatesOf(stderr: String): List<String> =
    Regex("""(?:ERROR|FATAL|PANIC):\s+([0-9A-Z]{5})""").findAll(stderr).map { it.groupValues[1] }.toList()

/**
 * 見本の PG の中で、管理者（target_admin）がコンテナの中のソケットから SQL を流す。
 * 誤りは段の名前・SQLSTATE・件数だけを出して止める（標準エラーはログに出さない。D8: A）。
 */
fun runTargetDbAdminSql(stage: String, script: String, singleTransaction: Boolean = false): List<String> {
    val command =
        mutableListOf(
            "docker", "compose", "--profile", e2eTargetDbService, "exec", "-T", e2eTargetDbService,
            "psql", "-X", "-q", "-t", "-A", "-U", "target_admin", "-d", "business",
            "-v", "ON_ERROR_STOP=1", "-v", "VERBOSITY=sqlstate",
        )
    if (singleTransaction) {
        command.add("-1")
    }
    val result = runE2eCommand(command, stdin = script)
    if (result.exitCode != 0) {
        val states = sqlStatesOf(result.stderr)
        throw GradleException(
            "見本の PostgreSQL の「$stage」が失敗しました（終了コード ${result.exitCode}・誤りの件数 ${states.size}・" +
                "SQLSTATE ${states.distinct().ifEmpty { listOf("なし") }.joinToString(",")}）。psql の誤りの出力は、" +
                "一時のパスワードを含みうるため表示しません。README の「E2E の見本の PostgreSQL」の手順で確かめてください。",
        )
    }
    return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

/** `key=value` の行から数を読む */
fun countOf(lines: List<String>, key: String): Int =
    lines.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.toIntOrNull()
        ?: throw GradleException("見本の PostgreSQL の結果に $key がありません。")

/** 接頭辞のスキーマとロールを消す SQL（前の残りの片付け。名前は接頭辞で選ぶ） */
val e2eTargetDbPrefixCleanupSql =
    """
    SELECT 'schemas=' || count(*) FROM pg_namespace WHERE nspname LIKE 'e2e\_biz\_%';
    SELECT 'roles=' || count(*) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%';
    SELECT 'terminated=' || count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE usename LIKE 'mastersmith\_e2e\_%';
    SELECT format('DROP OWNED BY %I', rolname) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%' \gexec
    SELECT format('DROP ROLE %I', rolname) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%' \gexec
    SELECT format('DROP SCHEMA %I CASCADE', nspname) FROM pg_namespace WHERE nspname LIKE 'e2e\_biz\_%' \gexec
    SELECT 'remaining=' || ((SELECT count(*) FROM pg_namespace WHERE nspname LIKE 'e2e\_biz\_%')
        + (SELECT count(*) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%'));
    """.trimIndent() + "\n"

/** 見本の PG の前提の確かめ (a)〜(d)。満たさなければ起動・取り上げの手順を示して止める。 */
fun checkE2eTargetDb() {
    // (a) コンテナが動き、DB が応答するか（profile での起動）。
    val ready =
        try {
            runE2eCommand(
                listOf("docker", "compose", "--profile", e2eTargetDbService, "exec", "-T", e2eTargetDbService, "pg_isready", "-q"),
                timeoutSeconds = 30,
            ).exitCode == 0
        } catch (e: java.io.IOException) {
            logger.info("docker を呼べません: ${e.javaClass.name}")
            false
        }
    if (!ready) {
        throw GradleException(
            "見本の PostgreSQL（$e2eTargetDbService）が応答しません。E2E の前に " +
                "docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres " +
                "で起動してください（.env.targetdb が要ります。README の「E2E の見本の PostgreSQL」）。",
        )
    }
    // (b) PC の 127.0.0.1 の公開の番号に TCP で届くか（上乗せでの起動）。
    val tcpReachable =
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", e2eTargetDbPort.toInt()), 2000)
            }
            true
        } catch (e: java.io.IOException) {
            logger.info("見本の PostgreSQL の公開の番号に届きません: ${e.javaClass.name}")
            false
        }
    if (!tcpReachable) {
        throw GradleException(
            "見本の PostgreSQL が 127.0.0.1:$e2eTargetDbPort に開いていません。上乗せの compose で作り直してください: " +
                "docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres",
        )
    }
    // (c) PUBLIC に DB の TEMPORARY が残っていないか（一時の表で書き込みの権限の外を作れないようにする。Q6: A）。
    val publicTemp = runTargetDbAdminSql("TEMPORARY の確かめ", "SELECT 'temp=' || has_database_privilege('public', 'business', 'TEMP');\n")
    if (publicTemp.firstOrNull { it.startsWith("temp=") } != "temp=false") {
        throw GradleException(
            "見本の PostgreSQL の DB business で、PUBLIC に TEMPORARY が残っています。共有の見本の PG（配備したアプリも使う）を変える操作です。" +
                "docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1 " +
                "-c 'REVOKE TEMPORARY ON DATABASE business FROM PUBLIC' で取り上げてから流し直してください（README の U2 の手順と同じ）。",
        )
    }
    // (d) 公開の番号から来る接続が trust に当たらず、誤ったパスワードで拒まれるか（基盤の設計のレビュー R-02）。
    //     同じイメージ（同じ digest）の一時の psql を、docker の host のネットワークで流す。パスワードは乱数で、表示しない。
    //     確かめる経路は、docker の実行環境の側（colima では VM の中）の 127.0.0.1 の公開の番号で、PC の側の
    //     127.0.0.1:15432 から入る経路そのものではない。どちらも公開の番号の転送（bridge の gateway）から入るため、
    //     pg_hba.conf で当たる行（scram-sha-256）は同じ見込み。PC の側の経路は (b) の TCP の到達だけを確かめる
    //     （U3 のコードのレビューの R-08）。
    val containerId =
        runE2eCommand(listOf("docker", "compose", "--profile", e2eTargetDbService, "ps", "-q", e2eTargetDbService)).stdout.trim()
    val image = runE2eCommand(listOf("docker", "inspect", "--format", "{{.Config.Image}}", containerId)).stdout.trim()
    if (containerId.isEmpty() || !image.contains("@sha256:")) {
        throw GradleException("見本の PostgreSQL のイメージ（digest つき）を読めませんでした。compose.yaml の targetdb-postgres を確かめてください。")
    }
    val wrongPassword = java.util.HexFormat.of().formatHex(ByteArray(24).also { java.security.SecureRandom().nextBytes(it) })
    val probe =
        runE2eCommand(
            listOf(
                "docker", "run", "--rm", "--network", "host", "-e", "PGPASSWORD", "-e", "PGCONNECT_TIMEOUT=5", image,
                "psql", "-X", "-w", "-h", "127.0.0.1", "-p", e2eTargetDbPort, "-U", "target_admin", "-d", "business", "-tAc", "SELECT 1",
            ),
            extraEnv = mapOf("PGPASSWORD" to wrongPassword),
            timeoutSeconds = 60,
        )
    val rejected = probe.exitCode != 0 && probe.stderr.contains("password authentication failed")
    if (!rejected) {
        throw GradleException(
            "見本の PostgreSQL の公開の番号への誤ったパスワードの接続が拒まれませんでした（終了コード ${probe.exitCode}）。" +
                "trust に当たっているおそれがあるため、すぐに上乗せ無しで作り直してポートを閉じてください: " +
                "docker compose --profile targetdb-postgres up -d targetdb-postgres",
        )
    }
}

/** 前の実行の残り（接頭辞のスキーマとロール）を消す。消した件数だけをログに出す。 */
fun cleanupE2eTargetDbLeftovers() {
    val lines = runTargetDbAdminSql("前の残りの片付け", e2eTargetDbPrefixCleanupSql)
    val remaining = countOf(lines, "remaining")
    logger.lifecycle(
        "E2E の見本の PG: 前の残りを消しました（スキーマ ${countOf(lines, "schemas")}・ロール ${countOf(lines, "roles")}・" +
            "切った接続 ${countOf(lines, "terminated")}・残り $remaining）。",
    )
    if (remaining != 0) {
        throw GradleException("E2E の見本の PG の前の残りを消しきれませんでした（残り $remaining）。README の手で消す手順で消してください。")
    }
}

/** psql の \set に入れる値（英数字と _ と記号の限られた形だけを受ける。引用符で包む） */
fun psqlSetLine(name: String, value: String): String {
    require(Regex("""[0-9A-Za-z_:+\- ]+""").matches(value)) { "psql の変数 $name の値の形が違います。" }
    return "\\set $name '$value'\n"
}

/** 実行のスキーマ・表・架空の行・2つの一時のロール・付与を作り、WAR と Playwright へ渡す値を返す。 */
fun prepareE2eTargetDb(): Map<String, String> {
    val random = java.security.SecureRandom()
    val hex = java.util.HexFormat.of()
    val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(java.time.ZoneOffset.UTC)
        .format(java.time.Instant.now())
    val mark = "${stamp}_${hex.formatHex(ByteArray(4).also { random.nextBytes(it) })}"
    val schema = "e2e_biz_$mark"
    val reader = "mastersmith_e2e_r_$mark"
    val writer = "mastersmith_e2e_w_$mark"
    val readerPassword = hex.formatHex(ByteArray(24).also { random.nextBytes(it) })
    val writerPassword = hex.formatHex(ByteArray(24).also { random.nextBytes(it) })
    val until = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss+00").withZone(java.time.ZoneOffset.UTC)
        .format(java.time.Instant.now().plus(java.time.Duration.ofHours(2)))
    // 片付けには名前だけを渡す。準備が途中で失敗しても、片付けのタスクが名前で消す。
    e2eTargetDbRun["schema"] = schema
    e2eTargetDbRun["reader"] = reader
    e2eTargetDbRun["writer"] = writer
    val tableSql = frontendDir.file("e2e/support/businessDataSchema.sql").asFile.readText(Charsets.UTF_8)
    val script =
        psqlSetLine("schema", schema) + psqlSetLine("reader", reader) + psqlSetLine("writer", writer) +
            psqlSetLine("reader_pw", readerPassword) + psqlSetLine("writer_pw", writerPassword) + psqlSetLine("until", until) +
            "CREATE SCHEMA :\"schema\";\n" +
            tableSql + "\n" +
            """
            CREATE ROLE :"reader" LOGIN PASSWORD :'reader_pw' VALID UNTIL :'until';
            CREATE ROLE :"writer" LOGIN PASSWORD :'writer_pw' VALID UNTIL :'until';
            GRANT USAGE ON SCHEMA :"schema" TO :"reader", :"writer";
            GRANT SELECT ON ALL TABLES IN SCHEMA :"schema" TO :"reader";
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA :"schema" TO :"writer";
            GRANT USAGE ON ALL SEQUENCES IN SCHEMA :"schema" TO :"writer";
            SELECT 'rows=' || count(*) FROM :"schema".e2e_item;
            SELECT 'writerTemp=' || has_database_privilege(:'writer', 'business', 'TEMP');
            """.trimIndent() + "\n"
    val started = System.nanoTime()
    val lines = runTargetDbAdminSql("準備", script, singleTransaction = true)
    if (lines.firstOrNull { it.startsWith("writerTemp=") } != "writerTemp=false") {
        throw GradleException("E2E の書き込みのロールに TEMPORARY があります。PUBLIC の TEMPORARY を取り上げてから流し直してください。")
    }
    logger.lifecycle(
        "E2E の見本の PG: 実行のスキーマ $schema と一時のロール2つを作りました（行 ${countOf(lines, "rows")}・" +
            "${(System.nanoTime() - started) / 1_000_000} ms・ロールは $until まで有効）。",
    )
    return mapOf(
        "MASTERSMITH_TARGET_DB_TYPE" to "postgresql",
        "MASTERSMITH_TARGET_DB_HOST" to "127.0.0.1",
        "MASTERSMITH_TARGET_DB_PORT" to e2eTargetDbPort,
        "MASTERSMITH_TARGET_DB_DATABASE" to "business",
        "MASTERSMITH_TARGET_DB_SCHEMA" to schema,
        "MASTERSMITH_TARGET_DB_USERNAME" to reader,
        "MASTERSMITH_TARGET_DB_PASSWORD" to readerPassword,
        "MASTERSMITH_TARGET_DB_WRITE_USERNAME" to writer,
        "MASTERSMITH_TARGET_DB_WRITE_PASSWORD" to writerPassword,
        "E2E_BIZ_SCHEMA" to schema,
    )
}

/** この実行のスキーマとロールを消す片付け（e2eTest の finalizedBy。名前だけで消す）。 */
val e2eTargetDbCleanup =
    tasks.register("e2eTargetDbCleanup") {
        description = "E2E が見本の PostgreSQL に作った実行のスキーマと一時のロールを消す（e2eTest の後に必ず動く）。"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        doLast {
            val schema = e2eTargetDbRun["schema"]
            val reader = e2eTargetDbRun["reader"]
            val writer = e2eTargetDbRun["writer"]
            if (schema == null || reader == null || writer == null) {
                logger.lifecycle("E2E の見本の PG: この実行で作ったものは無いため、片付けは要りません。")
                return@doLast
            }
            val started = System.nanoTime()
            val script =
                psqlSetLine("schema", schema) + psqlSetLine("reader", reader) + psqlSetLine("writer", writer) +
                    """
                    SELECT 'terminated=' || count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE usename IN (:'reader', :'writer');
                    SELECT 'roles=' || count(*) FROM pg_roles WHERE rolname IN (:'reader', :'writer');
                    SELECT 'schemas=' || count(*) FROM pg_namespace WHERE nspname = :'schema';
                    SELECT format('DROP OWNED BY %I', rolname) FROM pg_roles WHERE rolname IN (:'reader', :'writer') \gexec
                    SELECT format('DROP ROLE %I', rolname) FROM pg_roles WHERE rolname IN (:'reader', :'writer') \gexec
                    SELECT format('DROP SCHEMA %I CASCADE', nspname) FROM pg_namespace WHERE nspname = :'schema' \gexec
                    SELECT 'remaining=' || ((SELECT count(*) FROM pg_namespace WHERE nspname LIKE 'e2e\_biz\_%')
                        + (SELECT count(*) FROM pg_roles WHERE rolname LIKE 'mastersmith\_e2e\_%'));
                    """.trimIndent() + "\n"
            val lines = runTargetDbAdminSql("片付け", script)
            e2eTargetDbRun.clear()
            val remaining = countOf(lines, "remaining")
            logger.lifecycle(
                "E2E の見本の PG: 片付けました（スキーマ ${countOf(lines, "schemas")}・ロール ${countOf(lines, "roles")}・" +
                    "切った接続 ${countOf(lines, "terminated")}・${(System.nanoTime() - started) / 1_000_000} ms・" +
                    "接頭辞の残り $remaining）。",
            )
            if (remaining != 0) {
                throw GradleException("E2E の見本の PG に接頭辞のスキーマかロールが残っています（$remaining 件）。README の手で消す手順で消してください。")
            }
        }
    }

/** 流す E2E のファイル（-Pe2e.files=e2e/180-….e2e.ts,e2e/185-….e2e.ts。無ければ e2e の全件） */
val e2eFiles: List<String> =
    providers.gradleProperty("e2e.files").orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.ifEmpty { null }
        ?: listOf("e2e")

tasks.register<Exec>("e2eTest") {
    description =
        "ビルドした WAR を起動し、Playwright で画面を確かめる（./gradlew verify と CI には入れない。計画の P2 の決定）。" +
            "事前に npx playwright install chromium でブラウザを入れ、docker compose --profile mail up -d mailpit で" +
            "メールの受け手（Mailpit）を、上乗せの compose で見本の PostgreSQL を起動しておく。" +
            "ファイルを選ぶときは -Pe2e.files=e2e/180-business-data-flow.e2e.ts（カンマで区切って複数）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(":backend:bootWar")
    finalizedBy(e2eTargetDbCleanup)
    doFirst {
        // E2E はメールを手元の受け手（Mailpit）へ送る。届かなければ起動の手順を示して失敗させる（黙って飛ばさない。
        // 基盤の設計の Q1 A）。e2eTest は Mailpit を起動・停止しない。
        val reachable =
            try {
                val connection = java.net.URI(mailpitInfoUrl).toURL().openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                try {
                    connection.responseCode == 200
                } finally {
                    connection.disconnect()
                }
            } catch (e: java.io.IOException) {
                logger.info("Mailpit に届きません: ${e.javaClass.name}")
                false
            }
        if (!reachable) {
            throw GradleException(
                "メールの受け手（Mailpit、$mailpitInfoUrl）に届きません。E2E の前に " +
                    "docker compose --profile mail up -d mailpit で起動してください（README の「メール（U1）」）。",
            )
        }
        // 業務データの E2E は見本の PostgreSQL を前提にする（team.md の Testing Posture。届かなければ手順を示して止める）。
        checkE2eTargetDb()
        cleanupE2eTargetDbLeftovers()
        // 値は Exec の環境変数だけで渡す（WAR は Playwright の webServer がこのプロセスの環境変数を引き継いで起動する）。
        environment(prepareE2eTargetDb())
    }
    workingDir = frontendDir.asFile
    commandLine(listOf("npx", "playwright", "test") + e2eFiles)
}
