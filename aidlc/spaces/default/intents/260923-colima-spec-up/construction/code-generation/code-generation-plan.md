# Code Generation の計画 — コンテナのメモリの上限と JVM の設定を環境変数で変えられるようにする（F3・F4）

## 対象と前提

- 対象: 単位の分割の無い不具合の修正（scope: bugfix、Test Strategy: Minimal）。成果物はこのディレクトリ（`aidlc/spaces/default/intents/260923-colima-spec-up/construction/code-generation/`）に置く。
- 入力: `aidlc/spaces/default/intents/260923-colima-spec-up/inception/requirements-analysis/requirements.md`（FR1〜FR7、NFR1〜NFR5）と、コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（特に `code-quality-assessment.md` の TD-6〜TD-11）。
- 直し方: アプリの Java のコード（`backend/src/main/java/`）は変えない（FR7.2）。変えるのは `Dockerfile`、2つの compose、文書、確かめの手順である。
- 作業の場所: 単位の分割が無いため、Bolt の作業ブランチは作らずに `develop` の上で作業する（前の Intent と同じ）。修正と、その確かめ（再発防止のテストに当たるもの）は、依頼者の承認を得て1つのコミットにまとめる（`project.md` の Mandated）。
- この段で行わないこと（段の分担）:
  - colima の VM の作り直し（FR1）、F3 の内訳の測定（FR4）、負荷の試験（FR5）は Build and Test の段で行う。VM の作り直しは、その段で実施の前に依頼者の確認を得る（FR1.2）。
  - この PC の `.env` の変更（FR2.2。メモリ 2g・CPU 4）は、Deployment Execution の段で依頼者の確認を得て行う。`.env` は秘密情報を含むため、この段では開かない。
  - この段の確かめは、今の VM（CPU 2・メモリ 2GiB）で動く小さな範囲（設定の展開と、JVM の起動の引数の効き方）に限る。

## 設計の要点

### メモリの上限（FR2.1）

- `compose.yaml` と `docker/perf/compose.yaml` の `mem_limit: 1g` を `${MASTERSMITH_CONTAINER_MEMORY:-1g}` にする。既存の `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}` と同じ書き方である。
- `compose.yaml` はプロジェクトのディレクトリの `.env` から値を読む。`docker/perf/compose.yaml` は `-f` で指定するため `.env` を読まず、CPU と同じくシェルの環境変数（`export`）で渡す（`perf/README.md` の手順に合わせる）。

### JVM の設定の口（FR3）

- 環境変数の名前は `MASTERSMITH_JAVA_OPTIONS` とする（既存の `MASTERSMITH_*` にそろえる。`JAVA_TOOL_OPTIONS` などの JVM 標準の変数は使わない）。
- 標準の変数を使わない理由は次の2つ。
  - `JAVA_TOOL_OPTIONS`・`JDK_JAVA_OPTIONS` は、コマンド行の引数より前に読まれる。そのため、`ENTRYPOINT` の `-XX:MaxRAMPercentage=75.0` に上書きされる（TD-8）。
  - どちらも起動の時に「Picked up ...」の1行を標準エラーに出し、1行1件の JSON のログの形を崩す。
- `Dockerfile` の `ENTRYPOINT` を、既定の引数のあとに `MASTERSMITH_JAVA_OPTIONS` を置く形に変える。例は次のとおりで、細部はこの段で決める。
  - `ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Tokyo ${MASTERSMITH_JAVA_OPTIONS:-} \"$@\" -jar /app/mastersmith.war", "mastersmith"]`
  - JVM の `-XX` の指定は後に書いたものが効くため、`MASTERSMITH_JAVA_OPTIONS` で割合などを上書きできる。
  - `exec` で java をコンテナの PID 1 にし、停止の合図（SIGTERM）を Java が直接受け取る今の動作を保つ（FR3.2）。
  - `"$@"`（`docker run` の引数）も同じ位置に渡し、確かめのときに `-XX:+PrintFlagsFinal -version` を付けて、アプリを起動せずに実際の値を読めるようにする。
- 環境変数を渡さないときは、今と同じ引数になる（ヒープは上限の 75%、タイムゾーン Asia/Tokyo）。
- 値は `.env` の `env_file`（配備）と、一時の `app.env`（負荷の試験環境）でコンテナに渡る。compose の変更は要らない。
- 値の区切りは空白（シェルの単語の分割）とする。空白を含む値は扱わない旨を README に書く。

### 確かめの手順（再発防止）

- アプリの Java のテストでは、コンテナと JVM の起動の設定を確かめられない。そのため、確かめのスクリプト `docker/check-container-limits.sh`（新規、Apache License 2.0 のヘッダーつき）を置く。
- スクリプトは次を確かめ、期待と違えば 0 以外で終わる。
  1. `docker compose config` で展開した `app` の `mem_limit` が、環境変数なしで 1g（1073741824）、`MASTERSMITH_CONTAINER_MEMORY=768m` で 768m になる（配備用と負荷の試験用の両方）
  2. イメージ `mastersmith:local` を、メモリの上限を付けて `-XX:+PrintFlagsFinal -version` の引数で起動し、`MaxHeapSize` が上限の 75% になる（環境変数なし）
  3. `MASTERSMITH_JAVA_OPTIONS='-XX:MaxRAMPercentage=60.0'` を渡すと、`MaxHeapSize` が上限の 60% になる。あわせて、ヒープ以外の上限の例（`-XX:MaxMetaspaceSize=128m`）が効く
  4. PID 1 が java であること（`exec` の確認）と、タイムゾーンの引数が残っていること
- 小さな上限（例: 512m）で JVM の `-version` だけを動かすため、今の VM（2GiB）で、配備したアプリを止めずに実行できる。
- これが、`project.md` の Mandated（不具合を再現するテストを同じコミットに含める）への、この段での対応である。負荷の試験による再現と修正の確認（FR5）は、Build and Test の段で行う（要件の「残る未確定の点」）。

## 変更するファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `Dockerfile` | `ENTRYPOINT` を `MASTERSMITH_JAVA_OPTIONS` と引数を受け取る形にする。コメントを直す | FR3.1、FR3.2 |
| `compose.yaml` | `mem_limit` を `${MASTERSMITH_CONTAINER_MEMORY:-1g}` にする。CPU とメモリのコメント、`lgtm` の「VM メモリ 約 2GiB」の前提のコメントを、新しい VM（CPU 4・メモリ 6GiB）に合わせて直す | FR2.1、FR6.3 |
| `docker/perf/compose.yaml` | `mem_limit` を同じ変数にする。「CPU 2 で測る」のコメントを直す | FR2.1、FR3.3 |
| `docker/check-container-limits.sh`（新規） | 上の「確かめの手順」 | FR2.1、FR3.1、FR3.2 |
| `README.md` | 21 行目と 232 行目の colima の例を `colima start --cpu 4 --memory 6` に統一する。VM の確かめ方（`colima list`・`docker info`）を書く。環境変数の表に `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_JAVA_OPTIONS` を加える。既定 1g のままでは F3 が再び起きうる既知の制約（負荷の目安と上限の上げ方）を書く。確かめのスクリプトの使い方を書く | FR1.3、FR6.1、FR6.2、FR6.5 |
| `.env.example` | コンテナの項目に2つの変数をコメントとして加える。CPU のコメント（「例: colima の既定の 2」）を直す | FR6.2 |
| `perf/README.md` | 一時の `app.env` と `export` の CPU 2 固定を、CPU 4・メモリ 2g にする。手順 0 の理由を CPU の取り合いに直す。末尾の F3 の注記は、FR5 の結果が出るまで「修正の前の結果」と明記して残し、Build and Test の段で結果に合わせて書き換える。NMT で内訳を測る方法の節を加える（FR4 の手順） | FR4.1、FR6.4 |

## 実行の手順

Testing Contract の方針は test-after（層ごとに実装してから、その層のテストを書いて実行する）である。今回の変更はアプリの層ではなく「Environment/build configuration」と「Documentation」に当たる。そのため、設定を変えてから確かめのスクリプトを書いて実行する順とする。

- [x] Step 1: 変更の前の基準を取る。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合）、失敗の数、カバレッジ（行・分岐）を記録する（`project.md` の Testing Posture: 実測の数字だけを報告する）。
- [x] Step 2: 修正の前の状態を記録する（再現）。今の `Dockerfile` のイメージで、`MASTERSMITH_JAVA_OPTIONS` に当たる指定（`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`）が効かず、`MaxHeapSize` が 75% のままであることを確かめる（TD-8 の実測）。`docker compose config` で `mem_limit` が環境変数で変わらないことも確かめる。結果を控える。
- [x] Step 3: `Dockerfile` の `ENTRYPOINT` を変える（FR3.1、FR3.2）。
- [x] Step 4: `compose.yaml` と `docker/perf/compose.yaml` の `mem_limit` とコメントを変える（FR2.1、FR6.3）。
- [x] Step 5: `./gradlew :backend:bootWar` と `docker compose build app` でイメージを作り直す。配備したアプリのコンテナは作り直さない（配備は Deployment Execution の段）。
- [x] Step 6: 確かめのスクリプト `docker/check-container-limits.sh` を書いて実行し、すべて通ることを確かめる。スクリプトが Step 2 の状態（直す前のイメージ）で失敗することも確かめる。たとえば、直す前の `Dockerfile` で作ったイメージを別のタグに残しておき、スクリプトのイメージのタグを変えて実行する。結果を控える。
- [x] Step 7: 文書を直す（README・`.env.example`・`perf/README.md`）（FR1.3、FR4.1 の手順、FR6.1〜FR6.5）。
- [x] Step 8: 統合の前の検査。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべて通ることを確かめる。確かめる点は、ライセンスヘッダーの検査（新しいスクリプトを含む）、カバレッジの下限（行 80%・分岐 70%）、Gitleaks である。あわせて、`git diff --stat -- backend/src/main/java` が空であること（FR7.2）を確かめる。
- [x] Step 9: 成果物を書く。
  - `code-summary.md`: 変更したファイル、Step 1・2・6・8 の実測の結果、判断、計画との差
  - `traceability.json`: FR・NFR の ID ごとの対象ファイル
  - `source-manifest.json`: 変更したパスの一覧
- [ ] Step 10: コミットを提案する。依頼者の承認を得て、修正・確かめのスクリプト・文書を1つのコミットにする（日本語のメッセージ）。AI は `git push` をしない。

## 要件との対応

| 要件 | 計画の Step | 確かめ方 |
|---|---|---|
| FR1.1・FR1.2 VM の作り直し | この段では行わない | Build and Test の段（依頼者の確認のうえで） |
| FR1.3 VM の手順を README に | Step 7 | 文書の差分 |
| FR2.1 メモリの上限の変数 | Step 4、6 | 確かめのスクリプトの 1 |
| FR2.2 この PC の `.env` | この段では行わない | Deployment Execution の段 |
| FR3.1・FR3.2 JVM の設定の口と既定の動作 | Step 2、3、6 | 確かめのスクリプトの 2〜4（直す前は失敗、直した後は成功） |
| FR3.3 負荷の試験環境でも同じ口 | Step 4、7 | 同じイメージと `app.env` で渡ることを `perf/README.md` に書く |
| FR4.1・FR4.2 F3 の内訳 | Step 7（手順の用意） | 測定は Build and Test の段 |
| FR5.1〜FR5.4 負荷の試験 | この段では行わない | Build and Test の段 |
| FR6.1〜FR6.5 文書 | Step 7 | 文書の差分 |
| FR7.1 検査が通る | Step 1、8 | `./gradlew verify` |
| FR7.2 Java のコードを変えない | Step 8 | `git diff --stat -- backend/src/main/java` が空 |
| NFR1・NFR2・NFR3 応答時間・停止なし・VM の見積もり | この段では行わない | Build and Test の段 |
| NFR4 品質の下限の維持 | Step 8 | JaCoCo の検証 |
| NFR5 秘密情報 | Step 7、8 | Gitleaks（`verify` に含まれる）、差分の確認、`.env` を開かない |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:338d79c16c40b1a3638ada91499f61136a06d4625c38fdf43e43c8bf0796f91a",
  "contract_sha256": "sha256:429c2982f88b93aada8f9b523a1c6f00d6755bca3b9fd5adab14330158afd89c"
}
```
