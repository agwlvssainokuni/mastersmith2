# CI/CD Pipeline — U3 利用者の管理の API（u3-user-admin-api）

U3 の検査の流れ（1コマンドの検査と CI）、カバレッジの作業、負荷の試験の用意、E2E、統合と配備を示します。既存の仕組み（`.github/workflows/ci.yml`・`build.gradle.kts` の `verify` と `e2eTest`・`backend/build.gradle.kts` の `packagesJudgedByTotal`・`docker/perf/compose.yaml`・`perf/README.md`・`perf/k6/scenarios.js`・`frontend/playwright.config.ts`）を正とし、U3 で足す点だけを書きます。`./gradlew verify` と CI の設定は変えません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

出典の略号は `infrastructure-specification.md` と同じ。

## 1. 検査の流れ（既存の `./gradlew verify`、変えない）

`.github/workflows/ci.yml`（`develop` へのプッシュ・`v*` のタグ・手動、`./gradlew verify`、制限時間 60 分、秘密を使わない）は変えません。U3 のテストは既存の段（`build.gradle.kts` の `verifyStages` の段 0〜9）に入り、1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U3 で足すもの | 関門（失敗の条件） |
|---|---|---|
| 1 フォーマット・2 リンタ・3 ライセンスヘッダー | `useradmin` の Java（B3・B4）、B4 で手を入れる `auth`・`invitation`・`common.observability`・`common.error.web` と、判定の部品と例外の置き場（候補 `common.persistence`） | 既存の基準（palantir-java-format、Apache License 2.0 の `/* */` のヘッダー） |
| 4 ビルド | 新しい依存は無い（`tech-stack-decisions.md`） | コンパイルの失敗 |
| 5 単体テスト（`XxxTest`） | B3: 入力の境界、伏せ字の `toString`、`UserAdminBoundaryArchitectureTest`。B4: 拒否の判定の関数と LockView の jqwik（失敗時の種を記録）、本番の業務処理が Busy のときに巻き戻しの印を付けること、排他の失敗の判定（型と誤りの番号）、repository が例外をメソッドの外へ出さないこと | 1件でも失敗 |
| 6 結合テスト（`XxxIT`） | B3: 一覧（1ページ・空のページ・問い合わせの回数・`%`・`_`・`\` の文字どおりの検索）、氏名と言語の変更、2つの API の 401・403・200、要求の改ざん、TRACE と INFO の `UserAdminSecretLeakIT`。B4: 5つの操作の認可と拒否の順、最後の管理者の保護（待ち合わせの重なり）、失敗回数を戻す操作とログインの重なり、上限切れの 409 と巻き戻し、監査の行、`AuditWriteFailureIT` の形、止める操作の巻き戻り、既存の E1〜E4 と書き込みの問い合わせの上限切れの漏えい（TRACE と INFO の両方、`infrastructure-specification.md` 6節） | 1件でも失敗。どれもコンテナを使わず組み込みの H2 で動くため、コンテナの実行環境が無いときも飛ばさない（飛ばしてよいのは対象DB のテストだけ、`team.md` の Way of Working） |
| 7 カバレッジ | 2節 | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外を足さない |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）・OSV-Scanner・Gitleaks は既存のまま。問い合わせは名前つきの引数と SpEL だけ | 既存の基準。`backend/config/spotbugs-exclude.xml` に除外を足さない（NFR9.2） |
| 9 成果物 | 既存の `bootWar`（コミットのハッシュで見分ける） | WAR が作れない |

- 結合テストの時間: B4 の上限切れのテストは、1件ごとに排他の待ちの上限（約 3 秒）を待つ。5つの操作・既存の E1〜E4 の経路・書き込みの問い合わせを TRACE と INFO の両方で起こすため、結合テストの時間が延びる。B4 の前後の `verify` の時間を記録し、CI の制限時間 60 分に余裕があることを確かめる。今の時間の基準値と増加の許容は B3・B4 の計画で決める（承認の場の決定 R-02）。待つ時間を短くするために排他の待ちの上限を変えない（NFR4.4）。
- CI に秘密を渡さない。テストデータのメールアドレスは予約のドメイン（`example.com` など）だけ。

## 2. カバレッジ（NFR9.6）

| パッケージ | Bolt | 今の扱い（2026-10-02 の `backend/build.gradle.kts`） | U3 での作業 |
|---|---|---|---|
| `useradmin.web`・`useradmin.service`・`useradmin.domain`（ほかに作れば `useradmin` の下位パッケージ） | B3・B4 | 新しいパッケージ | 自動でパッケージごとの下限の対象 |
| 判定の部品と例外の置き場（候補 `common.persistence`） | B4 | 新しいパッケージ | 自動で対象 |
| `common.observability`（`TraceAspect`） | B4 | `packagesJudgedByTotal` にある | Q2 A の手当てで手を入れるため、テストを足して下限を満たし、一覧から外す |
| `common.error.web`（`GlobalExceptionHandler`） | B4 | `packagesJudgedByTotal` にある | 同上 |
| `auth.domain`・`auth.repository` | B4 | 今は一覧にある。B1（U1）で外す計画 | B1 で外れた後も、U3 の変更（LockView・`tryLockForUpdate`・E1 の直し）の後に下限を満たし続ける |
| `auth.service`・`user` の各パッケージ・`audit.domain`・`audit.service`・`invitation.repository`・`invitation.service` | B3・B4 | すでにパッケージごとの下限の対象 | 下限を満たし続ける |
| `access.domain` | B3・B4 | 一覧にある | 使うだけで本体を変えないため作業は付かない。手を入れたときは一覧から外す |

- 一覧の残りのパッケージ（例 `common.web`）に実際に手を入れた Bolt も、`team.md` のとおり下限を満たして一覧から外す。一覧を増やさない、計測の除外を増やさない。
- 今の値は、B4 のコード生成の計画で `:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` で実測し、`jacocoTestReport.xml` から読んで見積もる（`project.md` の学び）。
- 統合の前に、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して、パッケージごとの値を記録する（`project.md` の Testing Posture）。

## 3. Bolt と検査の対応（関門）

| Bolt | 統合の前の関門 | 統合の後 |
|---|---|---|
| B3 一覧と氏名・言語の変更 | `./gradlew verify`（clean を付けた実測を含む、コンテナの実行環境を起動して対象DB のテストも通す） | CI の `verify` |
| B4 管理の操作と最後の管理者の保護 | `./gradlew verify`（同上）と、E2E の `./gradlew e2eTest`（010〜100、5節） | CI の `verify` |

## 4. 負荷の試験（performance-validation へ渡す、NFR5.1〜NFR5.7・NFR6.2・NFR6.3）

台本の場面と手順書（`perf/k6/scenarios.js`・`perf/README.md`）と `k6 inspect`（`--include-system-env-vars` を付ける）での読み込みと場面の名前の確かめは Build and Test、測定は performance-validation が持ちます。既存の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、`127.0.0.1:18080`）で行い、配備した環境には流しません（`project.md` の Testing Posture）。

### 4.1 使い捨ての環境の用意

| 対象 | 用意 | 理由 |
|---|---|---|
| `docker/perf/compose.yaml` | 変えない。U3 はメールを送らないため Mailpit（profile `mail`）を起動しない | 要点の「決まっていること」 |
| 一時の環境ファイル（`perf/README.md` の手順 1 の `app.env`、リポジトリの外、権限 700） | 接続プールを見る場面では `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`。NFR6.3 の場面では加えて `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10`。終わったら既定の 30 に戻した環境で NFR6.2 を流すか、環境を作り直す | NFR6.2・NFR6.3。配備したアプリの公開の範囲と上限は変えない |
| 試験用のデータ（アプリを止めて SQL で入れる、`perf/README.md` の手順 2 の形） | 利用者 1,000 名（一覧）、操作する管理者と5つの操作の対象の利用者（VU ごとに分ける）、止める対象ごとの未無効 100 件・無効 1,000 件のリフレッシュトークン（くり返しの前に入れ直すか、回数分の対象を用意） | NFR5.1・NFR5.4・NFR5.5。メールアドレスは予約のドメインだけ |
| ロックの状態の行 | SQL で入れた利用者は行を持たない。失敗回数を戻す場面は、組の最初のログインの失敗で行を作る（判定と回数に数えない） | NFR5.4、`project.md` の Testing Posture |
| VM の余裕 | VM（CPU 4・メモリ 6GiB）に使い捨てのアプリ（2g）と k6 を同時に置ける。測る間は配備したアプリを止める。k6 の分の CPU が値に混ざることを結果に明記する | `performance-requirements.md` の「測り方の決まり」 |

### 4.2 k6 の場面（`perf/k6/scenarios.js` に足す、名前は Build and Test で決める）

| 場面 | 判定 | 受け入れの条件 |
|---|---|---|
| 一覧 a〜d（検索なしの1ページ目・最後のページ、多く当たる検索・ほとんど当たらない検索） | 場面ごとに同時 10 件・100 回以上、`http_req_duration` の p95 1000 ms 以内、`checks`（200 と total）の率 1 | 1,000 名で測る（NFR5.1） |
| 氏名と言語の変更 | 同時 10 件・100 回以上、p95 1000 ms、`checks` の率 1 | 各 VU は自分の対象の利用者を使う（NFR5.3） |
| 5つの操作 | 5つを合わせて同時 10 件、操作ごとに 100 回以上、操作ごとの p95 1000 ms、`checks`（204）の率 1。409（BUSY・NO_CHANGE）と 5xx が 0 件 | 操作する管理者と初期管理者を対象にしない、初期管理者を操作する人にしない、対象は VU ごとに分ける、組で状態を戻しながらくり返す（NFR5.4・NFR5.6） |
| 止める（悪い側） | 5つの操作と同じ | 対象ごとにトークンの行を入れ直す（NFR5.5） |
| 接続プール（上限 30） | 5つの操作と一覧を合わせて同時 10 件。待ちの時間切れの累計 0、500 が 0 件、流した5つの操作の成功の件数と監査の SUCCESS の件数が一致、借りるまでの待ちの最大を記録 | NFR6.2 |
| 接続プール（上限 10） | (A) 5つの操作だけ同時 5 件で時間切れの累計 0・件数が一致。(B) 同時 10 件で2本目の待ちが出る。(B) の欠けた監査と ERROR の件数を記録し、p95 と件数の一致に数えない | NFR6.3。上限 10 の場面の条件と BUSY の件数の扱い（NFR 要件のレビューの R-07・R-08）は performance-validation の台本で扱う |

- 正とする値は k6 の値。サーバー側の `http_server_requests_milliseconds_bucket` の p95 は参考として並べる。
- 長い試験は `caffeinate -i` で台本の全体を包む。遅れが出たら `pmset -g log` でスリープを確かめる。試験の前に、この表と台本の手順を1つずつ突き合わせる（`project.md` の Testing Posture）。
- 監査の件数は、使い捨ての環境を消す前にアプリを止めて内部DB を読み取りで開いて数え、結果を見てから片付ける（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v`、一時の環境ファイルを消す）。
- 目標に届かないときは目標を緩めず、原因をログと状態で確かめて依頼者に相談する。一覧が NFR5.1 に届かなければ、`users (created_at, user_id)` の索引を足す直し（NFR10.2 の決まり）を諮る。

## 5. E2E（`./gradlew e2eTest`、verify と CI の外、Q1 A）

| 項目 | 設計 | 出典 |
|---|---|---|
| U3 の流れ | 足さない。この Intent の代表の流れ1本は B5（U5） | `team.md` の Testing Posture、`bolt-plan.md` の B5 |
| B3 | E2E を流さず、`./gradlew verify` だけで統合する。新しい管理の API と検索の文字の型の変換を足すだけで、画面と認証の経路に手を入れない。B3 の計画で変更の範囲がこの経路に触れないことを確かめ、触れたら統合の前に E2E を流す条件を書く（承認の場の決定 R-03） | Q1 A |
| B4 | 統合の前に `./gradlew e2eTest`（010〜100）を手元で流し、通ったことを記録する。B4 はログインの判定（`LoginService` の待ち合わせの口）、ロックの状態の行の排他（E1）、招待・送り直し・取り消し・登録の完了の排他の問い合わせ（E2〜E4）、すべての要求が通る `TraceAspect` と `GlobalExceptionHandler` に手を入れ、既存の `020-auth`（ログイン）と `090-invitation-registration-flow`（招待から登録の完了まで）がこの経路を通るため | Q1 A |
| B4 の前提 | 事前に `npx playwright install chromium` でブラウザを入れ、`docker compose --profile mail up -d mailpit` で Mailpit を起動する（`e2eTest` は `http://127.0.0.1:8025/api/v1/info` に届かなければ起動の手順を示して失敗する）。`caffeinate -i` で全体を包む | `build.gradle.kts` の `e2eTest`、`project.md` の Testing Posture |
| 秘密 | 既存の `frontend/playwright.config.ts` のとおり、仮の資格情報はプロセスの環境変数で渡し、報告に残らないことを既存の確かめの部品が見る。Mailpit が受けたメールは見終えたら止めて消す | `project.md` の Testing Posture |

## 6. 統合と配備

| 順 | 内容 | 関門 | 持ち主の段 |
|---|---|---|---|
| 1 | B3 の作業ブランチ（`develop` から作る短命のブランチ、例 `feature/260930-user-admin-b3`）で U3 の前半を作る | — | code-generation |
| 2 | 統合の前に `./gradlew verify`（2節の実測を含む）を通す。コンテナの実行環境が無い警告が出たら起動してやり直す | 全検査の合格 | code-generation・build-and-test |
| 3 | `develop` へ squash の1コミット（日本語の件名）。統合を終えたブランチは消す。プッシュは依頼者が行う | 依頼者の承認 | — |
| 4 | CI が同じ `verify` を再確認する。失敗したら次の Bolt に進む前に `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | CI の合格 | ci-pipeline |
| 5 | B4 の作業ブランチで U3 の後半を作る。既存の経路の上限切れの漏えいの直しは、再現するテストを直しと同じコミットに含める（`project.md` の Mandated） | — | code-generation |
| 6 | 統合の前に `./gradlew verify`（2節の実測、`common.observability`・`common.error.web` を一覧から外した後の値を含む）と `./gradlew e2eTest`（5節）を通す | 全検査と E2E の合格 | code-generation・build-and-test |
| 7 | 3・4 と同じく squash の1コミットで統合し、CI で再確認する | 依頼者の承認・CI の合格 | — |
| 8 | この Intent のすべての Bolt の後に、手元のコンテナへ手で配備する。U3 は表・`.env`・設定を変えない（V9 のバックアップと戻しの練習は U1 の段で置かないと決まっている）。戻しの手順に、戻す前に停止中の利用者を確かめる手順を必ず入れる（下の注） | 未コミットの変更が無い（アプリのソース） | deployment-pipeline・deployment-execution |
| 9 | ヘルスチェックが UP、スモークテストが通るまで配備の完了としない。U3 の確かめに何を入れるかは、`infrastructure-specification.md` 8節の事実（監査に残る要求・データを変える要求）をもとに deployment-pipeline で決める。監査に残る要求を送る前は依頼者に伝える | healthy とスモークテスト | deployment-pipeline・deployment-execution |

- 配備の方式は既存のとおり1台の置き換え（青緑・カナリアは無い）。成果物の版はコミットのハッシュで見分ける（`team.md` の Deployment）。
- 戻し方は、U3 から見ると直前の版のイメージへ戻すだけ（`infrastructure-specification.md` 5節）。ただし戻し先のこの Intent の前の版は停止の判定を持たず、戻している間は停止中の利用者がログインの照合・トークンの更新・アクセストークンの認証を通れる。戻す前に停止中の利用者がいるかを件数で確かめ、いれば扱いを依頼者に確かめる手順を deployment-pipeline で必ず決める（U1 の承認の場の決定 R-03 と同じ条件）。前の版が監査の新しい値の行を読まないことはソースで確かめた（同 5.1）。

## 7. 秘密情報と CI/CD

- U3 は新しい秘密情報・設定の項目を足さず、CI の設定にも秘密を置かない。配備した環境の `.env` は変えない。
- 負荷の試験の資格情報と一時の環境ファイルは、リポジトリの外（権限 700）に置き、値を表示せず、終わったら消す。試験用の利用者は予約のドメインだけ。
- E2E の仮の資格情報は既存のとおりプロセスの環境変数で渡す。
- 結果の記録・報告に、利用者のメールアドレス・氏名・トークン・ハッシュ値を写さない。

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| C-D1 | `inception/delivery-planning/bolt-plan.md` の「すべての Bolt に共通の完了の条件」 | 統合の前に E2E を流すのは画面・認証に関わる B1・B2・B5 | B4 も統合の前に `./gradlew e2eTest`（010〜100）を流して記録する。B3 は流さない（5節） | Q1 A。Bolt の計画の後に決まった機能設計と NFR 設計で、B4 が認証の経路に手を入れることになったため。Bolt の計画は書き換えない |
| C-D2 | Delivery Planning の B4 の見積もり、NFR9.6 | `common.observability`・`common.error.web` の作業は無い | B4 で2つのパッケージを一覧から外し、パッケージごとの下限を満たす（2節） | Q2 A（`infrastructure-specification.md` 10節の I-D1・I-D2）。今の値は B4 の計画で実測する |
| C-D3 | NFR 設計 `security-design.md` 10節の確かめのテストの一覧 | 上限切れの漏えいのテストは U3 の5つの操作と既存の E1〜E4 | 書き込みの問い合わせの上限切れの漏えいのテスト（TRACE と INFO の両方）を B4 に足す（1節） | Q2 A。どの書き込みの問い合わせを対象にするかは B4 の計画で決める |

## 承認の場の決定（Request Changes、2026-10-02）

この段の1回目のレビュー（Verdict READY、Major 1件・Minor 3件）を受けて、依頼者が承認の場で決めたことです。

| ID | 扱い | 内容 | 反映した所・渡す先 |
|---|---|---|---|
| R-01（Major） | 直した | 5節の戻し方の誤り（「1つ前の版も停止の列を読む」）を直した。戻し先はこの Intent の前の版のイメージで V9 の停止の列を知らないため、戻している間は停止中の利用者がログインの照合・トークンの更新・アクセストークンの認証を通れる（`project.md` の Mandated が効かない）。戻す前に停止中の利用者がいるかを件数で確かめ、いれば扱いを依頼者に確かめる手順を deployment-pipeline で必ず決める。U1 の「V9 の後方互換と戻しの練習を置かない」の受け入れ（U1 の承認の場の決定 R-03）と同じ条件。前の版の本番のコードは監査の行を読まない（`AuditEventRepository` を呼ぶのは `save` だけ）ことをソースで確かめた | `infrastructure-specification.md` 4節・5節・5.1・8節・10節 I-D4、この文書 6節、`traceability.json` の NFR10.1。deployment-pipeline へ渡す |
| R-02（Minor） | 申し送る | 今の `verify` の時間の基準値を測り、B4 の結合テストの増加の許容（CI の制限時間 60 分に対してどこまでか）を決める | B3・B4 のコード生成の計画 |
| R-03（Minor） | 申し送る | B3 の変更が画面・認証の経路に触れないことを確かめる項目を置き、触れたら統合の前に `./gradlew e2eTest` を流す条件を書く | B3 のコード生成の計画 |
| R-04（Minor） | 申し送る | 監視の `uri` ラベルの実際の値と `le` のバケットを確かめる（`monitoring-design.md` 8節の確認項目を段の入口の条件として保つ） | observability-setup |
| 書き手の点 1 | 受け入れ | NFR 設計の2回目のレビューの R-02（E2〜E4 を移した後も断片のメソッドの名前・引数・戻り値を変えず、`InvitationService` の既存の単体テストの差し替えを壊さない）を B4 の計画の条件にする | B4 のコード生成の計画 |
| 書き手の点 2 | 受け入れ | 初期管理者だけでは最後の管理者の拒否（LAST_ACTIVE_ADMIN）を見せられない（自分自身の操作は先に SELF_OPERATION で拒否される）事実を渡す | deployment-pipeline（`infrastructure-specification.md` 8節） |
| 書き手の点 3 | 受け入れ | B4 の `verify` の時間（上限切れのテストで延びる分）は B4 の計画で見積もる | B4 のコード生成の計画（R-02 と合わせる） |
