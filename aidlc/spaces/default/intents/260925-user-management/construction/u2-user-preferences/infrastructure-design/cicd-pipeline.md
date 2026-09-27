# CI/CD Pipeline — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の検査と配備の流れです。検査の入口（`./gradlew verify`）と CI（`.github/workflows/ci.yml`）は既に実装されているため、新しい設計ではなく、既にあるものの上で U2 が何を足し、何に影響するかを書きます（`aidlc/spaces/default/memory/project.md` の Deployment）。出典の略号は `infrastructure-specification.md` と同じ。

## 1. 検査の流れ（既存の `./gradlew verify`、変えない）

統合の前の関門はローカルの `./gradlew verify` の1コマンドで、CI も統合の後（`develop` へのプッシュ時）に同じタスクを呼びます（`team.md` の Way of Working・Deployment）。U2 は段の並び・`ci.yml`・新しい検査の道具を変えず、新しい依存も足しません（要点 8、`tech-stack-decisions.md`）。

| 段（既存の順） | U2 が足すもの | 関門の基準（既存） |
|---|---|---|
| フォーマット・リンタ・ライセンスヘッダー | 新しい Java のファイル（`user.web` など）と V7 の SQL | Spotless（palantir-java-format、Apache License 2.0 のヘッダー）。違反で失敗 |
| ビルド（型検査を含む） | `user.web`・`user.domain` の新しい型、`UserSummary` の広げ | コンパイルの失敗で失敗 |
| 単体テスト（`test`、`XxxTest`） | DisplayName・表示の設定の値・PasswordPolicy・項目ごとの誤りの理由の決め方の jqwik の性質ベースのテスト（失敗時の種を記録）、結果の型から応答への変換 | 1件でも失敗で失敗 |
| 結合テスト（`integrationTest`、`XxxIT`） | 3本の API（401・200・204・400）、パスワードの変更（今のパスワードの確かめ、規則の境界、変更の後のトークン、照合の後の同時の変更で 400）、部分の書き換え、監査（必須の項目・書き込みの失敗・巻き戻し）、秘密の漏えい（`*SecretLeakIT` に V7 の2列）、ログイン・更新の SQL の数、`createUser` の同時の作成、V7 の自動の確かめ（2節） | 1件でも失敗で失敗。U2 のテストはどれも組み込みの H2 を使い、コンテナを使わない |
| カバレッジの下限（`jacocoTestCoverageVerification`） | パッケージごとの下限の対象が増える（3節） | 全体の合計と、`packagesJudgedByTotal` に無いパッケージごとに行 80%・分岐 70%。下回れば失敗 |
| セキュリティ検査（Gitleaks・SpotBugs＋FindSecBugs の関門・OSV-Scanner） | 新しい除外は足さない（NFR9.3） | 既存の基準（重大度 High 以上、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず失敗） |

- ArchUnit の既存の層と機能の境界のテスト（`user` は `auth`・`audit` に依存しない、ハッシュを読むのは `user` の中だけ など）は緩めません（`logical-components.md` 5節）。
- CI は秘密情報を使いません。テストの資格情報はテストの中で作る仮の値で、Gitleaks の検査の対象のままにします。

## 2. V7 の自動の確かめ（`reliability-design.md` 6.2 の (1)）

- V6 までの移行ファイルの複写をテストの資源に置き、組み込みの H2 の一時のファイルに対して、今の Flyway で V7 まで当てた後、V6 までしか知らない Flyway の `validate`・`migrate` が失敗しないことを確かめる。あわせて既知の限界（足した4列を渡さない users の追記は失敗し、audit_events の2列を渡さない追記は通る）と、V7 の値・1回だけの適用を固定する（`infrastructure-specification.md` 4節）。
- コンテナを使わないため、コンテナの実行環境が無いときに警告を出して飛ばしてよいテスト（対象DB のテスト）には入れない。CI でも毎回動く（要点 9）。
- 見込み（Flyway の既定が知らない新しい移行を無視する）が外れたと分かったら、コード生成の計画の中で依頼者に諮る（承認の場の決定 U2 R-02 の扱いと同じ）。

## 3. カバレッジの一覧（`packagesJudgedByTotal`）から外す作業の影響（NFR9.6）

- B2 で、手を入れる `user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web` を `backend/build.gradle.kts` の `packagesJudgedByTotal`（今 22 パッケージ）から外す。実際に手を入れたら `audit.repository` も外す。新しい `user.web` は一覧に無いため自動で対象になる（`team.md` の Testing Posture）。
- 外したパッケージが1つでも単独で行 80%・分岐 70% を下回ると、ローカルの `verify` も CI も同じ判定で失敗する。`ci.yml` は変えない。
- そのため統合の前に、手元で colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（README）を渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して、パッケージごとの値を記録する（`project.md` の Testing Posture。渡さないと対象DB のテストが SKIPPED になり下限の判定が崩れる）。
- 前に単独で下回っていた `audit.service`（行 77.2%）は、PasswordChangedEvent の受け取りと既存の分岐のテストを足して上げる。一覧と計測の除外は増やさない。一度外したパッケージは戻さない。

## 4. 負荷の試験（performance-validation へ渡す、NFR5.2・NFR6.1〜NFR6.5）

場面の用意と手順書は Build and Test、測定は performance-validation が持ちます（`performance-requirements.md` の前提）。既存の使い捨ての環境（`docker/perf/compose.yaml`、`perf/README.md`）で行い、配備した環境には流しません。

1. `perf/k6/scenarios.js` に、プリファレンスの取得・保存（成功・入力の誤り）、パスワードの変更の成功・今のパスワードの誤り・入力の誤りの場面を足す（名前はコード生成・Build and Test で決める）。どれも同時 10 で、閾値は変更の成功が p95 2 秒、ほかは 1 秒。既存の `loginSuccess`・`refresh` を流し直す（NFR6.5）。
2. `perf/README.md` の手順 2 の仮の利用者の SQL に display_name（メールアドレスと同じ値）を足す（V7 の後は無いと失敗する。`infrastructure-specification.md` 9節の I-D2）。
3. パスワードの変更の成功の場面には専用の仮の利用者（例 `perf-pw01`〜`perf-pw10`）を足し、VU ごとに1人を当てて変更の前後のパスワードを交互に使う。ほかの場面の利用者のパスワードは変えず、1人を同時に2つの場面で使わない。
4. 同時のログインの前に、仮の利用者を1人ずつログインさせてロックの状態の行を作る（`project.md` の Testing Posture）。
5. 使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、hikaricp の待ちの時間切れの累計 0 と借りるまでの待ちの最大を読む（`monitoring-design.md` 1節）。
6. 使い捨ての環境を消す前に、H2 の道具（読み取り）で PASSWORD_CHANGED・SUCCESS の件数を数え、流した成功の件数と突き合わせる（`project.md` の Testing Posture。消した後は取り直せない）。
7. `caffeinate -i` を付けて流し、測る間は配備したアプリを止める。台本を書く前に、計画の「引き継ぐこと」の項目を台本の手順と1つずつ突き合わせる（`project.md` の Testing Posture）。

## 5. 統合と配備

| 順 | 内容 | 関門 | 持ち主の段 |
|---|---|---|---|
| 1 | B2 の作業ブランチ（`develop` から作る短命のブランチ）で U2 を作る | — | code-generation |
| 2 | 統合の前にローカルの `./gradlew verify`（3節の実測を含む）を通す。コンテナの実行環境が無い警告が出たら起動してやり直す | 全検査の合格 | code-generation・build-and-test |
| 3 | `develop` へ squash マージ（1 Bolt が1コミット、日本語の件名）。プッシュは依頼者が行う | 依頼者の承認 | — |
| 4 | CI が同じ `verify` を再確認する。失敗したら次の Bolt に進む前に直す | CI の合格 | ci-pipeline |
| 5 | この Intent のすべての Bolt の後に、手元のコンテナへ手で配備する（README の「コンテナでの起動と確認」）。配備の前に内部DB のバックアップと戻し用のタグを取る（`infrastructure-specification.md` 5節） | 未コミットの変更が無い（アプリのソース） | deployment-pipeline・deployment-execution |
| 6 | 起動で V7（と V8）が当たり、ヘルスチェックが UP、スモークテストが通るまで配備の完了としない。U2 からは、既存のログイン・更新が今までどおり動くことが確かめの対象（応答の4つの値の確かめは画面の単位と合わせて deployment-pipeline で決める） | healthy とスモークテスト | deployment-execution |
| 7 | 配備の後に戻しの練習を行う（`infrastructure-specification.md` 6節、Q1 A） | 1つ前の版の起動・ログイン・更新・監査・初期管理者を作らない | deployment-execution |

- 配備の方式は既存のとおり1台の置き換え（青緑・カナリアは無い）。成果物の版はコミットのハッシュで見分ける（`team.md` の Deployment）。
- 戻し方は `infrastructure-specification.md` 5節（第一の手は V7・V8 の後の内部DB のまま戻し用のタグのイメージで起動、第二の手はバックアップの展開、戻すときは初期管理者のメールアドレスを変えない）。

## 6. E2E

U2 は画面を持たないため、`./gradlew e2eTest`（`verify` と CI の外）に流れを足しません。パスワードの変更とプリファレンスの画面の確かめは U7（B5）が持ちます（要点 11）。

## 7. 秘密情報と CI/CD

- U2 は新しい秘密情報・環境変数を足さず、CI の設定にも秘密を置かない。
- 負荷の試験と戻しの練習で使う資格情報・`.env` の複写は、リポジトリの外（ホームの下、権限 700）に置き、表示せず、終わったら消す（`infrastructure-specification.md` 7節）。

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| C-D1 | `nfr-design/performance-design.md` 7節 | 仮の利用者 10 名以上、同じ利用者を同時に使わない | 既存の手順の SQL に display_name を足し、パスワードの変更の専用の仮の利用者を足す（4節の 2・3） | `infrastructure-specification.md` 9節の I-D2 と同じ。既存の手順が V7 で壊れるための追加 |
