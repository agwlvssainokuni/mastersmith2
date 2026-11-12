# テストの結果（Intent 261110-deps-forks-review）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 手元の全件の `verify`

- コマンド: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）
- コミット: `c07dfbb7`（作業ブランチ `fix/261110-comments`。R-01 のコメントの直しを含む。`develop` へは `e566faa4` として squash で統合）
- 結果: 終了コード 0、BUILD SUCCESSFUL、14分36秒（2026-11-11 23:23:18〜23:37:55）。走っている間の眠りは無い。

| 種類 | 件数 | 通った | 失敗 | 飛ばした |
|---|---:|---:|---:|---:|
| バックエンドの単体テスト | 2,939 | 2,939 | 0 | 0 |
| バックエンドの結合テスト | 1,716 | 1,706 | 0 | 10（方言の飛ばし。前と同じ） |
| 画面のテスト（Vitest、計測なしと計測ありの2回） | 3,063 × 2 | 3,063 × 2 | 0 | 0 |

- 件数は前の Intent（261108-ci-checks-release）と同じ。この Intent はテストを足していない（計画どおり）。
- バックエンドのカバレッジ: 行 97.75%・分岐 91.52%。`jacocoTestCoverageVerification` が通った（全体と、71 パッケージすべてのパッケージごとの下限）。最も低いのは `config` の分岐 76.7%、次が `targetdb.service` の分岐 77.1%（どちらも下限 70% の内）。
- 画面のカバレッジ: 行 95.49%・分岐 91%（`thresholds` の判定が通った）。
- OSV-Scanner（手元は Homebrew の 2.7.0）: High 以上の指摘なし。

## 2. E2E 全体

- コマンド: `caffeinate -dims ./gradlew e2eTest`（Mailpit と、E2E 用のポートを開けた見本の PostgreSQL を起動して）。コミットは 1節と同じ `c07dfbb7`。
- 結果: 298 件すべて通った（15.9 分、23:37:56〜23:54:04。眠りは無い）。axe の違反 0（`KNOWN_VIOLATIONS` はどれも空）。
- 報告の json の検索で、残してはならない値（13 種類）は 0 件。見本の PostgreSQL のスキーマとロールは片付け、ポートを外して作り直した。
- E2E の WAR の JVM の `-Duser.timezone=Asia/Tokyo`（FR2.5）を足した後の全件の通過は、Code Generation の ①・② の後と、この回の3回目。

## 3. CI

### 3.1 `-PitForks` の比べ（FR4.1〜FR4.3）

依頼者が `develop`（`e566faa4`）をプッシュした後、B5 の承認を得て、`gh workflow run ci.yml --ref develop -f it_forks=<N>` を 2・3・2・3・2・3 の順に、1回終わるごとに次を起こした。台本は起こす前に、`develop` の先頭が `e566faa4` のままかを毎回確かめた。6回とも `headSha` は `e566faa4` で、比べの間に `develop` へのプッシュは無かった（FR4.1a）。

| 回 | N | 実行 | 検査の job | うち `./gradlew verify` の手順 | 結論 | 試行 |
|---|---|---|---|---|---|---|
| 1 | 2 | 43059136421 | 23分28秒 | 21分44秒 | success | 1 |
| 2 | 3 | 43063129362 | 27分07秒 | 26分05秒 | success | 1 |
| 3 | 2 | 43067785167 | 28分40秒 | 27分36秒 | success | 1 |
| 4 | 3 | 43074723292 | 21分32秒 | 20分47秒 | success | 1 |
| 5 | 2 | 43078240581 | 28分52秒 | 28分08秒 | success | 1 |
| 6 | 3 | 43082808332 | 21分12秒 | 20分30秒 | success | 1 |

| N | 検査の job の中央値 | `verify` の手順の中央値 | 最も遅い回 |
|---|---|---|---|
| 2 | 28分40秒 | 27分36秒 | 28分52秒 |
| 3 | **21分32秒** | 20分47秒 | 27分07秒 |

- 決め方（FR4.3）: 中央値が短い方。差は 7分08秒で 1 分以上のため、**N=3** に決めた。失敗・不安定なテストは出ていない（6回とも試行 1 で success）。選んだ値の最も遅い回は 27分07秒で、42 分の線の内。
- 時間は run の全体ではなく、検査の job の始めから終わりで測った（`project.md` の Deployment）。
- 回 3 と回 4 の間に約 15 分の空きがある（回 3 の終わり 17:52、回 4 の起動 18:07）。この間に `develop` の ci.yml の別の実行は無く、原因は確かめていない（台本の待ちの側と見ている）。各回の時間は job の始めから測るため、比べの値には響かない。

### 3.2 プッシュの CI（参考。比べの6回には数えない、FR4.2）

| コミット | 実行 | 検査の job | `verify` の手順 | cloc を入れる手順 | 結論 | 試行 |
|---|---|---|---|---|---|---|
| `e566faa4`（既定の 3） | 43045374225 | **46分41秒** | 24分26秒 | **21分24秒** | success | 1 |
| `a65fb5af`（既定の 3。FR4.4 の直しを含む） | 43250128922 | （プッシュの CI の終わりを待って記入） | | | | |

- `e566faa4` の検査の job が 42 分の線を超えた。原因はテストではなく、報告用の手順 `規模を数える道具（cloc）を入れる`（`sudo apt-get update && sudo apt-get install -y cloc`）が 21分24秒かかったこと。比べの6回では、同じ手順は 10秒〜1分12秒だった。GitHub のホストの実行環境から apt の配布元への取得が遅かったと見ている（確かめていない見立て）。
- 依頼者の決定 B6（B 記録だけ）で、`ci.yml` の手順は変えず、ここに記録した。この手順は `continue-on-error: true` のため、遅れても CI は失敗にならない。テストの分け方の問題ではないため、NFR5 の「テストの分け方を諮る」には当たらないと扱った。
- `b4c8d77e` のプッシュの CI（43038873152）は、`e566faa4` のプッシュで待ちの実行が入れ替わり、cancelled になった（`concurrency` の決まりによる。失敗ではない）。
- OSV-Scanner 2.7.1（CI の版。版と SHA-256 を ci.yml で固定）: `e566faa4` のプッシュの CI と比べの6回のすべてで `verify` が通り、High 以上の指摘なし（Code Generation のレビュー R-02 の確かめ）。

### 3.3 既定の値の直し（FR4.4・FR4.5）

- `IT_FORKS_DEFAULT` は既に `"3"` のため値は変えず、`.github/workflows/ci.yml` のコメントと `docs/verification.md` の `-PitForks` の節に比べの結果を書いた（`c59185b9`。`verifyQuick` が通った後に squash で統合）。手元の推奨（N=3）は変えていない。
- FR4.5（既定の値を変えたコミットのプッシュの CI の記録）は、値を変えていないため当たらない。参考として、直しを含む `a65fb5af` のプッシュの CI を 3.2節に記録した。

## 4. 手元の監視の確かめ（B3: A）

- `docker compose --profile monitoring --profile observability up -d lgtm otel-collector` で一時的に起動した（23:54:42〜23:57:38）。
- Grafana の health が 200（Grafana 13.3.0、database ok）。ダッシュボード 4（`JVM Overview (OpenTelemetry)`・`MasterSmith の概要`・`RED Metrics (classic histogram)`・`RED Metrics (native histogram)`）、警報の決まり 17、データの元は loki・prometheus・pyroscope・tempo。
- 動いたコンテナの版とダイジェストは `compose.yaml` と一致した（`grafana/otel-lgtm:0.40.0@sha256:72e05804…`・`otel/opentelemetry-collector:0.165.0@sha256:34b24b74…`）。lgtm のログの error・panic は 0 件。collector は起動して止まらなかった。
- 確かめた後に2つとも止めた。アプリは起動していないため、外部エクスポートでログ・指標が届くことは確かめていない（appender の固定は前提が変わらず、FR2.2 の外部エクスポートの確かめは要らなかった）。

## 5. 負荷の試験（FR5）

- 依頼者の決定 B2（A 省く）で流していない。
- 理由: 要件 FR5.1 の条件は「Spring Boot・H2・JRE のどれかの版が変わったとき」で、Code Generation の終わりの版の前後の表（`code-summary.md` 2節）では、Spring Boot 4.1.2・H2 2.4.240・JRE のイメージ（`eclipse-temurin:25.0.5_8-jre-noble`、ダイジェストも同じ）はどれも変わっていない。そのため FR5.2（どれも変わらないときは省き、理由を記録する）に当たる。
- Jackson（3.1.7 → 3.1.8）を条件に入れない根拠（Code Generation のレビュー R-03）: FR5.1 の条件は Spring Boot・H2・JRE の3つだけを名指ししており、Jackson を含まない。Jackson の更新はパッチの版で、networknt 3.0.9 の推移依存として入った。動きは全件の `verify`（DSL のテスト 339 件を含む）と E2E 298 件で確かめた。
- FR5.3（流すときの決まり）は、流していないため当たらない。

## 6. 時間の記録（NFR5）

| 確かめ | 時間 | 42 分の線 |
|---|---|---|
| 手元の全件の `verify`（N=3） | 14分36秒 | — |
| 手元の E2E 全体 | 15.9 分 | — |
| CI の検査の job（N=3 の比べの中央値） | 21分32秒 | 内 |
| CI の検査の job（`e566faa4` のプッシュ） | 46分41秒 | **超えた**（cloc の apt-get の遅れ。3.2節、B6） |
