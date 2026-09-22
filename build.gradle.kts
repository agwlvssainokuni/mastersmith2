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
