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
// ルートのプロジェクト。バックエンド（Java）は backend サブプロジェクト、
// フロントエンド（React + TypeScript）は frontend ディレクトリを npm で扱う。
rootProject.name = "mastersmith"

dependencyResolutionManagement {
    // 依存関係の取得元は Maven Central だけにする。
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include("backend")

// メールのテンプレートの描画に使う自前の Mustache のエンジン java-mustache-processor（Apache License 2.0）。Maven Central に
// 無いため、Git サブモジュール vendor/java-mustache-processor（固定先のコミットで版を固める）を composite build で組み、
// backend の依存 cherry.mustache:cherry-mustache-core を、この取り込んだビルドの成果物で置き換える。
// 依存の取得元（上の Maven Central だけの決まり）は変えない。部品側のビルドのプラグインは Gradle Plugin Portal から取る
// （ビルドのときだけの道具で、WAR には入らない）。
// サブモジュールを取得していない（git clone に --recurse-submodules を付けていない・git submodule update --init をしていない）と、
// ここで取り込むビルドが見つからず、Gradle の構成の段階で失敗する。README の「取得と準備」を参照。
includeBuild("vendor/java-mustache-processor")
