# CI/CD Pipeline — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の検査・負荷の試験・配備・戻しの流れです。既存の流れ（ローカルの1コマンドの検査 `./gradlew verify`、統合後の再確認の GitHub Actions `.github/workflows/ci.yml`、開発者の PC 上のコンテナへの配備）を正とし、U8 で足すものだけを書く（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `infrastructure-design-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号は `infrastructure-specification.md` と同じ。

## 1. 検査の段と U8 の確かめ

`./gradlew verify` とCI のタスク・段の並び・ビルドの設定は変えない（要点 12）。U8 のテストは既存の段で動く。

| 段（既存） | U8 の確かめ | 関門の基準 | 出典 |
|---|---|---|---|
| フォーマット・リンタ・ライセンスヘッダー | 新しいファイルに Apache License 2.0 のヘッダー（`/* ... */`）、palantir-java-format | 既存のまま（失敗で止める） | `team.md` の Code Style |
| ビルド・静的解析（SpotBugs ＋ FindSecBugs） | `appearance` のパッケージ | 既存のまま（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` で止める） | `team.md` の Code Style |
| 単体テスト | 判定の関数の単体テストと jqwik の性質ベースのテスト（3つの性質、失敗時の乱数の種を記録）、判定が Bean の作成の1回だけであること、`AppearanceBoundaryArchitectureTest`（DB アクセス・ほかの機能に依存しない、ほかの機能から依存されない） | 全件が通る | NFR6.2・NFR9.2・NFR9.8・NFR5.1 |
| 結合テスト | `appearance.web` の `XxxIT`: 未認証の GET 200 と2項目、未認証の POST 401、使えるトークン付きの POST 405 と `Allow`、未認証の HEAD 401、使えないトークン付きの GET 401・使えるトークン付きの GET 200、許されない値（`red`・`mono`）と設定なしで起動して blue・sans、元の文字列が本文に出ないこと、`Cache-Control: no-store` と CSP、WARN の件数・キー・値を含まないこと、GET の前後で監査の表の件数が変わらないこと、GET の前後で内部DB の接続を借りた回数が増えないこと。テスト用の公開の決まり（`PublicApiTestRules`）は有効にしない | 全件が通る。既存の健全性のテストもそのまま通る | NFR4.1〜NFR4.5・NFR4.8・NFR5.2・NFR9.1・NFR9.4〜NFR9.6 |
| カバレッジの下限 | 新しいパッケージ `appearance.config`・`appearance.service`・`appearance.web` は自動でパッケージごとの下限（行 80%・分岐 70%）の対象。計測の除外は増やさない | 下回ったら失敗 | NFR9.7、要点 13 |
| セキュリティ検査（Gitleaks・OSV-Scanner） | 新しい依存は無い | 既存のまま（High 以上で止める） | `tech-stack-decisions.md` の1節 |

- カバレッジは、テストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する（`project.md` の Testing Posture）。
- `common.security`（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧にある）の説明文の書き直しが、「手を入れる Bolt ではテストを足して一覧から外す」に当たるかは、承認の場の決定 A5 のとおり B3 か B4 の計画で依頼者に確かめる。この段では決めない。
- 接続を借りた回数を指標 `hikaricp.connections.acquire` で読めるかは、承認の場の決定（U8 R-02）のとおり B4 の最初に確かめ、読めなければテストの中だけの数える包みに替える（本番のコードは変えない）。

## 2. E2E

U8 の単独の E2E（Playwright、`./gradlew e2eTest`）は足さない。画面での見た目の反映は U4 の検査で扱う。E2E で使う WAR は見た目の設定なし（既定の blue・sans）で動くため、E2E の起動の設定に U8 の項目を渡す必要は無い。この段に持ち越された論点（Mailpit・招待のベース URL・V7・V8）は U8 に関わらない（要点 15）。

## 3. 負荷の試験

| 項目 | 設計 | 出典 |
|---|---|---|
| 台本 | コード生成（B4）で `perf/k6/scenarios.js` に見た目の設定の場面を1本足す（トークンなしの `GET /api/appearance`、場面とタグの名前は仮に `appearance`）。前例（`dslLight`）どおり `thresholdsFor` に `http_req_duration{name:appearance}` の `p(95)<300` を置き、checks で全件 200・本文が `brandColor`・`fontFamily` の2項目であることを確かめる。ファイルの先頭の場面の一覧と `perf/README.md` の場面の説明に足す | NFR6.1、要点 14 |
| 測る段 | performance-validation。Build and Test では k6 で読み込めること（`k6 inspect`）だけを確かめる | `performance-design.md` の5節 |
| 環境 | 使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、終わったら消す）。内部DB のファイルが膨らんだ状態（悪い側の条件）のまま、同時 10 件で流す。長い試験は `caffeinate -i` を付ける | `project.md` の Testing Posture |
| 置き場 | `./gradlew verify` と CI の外 | 既存の負荷の試験と同じ |
| 監査ログ | この API は監査に残らないため、試験で監査ログは増えない | NFR9.5 |
| 満たせなかったとき | 目標を緩めず、環境を起動し直して再現させ、原因をログと状態で確かめてから記録する | `project.md` の Testing Posture |

## 4. 配備

既存の流れのまま（要点 16）。

1. `develop` への統合の前に `./gradlew verify` を通す（コンテナの実行環境が動いていること）。
2. `./gradlew :backend:bootWar` で WAR を作り、`docker compose up -d --build` でイメージを作り直して起動する。
3. 見た目の設定を変えたいときは、`.env` に `MASTERSMITH_APPEARANCE_BRAND_COLOR`・`MASTERSMITH_APPEARANCE_FONT_FAMILY` を入れる（無ければ既定）。変更は起動し直しで当たる。
4. 健全性（compose の確かめと `/actuator/health` の 200）を待ち、スモークテストを行う。スモークテストに「未認証の `GET /api/appearance` が 200 で2項目を返す」を足す候補とし、具体は deployment-pipeline の段で決める。
5. 起動のログに見た目の設定の WARN が無いこと（許されない値を入れていないこと）を確かめる。

環境の昇格（検証環境・本番環境）は、配備先が決まるまで置かない。機能の切り替えの旗は使わない。

## 5. 戻し

| 場合 | 戻し方 | 注意 |
|---|---|---|
| U8 を含む版を戻す | イメージだけを直前の版に戻す既存の決まりのまま | 直前の版には `/api/appearance` が無く、画面は失敗として既定のまま描く（U4 W3）。U8 は内部DB の表を持たないため、スキーマの後方互換の心配は無い。`.env` に足した2項目は直前の版では使われず害が無いため、消さなくてよい |
| 見た目の設定の値を戻す | `.env` の2項目を元に戻して起動し直す | 作り直しは要らない（`reliability-design.md` の6節） |

## 6. CI/CD の秘密情報

U8 は CI/CD に新しい秘密情報を足さない。CI は既存どおり見た目の設定なし（既定）で結合テストを動かす。`.env` はコミットしない。

## 7. 上流との差

承認済みの文書と食い違う点は無い。k6 の閾値を `thresholdsFor` に置くこと（3節）と、戻すときに `.env` の2項目を消さなくてよいこと（5節）は、承認済みの設計に具体が無い点を既存の前例で埋めた追加。
