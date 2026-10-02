# Infrastructure Design の質問 — u5-user-admin-ui（利用者の管理の画面、ui）

U5 は、管理者が利用者の一覧・検索・5つの操作（印を付ける・外す・止める・停止を解く・失敗回数を戻す）・氏名と言語の変更を行う画面（`frontend/src/features/useradmin/`）と、その E2E（110 の代表の流れ、120 の実際のブラウザの検査と画面の時間の測り）の単位です。B5 で作ります。自分の API・内部DB の表・サーバーの状態を持たず、`backend/` に手を入れません。ui の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です（段の定義 `.claude/aidlc-common/stages/construction/infrastructure-design.md` の `produces_kinds`）。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。U5 の基盤の中身のほとんどは、承認済みの NFR 要件・NFR 設計と既存の仕組みで決まっています。判断の分かれる1点（B2〜B4 の E2E の実行で html の報告と trace に仮の資格情報が残りうる間の扱い。同じ段の U4 のレビューの R-03）だけを質問にしました。

読んだ上流（とくに断りの無いものは `aidlc/spaces/default/intents/260930-user-admin/` の下）:

- この単位の承認済みの NFR 設計 `construction/u5-user-admin-ui/nfr-design/`（`security-design.md` の 3節・8節・9節・12節と「承認の場の決定（Request Changes、2026-10-02）」、`performance-design.md` の 5節・6節、`logical-components.md` の 3節・5節・6.3・8節・9節）
- この単位の承認済みの NFR 要件 `construction/u5-user-admin-ui/nfr-requirements/`（`security-requirements.md` の NFR3.4・NFR3.5 と「承認の場の決定」、`performance-requirements.md` の NFR5.1〜NFR5.3、`tech-stack-decisions.md` の NFR7.3・NFR9.2〜NFR9.4・NFR9.8・NFR9.9）
- この単位の承認済みの機能設計 `construction/u5-user-admin-ui/functional-design/`（`functional-spec.md` の 9節と「承認の場の決定」、`frontend-components.md` の 2.4・2.5・9節）
- 部品の一覧 `inception/domain-design/components.md`、契約 `inception/contract-design/contract-summary.md`（C3・C4）、Bolt の計画 `inception/delivery-planning/bolt-plan.md`（B5 と共通の完了の条件。B5 はサブモジュールの固定先の更新を含むため fast-forward でよい）
- 同じ段で先に決まった U1〜U4 の質問の文書（`construction/u1-user-suspension/`・`u2-shared-paging/`・`u3-user-admin-api/`・`u4-admin-forbidden-ui/` の `infrastructure-design/`）と、U4 のレビューの記録（`.aidlc-reviews/infrastructure-design/units/u4-admin-forbidden-ui/fbbe60de556495c3/1.json` の R-03）。U3 の Q1 A で、B4 も統合の前に `./gradlew e2eTest` を流すことになった
- 決まり `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment・Code Style）・`project.md`
- 既存の仕組み（読むだけ）: `frontend/playwright.config.ts`（reporter に list・html・json と報告の部品、`trace: 'retain-on-failure'`）、`frontend/playwright-secret-check-reporter.ts`、`frontend/e2e/`（010〜100 と `support/`）、`frontend/.npmrc`（今は `engine-strict=true` だけ）、`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/`）、`build.gradle.kts`（`verifyPrepare` の `vendorInstall`・`vendorBuild`・`vendorUnchanged`、`e2eTest` の Mailpit の確かめ）、`.github/workflows/ci.yml`（`submodules: true`、`./gradlew verify`）、`docker/monitoring/`、`README.md`（「サブモジュール」「ビルドした WAR での画面の確認（E2E）」）

## 決まっていること（質問にしない）

| 決まっていること | 根拠 |
|---|---|
| 配備の形は変えない。変わるのは実行可能 WAR に同梱する `dist` の中身（`features/useradmin/` の画面。`lazy` で遅れて読む塊）だけで、`Dockerfile`・`compose.yaml`・`.env` の項目・資源の上限・ヘルスチェック・CSP（`application.yaml`）・キャッシュの扱いは変えない。新しい秘密を足さない。戻しは直前の版のイメージへ戻すだけ | `nfr-design/security-design.md` 7節・9節、`nfr-design/performance-design.md` 6節 |
| 画面の側に独自の指標・ログの送り先・時間の送信を足さない。管理の API の遅さ・誤り・403 は U3 の基盤の設計の `monitoring-design.md`（`http.server.requests` の `uri` 別、`ms-forbidden`・`ms-5xx-ratio`・`ms-error-logs` など）と監査で見る。U5 のために警報・パネルを足さない | `nfr-design/logical-components.md` 5節、`construction/u3-user-admin-api/infrastructure-design/monitoring-design.md` |
| 画面の時間（NFR5.1 一覧を開くまで 2 秒、NFR5.2 次のページまで 1.5 秒）は 120 の中の測りのテスト1件（既定の1組だけ）で測り、成否にしない。値は添付 `user-admin-screen-ms` に数だけを残す。記録の置き場は、コード生成（B5）の記録と Build and Test の記録で、json の報告から添付を復号して写す。`nextPage` は API の時間を含まず、`list` は差し替えの口の上乗せを含むことを書く。API の時間は U3 の持ち物で performance-validation の k6 が押さえる | `nfr-design/performance-design.md` 5節・6節、`nfr-requirements/performance-requirements.md` の NFR5.1〜NFR5.3 |
| E2E の置き場は `frontend/e2e/110-user-admin-flow.e2e.ts`（代表の流れ、流れの本数に数えるのは 110 だけ）と `frontend/e2e/120-user-admin-accessibility.e2e.ts`（20 組×11 状態の検査と測り、本数に数えない）。どちらも `./gradlew e2eTest` の中で `verify` と CI の外に置き、`workers: 1` のまま 010〜100・130 と同じ WAR・一時の内部DB・初期管理者を共有する。110 は自分で作った利用者だけを変え、120 は書き換えの要求を本物へ通さない | `nfr-requirements/tech-stack-decisions.md` の NFR7.3・NFR9.8、`team.md` の Testing Posture、`project.md` の学び（アクセシビリティの検査は本数に数えない） |
| 実行の時点は、B5 の統合の前とリリースの前に手元で `./gradlew e2eTest` を流し、010〜100・130・110・120 が通ったこと、110 で飛ばした注記が出ていないこと、報告の部品の結果（見つかった件数 0）を記録する。固定先の更新のコミットと `.npmrc` のコミットのそれぞれの後にも `npm ci` と `./gradlew e2eTest` を流す。前提として Mailpit を profile `mail` で起動し、長い実行は `caffeinate -i` で全体を包む | `nfr-requirements/tech-stack-decisions.md` の NFR9.3・NFR9.4・NFR9.8、`build.gradle.kts` の `e2eTest`、`project.md` の学び（caffeinate） |
| `frontend/playwright.config.ts` の直しは B5 で行う。reporter から html を外し、trace の既定を `off`（`E2E_TRACE` で `on`・`retain-on-failure` に切り替え、決まった3つの外の値は設定の読み込みで止める）。110・120 は `test.use({ trace: e2eTraceMode() })` を置く。json の報告（`test-results/e2e-results.json`）は変えず、Build and Test が写す唯一の報告にする。この変更は 010〜100 と 130 の報告にも効く（E2E のファイルは変えない） | `nfr-design/security-design.md` 3.2・12節の 1〜3、承認の場の決定の受け入れ 2 |
| 報告の部品 `frontend/playwright-secret-check-reporter.ts` を B5 で広げる。探す値は環境変数の3つ・値のファイル（`test-results/` の下、権限 600、探し終えたら消す）・値の形。探す先は json の報告（`attachments[].body` と `stdout`・`stderr` の `buffer` の base64 を復号して探す）と `test-results/` の下のすべてのファイル（zip は `node:zlib` で展開）。`runTag` は乱数の部分 16 文字以上で、24 文字以上のときだけ単独で探す。読めないものは失敗にし、値は表示せず種類と件数だけを出す。新しい依存を足さない | `nfr-design/security-design.md` 3.4・3.5・3.8、承認の場の決定の R-01・R-06 |
| 前の実行の `frontend/playwright-report/` が残っていると、報告の部品が前の html の報告として探し、見つかれば失敗にして消す手順を示す（見つからなくても消すよう警告する）。`e2eTest` が自動で消すことはしない（見つける前に消すと確かめにならないため）。B5 の最初の実行で一度起きうることは受け入れ済みで、結果をコード生成の記録に書く。わざと残した報告で失敗することは 3.8 の (6) で確かめる | `nfr-design/security-design.md` 3.5 の 6・3.8、承認の場の決定の受け入れ 1 |
| `README.md` の E2E の節は B5 のコード生成で直す。110・120 の行と「110 について」「120 について」の節（本数の数え方、差し替えの口、測りの記録、Mailpit の起動が要ること）、結果は json だけになること、手元だけ trace を有効にする手順（その実行は合否に使わず、見た後に `frontend/test-results/` を消す）、失敗の画面の写しで部品が失敗したときに確かめてから消す手順、前の `playwright-report/` を消す手順。`playwright.config.ts` の冒頭の説明も直す | `nfr-design/security-design.md` 3.6・3.7・12節の 7、`nfr-design/logical-components.md` 3節 |
| make-you-chic-ui の固定先を `077f5b4` から `3481488` 以降へ上げる。取り込む具体のコミットは B5 のコード生成の計画で決め、計画の承認の前に Dropdown の押せない項目と理由の文などの口（部品 2.4・2.5）があることを確かめる。中身は変えない | `nfr-requirements/tech-stack-decisions.md` の NFR9.3、`project.md` の Forbidden・Mandated |
| 固定先の更新は承認を得た専用のコミットで行い、更新前後のハッシュを記録する。`frontend/.npmrc` の `ignore-scripts=true`（`engine-strict=true` は残す）は別のコミットにする。統合は短命のブランチから `develop` へ fast-forward（squash でない）とし、統合の前に `./gradlew verify` を通す。コミットの細かい分け方（画面の作業を何コミットにするか）はコード生成の計画で決める | `team.md` の Way of Working、`bolt-plan.md` の B5、`nfr-requirements/tech-stack-decisions.md` の NFR9.3・NFR9.4、`project.md` の学び（前の Intent の C1〜C6 と fast-forward） |
| CI はサブモジュールを固定先のコミットで取得する（`submodules: true`）。そのため、依頼者が `develop` を push する前に、取り込む make-you-chic-ui のコミットがそのリポジトリの公開の側にあることを確かめる（無いと CI の取得で失敗する）。push は依頼者が行う | `.github/workflows/ci.yml`、`team.md` の Way of Working・Deployment |
| `ignore-scripts=true` が効くのは `frontend/` の中のインストールだけで、`verifyPrepare` の `vendorInstall`（`vendor/make-you-chic-ui` の中の `npm ci`）には効かない。`npx playwright install chromium` はインストールのスクリプトではないため影響しない。足した後に `npm ci`・`./gradlew verify`・`./gradlew e2eTest` が手元で通ること、push の後の CI が通ることを記録する | `nfr-requirements/tech-stack-decisions.md` の NFR9.4、`build.gradle.kts` の `vendorInstall` |
| `./gradlew verify` と CI の中身（段の並び）は変えない。U5 の部品のテスト・fast-check・カバレッジの下限（行 80%・分岐 70%、除外を増やさない）・初回の JavaScript の大きさの警告（`frontendBundleSize`）・OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む）は既存の段で動く。`frontend/package.json` の差分は無く、lockfile の差分は make-you-chic-ui の版の分だけ。`backend/` に差分が無いことを記録する | `nfr-design/logical-components.md` 8節、`nfr-design/security-design.md` 8節 |
| E2E の報告・値のファイルは開発者の PC の `frontend/test-results/` の下にだけ置かれ、`.gitignore` の対象のままでコミット・共有しない。E2E は CI の外のため、CI の成果物に報告は載らない | `nfr-design/security-design.md` 9節、`.gitignore` |

## Q1: B5 で `playwright.config.ts` を直すまでの間（B2・B4 の E2E の実行）、html の報告と trace に残りうる仮の資格情報をどう扱いますか？

背景: 今の `frontend/playwright.config.ts` は html の報告（`frontend/playwright-report/`）を作り、失敗のときの trace（`retain-on-failure`）を残します。Playwright 1.63 の html の報告は `fill` の手順の題に入れた値を書くため、既存のログインの手伝い（`loginAsAdmin`・`loginWithForm`）が入れる初期管理者のメールアドレスと仮のパスワードが、成功の実行でも残ります（`nfr-design/security-design.md` 3.2）。値は実行ごとに作る仮のもので、E2E の一時の内部DB の中でだけ使えますが、メールアドレスは実行ごとに変わりません。直しは承認済みの設計で B5（U5）に置かれ、U4 の基盤の設計の要約（Looks correct）でも「U4 は先取りしない」としました。ところが、統合の前に `./gradlew e2eTest` を流す Bolt は B2（U2・U4）と B4（U3 の Q1 A で足した）もあり、その間は報告が PC に残ります。html の報告は次の実行で上書きされ、B5 で html を外すと最後の報告は消されずに残ります（B5 の報告の部品が見つけて失敗にし、消す手順を示すことは受け入れ済み）。同じ段の U4 のレビューの R-03 は、130 の実行の後に報告の置き場が管理外であることと共有していないことを記録し、B5 が遅れるなら先取りを諮るよう勧めています。

A. 直しは承認済みの設計のとおり B5 で行う（先取りしない）。B2・B4 では `./gradlew e2eTest` の結果を json の報告から記録した後に `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことをその Bolt のコード生成の記録に書く。B5 の最初の実行で前の html の報告が残っていないため部品の失敗は起きにくくなるが、前の報告で失敗することは 3.8 の (6) でわざと残した報告で確かめる。B5 が B4 の後すぐに始まらないときも、この扱いのまま進める（推奨）
B. `playwright.config.ts` の直しのうち、html を外すことと trace の既定を `off` にすることだけを B2 に前倒しする（報告の部品の拡張・110・120・`e2eTraceMode()` は B5 のまま）。B2 から報告に値が残らなくなるが、承認済みの NFR 設計（B5 に置く）と U4 の確認済みの要約（先取りしない）との差を、この段の成果物と B2 の計画に記録する。B2 の計画と E2E の確かめが増える
C. 何もしない。B2・B4 の報告は README の「共有しない」の決まりで扱い、B5 の最初の実行で報告の部品が前の html の報告を見つけて失敗にしたときに消し、その結果を記録する（承認の場で受け入れた形のまま）。B4 の後から B5 の最初の実行までの間、報告が PC に残る
X. Other (please specify)

[Answer]: A

## Infrastructure Design の要点（答えの後に要約として確かめる）

1. **配備と配信（`infrastructure-specification.md`）**: 既存の WAR・イメージ・`compose.yaml` の `app` のまま。変わるのは同梱の `dist` の中身だけ（画面は `lazy` で遅れて読む塊）。CSP・キャッシュの扱い・資源の上限・`.env` は変えない。戻しは直前の版のイメージへ戻すだけ。
2. **共有するもの**: E2E の WAR・内部DB・初期管理者、`playwright.config.ts` と報告の部品、Mailpit、make-you-chic-ui の固定先、`frontend/.npmrc` を、`nfr-design/logical-components.md` 9節の表のとおり記録する。新しいサーバーの資源は無い。
3. **監視（`monitoring-design.md`）**: 画面の側には何も足さない。管理の API は U3 の監視の設計と監査で見る。画面の時間は手元の 120 の測りで記録だけ（成否にしない）、SLO は置かない。
4. **検査の流れ（`cicd-pipeline.md`）**: `./gradlew verify` と CI の段は変えない。110・120 は `./gradlew e2eTest` の中で `verify` と CI の外に置き、B5 の統合の前（固定先の更新と `.npmrc` のコミットのそれぞれの後にも）とリリースの前に手元で流す（Mailpit の起動、`caffeinate -i`）。
5. **E2E の設定と報告**: B5 で html を外し trace の既定を `off` にする（`E2E_TRACE`・`e2eTraceMode()`）。報告の部品は json の添付の base64 を復号し、`test-results/` の下のすべてと前の `playwright-report/` を探す。B2〜B4 の間の扱いは Q1 の答えに従う。README の E2E の節と設定の冒頭の説明を直す。
6. **固定先の更新と統合**: 専用のコミットで前後のハッシュを記録し、`.npmrc` は別のコミット。短命のブランチから `develop` へ fast-forward。push の前に取り込むコミットが make-you-chic-ui の公開の側にあることを確かめる。
7. **記録すること**: `backend/`・`application.yaml`・`frontend/package.json` に差分が無いこと、lockfile の差分が make-you-chic-ui の版の分だけであること、`./gradlew e2eTest` の結果（010〜100・130・110・120）、報告の部品の結果とわざと値を入れた確かめ（3.8 の (1)〜(8)）、`user-admin-screen-ms` の値、初回の JavaScript の大きさの前後の値、CI の結果。
8. **traceability**: 基盤に関わる NFR（NFR3.4・NFR3.5・NFR5.1〜NFR5.3・NFR7.3・NFR9.1〜NFR9.5・NFR9.8・NFR9.9 など）を、3つの成果物の節と設定・タスク・ファイルに結ぶ。新しい枝番は足さない。

## Consolidated Summary Confirmation

答えのまとめ:

- 基盤の設計の要点は、上の「決まっていること」と要点 1〜8 のとおり。
- Q1: A。`playwright.config.ts` の直しは承認済みの設計どおり B5 で行う。B2・B4 では `./gradlew e2eTest` の結果を json の報告から記録した後に `frontend/playwright-report/` と `frontend/test-results/` を消し、消したことと共有していないことをその Bolt のコード生成の記録に書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
