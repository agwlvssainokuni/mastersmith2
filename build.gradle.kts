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

spotless {
    // ルートと backend の Gradle の Kotlin DSL のファイル。ヘッダーの有無と形だけを確かめる。
    kotlinGradle {
        target("*.gradle.kts", "backend/*.gradle.kts")
        // 説明のコメントを残すため、ヘッダーでも空行でもない最初の行を区切りとする。
        licenseHeader(licenseHeaderBlock, "(?! \\*|/\\*|$)")
    }
}

// ---- フロントエンド ----
// npm は PC に入っている Node.js 24 の npm を呼ぶ。1コマンドの検査（verify）の組み立ては Step 19 で行う。

val frontendDir = layout.projectDirectory.dir("frontend")

tasks.register<Exec>("frontendBuild") {
    description = "画面をビルドする（frontend/dist）。実行可能 WAR に同梱する。"
    group = "build"
    workingDir = frontendDir.asFile
    commandLine("npm", "run", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.files(frontendDir.file("index.html"), frontendDir.file("package.json"), frontendDir.file("vite.config.ts"))
    outputs.dir(frontendDir.dir("dist"))
}

tasks.register<Exec>("e2eTest") {
    description =
        "ビルドした WAR を起動し、Playwright で画面を確かめる（./gradlew verify と CI には入れない。計画の P2 の決定）。" +
            "事前に npx playwright install chromium でブラウザを入れておく。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(":backend:bootWar")
    workingDir = frontendDir.asFile
    commandLine("npx", "playwright", "test", "e2e/u1-skeleton.e2e.ts")
}
