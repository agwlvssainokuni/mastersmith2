# 配備の流れ（CI/CD）— u2-display-time-zone

出典: NFR Design（`construction/u2-display-time-zone/nfr-design/` の `logical-components.md`・`security-design.md`・`performance-design.md`・`reliability-design.md`・`scalability-design.md`・`observability-design.md`・`reviews/review-01.md`）、NFR Requirements、機能設計、`team.md`・`project.md`（Deployment・Testing Posture）、既存の流れの実物（読み取りだけ: ルートの `build.gradle.kts` の `verifyStages`・`e2eTest`、`backend/build.gradle.kts`、`.github/workflows/ci.yml`、`Dockerfile`、`compose.yaml`、`backend/src/main/resources/db/migration/`（V13 まで）、`perf/k6/scenarios.js`・`perf/README.md`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`docs/deployment.md`・`docs/internal-db.md`・`docs/verification.md`）。書き方は同じ Intent の前の単位（`construction/u6-app-icon-assets/infrastructure-design/`・`construction/u3-target-db-time/infrastructure-design/`）と同じ。

U2 は、利用者ごとの表示のタイムゾーンを足す単位（種類 service）。バックエンドは `users` に NULL 可の列 1 つを足す移行 V14 と、既存の `GET`・`PUT /api/me/preferences` の項目 1 つの追加。画面は配布の提供元・選択肢・管理の画面の時刻の欄・構造の検査 1 つ。新しいサーバー・表・API の道・環境変数・ネットワーク・コンテナ・クラウドの資源を持たない。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤は作らない（`project.md` Deployment）。判断が分かれる論点は NFR 設計の決定（`LC`・`SD`・`PD`・`RD`）と下の決定で決まっているため、質問は作らず、既にある検査の流れと配備の記録として書く（`project.md`）。

## 1. 流れの中の位置

| 段 | 内容 | U2 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-<bolt>`）で作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（0 準備 → 1 フォーマット → 2 リンタ → 3 ライセンスヘッダー → 4 ビルド → 5 単体テスト → 6 結合テスト → 7 カバレッジ → 8 安全の検査 → 9 成果物） | 2・5・6・7 に足すものがある（2節）。段の並びと `verifyStages` の定義は変えない |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | **統合の前に流して通す**。`frontend/src` のテストでないファイルを変えるため、`team.md` の「frontend の本体」に当たる。新しい流れは足さず、既存の 080・100・110 を書き換える（2.5節。本数に数えない）。Intent の最後（Build and Test）とリリースの前にも 1 回流す |
| 統合 | `develop` へ squash マージ（1 Bolt が 1 コミット。複数の Bolt があれば計画の承認で決める）。メッセージは日本語 | なし。`vendor/` の固定先は動かさないので fast-forward の例外は使わない |
| CI | GitHub Actions が `develop` へのプッシュ時に `./gradlew verify` を流す（`.github/workflows/ci.yml`）。`origin` への `git push` は依頼者自身が行う | `verify` が 2節の検査を含むため、`ci.yml` の変更は無い |
| 成果物 | `frontend/dist` を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）。版はコミットのハッシュで識別 | 画面の変更と V14 が同じ WAR に入る。単独では配備しない |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認とスモークテスト | 3節（移行があるため、内部DB の複写・戻し・スモークテストを書く） |

## 2. 検査の関門（この単位が足すもの）

実行順は `verify` の既存の段の並びのまま。どれも失敗すると `verify` 全体が失敗し、統合と CI を止める。コンテナの実行環境（colima）が前提で、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおりシェルに渡す。渡さずに対象DB のテストが SKIPPED になった状態では統合しない（`team.md`）。

### 2.1 段ごとの追加

| 段 | 足す検査 | 呼び出し | 失敗で止まる基準 | 根拠 |
|---|---|---|---|---|
| 2 リンタ | ブラウザの既定のタイムゾーンを暗に使う書き方を止める ESLint の規則 1 つ（2.2節） | 既存の `frontendLint`（`oxlint . && eslint .`） | `src/` のテストでないファイルに、止める書き方が 1 件でもあれば失敗 | `SD6`・`LC9` |
| 5 単体テスト（JUnit） | 名前の門（`jqwik` の性質を含む）、3 状態の入力、検証前の値の型の伏せ字、移行のファイルの形（列の追加 1 文だけ）、`UserPreferencesServiceTest` の書き換え、`UserBoundaryArchitectureTest` の維持 | 既存の `test` | 1 件でも失敗すれば失敗。失敗時の乱数の種を記録する | `SD1`・`SD2`・`SD4`・`SD8` |
| 5 単体テスト（Vitest） | 配布の提供元（状態の遷移・世代・未ログインで捨てる・401）、選択肢の組み立て（5 回の中央値の測り・`memo`）、時刻の欄の部品（`loading`・`error`・`ready`、整形の呼び出し回数）、ESLint の規則の効き（`Linter` の単体テスト）、管理の画面の書き換え（旧書式 `JST`・`GMT+9` の検索 0 件）、vitest-axe を部品ごとに 1 件、機械の既定のタイムゾーンを変えても同じ結果になる確かめ | 既存の `frontendTest`（`npm run test`） | 1 件でも失敗すれば失敗 | `PD3`・`PD4`・`PD7`・`RD4`・`RD5`・`SD6`・`SD9` |
| 6 結合テスト | 保存の 1 文（3 状態・5 項目が 1 文で確定・本人の行が無いとき 401・待ち合わせで重ねた後勝ち）、`GET`・`PUT /api/me/preferences` の応答（5 つ目は常に出る・前の版の画面の PUT が保存済みの名前を消さない・ログインと現在の利用者の応答は変わらない）、`UserSchemaIT` への列の確かめ、**前の版との互換の新しいテスト**（3.1節）、`AuditMigrationCompatibilityIT` の期待を 13 から 14 へ、`MePreferencesLockTimeoutLeakIT` が新しい文でも通ること、`PreferencesSecretLeakIT`（TRACE を有効にして名前が出ない）、外部へ送る設定を有効にした結合テスト（名前がトレースの属性に出ない）、400 のログが WARN 以下で 1 回だけ | 既存の `integrationTest`（組み込みの H2。対象DB のコンテナは使わない） | 1 件でも失敗すれば失敗 | `RD1`〜`RD3`・`SD3`・`SD4`・`OD2` |
| 7 カバレッジ | 既存の下限（行 80%・分岐 70%、全体の合計）。`user.domain`・`user.repository`・`user.service`・`user.web` は `packagesJudgedByTotal` の 7 パッケージに載っておらず、パッケージごとの下限の対象。画面は全体の合計の `thresholds` | 既存の `jacocoTestCoverageVerification`・`frontendCoverage` | 下限を下回れば失敗。除外を増やして逃れない | `team.md` Testing Posture |

内部DB の結合テストは組み込みの H2 で行う（コンテナにしない。`team.md`）。U2 が足す結合テストは H2 だけで足り、対象DB のコンテナの時間は増えない。

### 2.2 構造の検査（ESLint の規則 1 つ）

| 項目 | 内容 |
|---|---|
| 置き場 | `frontend/eslint.config.js`。選択子の本体は 1 つのモジュールに置き、設定とテストが同じものを読む |
| 範囲 | `src/` のテストでないファイル全体（`src/shared/` を含む）。`e2e/`・`vendor/` は対象外 |
| 例外 | S1・S2 を `browserTimeZone.ts` の 1 か所だけ |
| 落とし穴 | フラットな設定は、同じ規則を後の設定が前を置き換える。新しい設定は既存の 2 つの選択子（`export default` と `enum` の禁止）を含めて書き、定数に取り出して共有する |
| `no-console` | 既存の `businessDataConsoleRule` の `files` に `src/app/display-settings/` の新しいファイルを足す（規則を足すだけで緩めない） |
| 取りこぼしの補い | 選択子は **項目の有無** しか見ないため、`{ timeZone }` で `timeZone` が `undefined` になりうる書き方（省略可の引数をそのまま渡す）は通る。補いとして、時刻の欄の部品と選択肢の組み立ての `timeZone` の引数の型を **省略不可の `string`** にする（型の検査 `tsc --noEmit`、verify の 4 で止まる）。機械の既定のタイムゾーンを変えても同じ結果になる単体テストで二重に守る。消す予定の `formatDateTime.ts`・`lockedUntil.ts` は `timeZone?: string` を `{ timeZone, … }` で渡す形で、まさにこの取りこぼしの例 |
| 確かめ | `Linter` の単体テストで、止める断片と通す断片を固定する（`security-design.md` の SD6 の一覧）。実物の `npm run lint` が既存の `src/` に 0 件で通ること |

### 2.3 カバレッジと時間

- バックエンド: `src/main` に手を入れるのは `user` の 4 層。いずれも `packagesJudgedByTotal` の一覧に無い。コード生成の計画で、一覧のパッケージ（`common.web` など）に `src/main` で触れないことを確かめ、触れるときはテストを足して下限を満たし一覧から外す（一覧は増やさない）。
- フロントエンド: 新しい部品（`src/app/display-settings/` の配布の提供元・時刻の欄、`src/features/preferences/` の選択肢）はテストで覆う。除外を増やして下限を逃れない。新しい機能のフォルダのカバレッジの値は Build and Test で記録する。
- `./gradlew verify` の時間は直近の記録で約 28 分（CI の上限 `timeout-minutes: 60` の 7 割の約 42 分が線）。U2 の追加は H2 の結合テストと Vitest で、分のオーダーに収まる見込み。Bolt の後と Build and Test で測って記録し、42 分を超えたら依頼者に諮る。

### 2.4 負荷の場面（k6）

| 項目 | 内容 |
|---|---|
| 台本 | `perf/k6/scenarios.js` に `preferencesSaveTimeZone` を足す（1 回の繰り返しに要求 1 つ。`set`・`clear`・`invalid` を交互）。既存の 3 場面（`preferencesGet`・`preferencesSave`・`preferencesInvalid`）の台本と閾値は変えない |
| 周辺 | `perf/k6/thresholds-test.sh` の期待の表と `perf/README.md` の場面の表・閾値の表を同じ変更で直す。`verify` の閾値の部品の確かめで台本の登録の漏れを拾う |
| 台本の読み込み | `k6 inspect --include-system-env-vars` で確かめる（付けないと場面の名前が `undefined` になる） |
| 流す場所 | 使い捨ての環境（仮の署名鍵・仮の利用者。`perf/README.md`）。配備したアプリと本物のデータ・監査を汚さない。**CI では流さない** |
| 流す時期 | Build and Test（この Intent の流れに Performance Validation の段が無く、使い捨ての環境を手元で用意できるため。`project.md`）。持ち主: Build and Test。判定は `PD6` のとおり（`http_req_duration` の p95 と `iteration_duration` の p95 の両方、`checks` の率 1。目標の 1 秒は緩めない） |
| 記録 | 結果（`--summary-export`）は伏せ字の道具（`perf/redact-k6-evidence.mjs`）を通してから `evidence/` に置き、`gitleaks dir` を流す。長い試験は `caffeinate -dims` で包み、後で `pmset -g log` で眠りが無かったかを確かめる。確かめの結果を見てから環境を片付ける |
| setup | 前の場面が残した状態に頼らず、試験用の利用者 10 人の表示のタイムゾーンを消してから始める |

### 2.5 E2E（`verify` と CI の外）

- 流す時期: **統合の前**（`frontend/src` のテストでないファイルを変えるため）に全件、Intent の最後（Build and Test）とリリースの前にも 1 回。
- 書き換え: 080・100・110 の見本に 5 つ目を足し（`preferencesFixtures.ts`）、取得の回数を名前つきの定数で持ち、`expect.poll` で待って確かめる（`RD6`）。新しい流れは足さない。書き換えは本数に数えない。日時の代表の流れ 1 本は業務データの単位が受け持つ。
- axe: 選択と管理の画面の時刻の欄を、ブランドカラーとテーマのすべての組で。規則を外す・`exclude` する・既知の違反の一覧に足す形で通さない（`project.md` Forbidden）。375px の幅のはみ出しも確かめる。全件の通過と違反 0 件を件数で Build and Test に記録する。
- Playwright が流す WAR とアプリの起動のタイムゾーンは今のまま（3.4節）。時差を別にして確かめるテストはその時差を明示に指定する。

### 2.6 依存・セキュリティ・秘密情報

- 新しい依存を足さない（`frontend/package.json`・`package-lock.json`・`backend/gradle.lockfile`・`build.gradle.kts` の依存・`config/npm-build-tools.txt` を変えない）。ライセンスの確認は要らない。`NFR1.5` のとおり。
- OSV-Scanner・SpotBugs＋FindSecBugs・Gitleaks は既存のまま `verify` の 8 で働く。保存の 1 文は **名前つきの束縛** で書き、利用者の入力を文字列のまま SQL に組み込まない（`SQL_` の指摘は priority に依らず止まる。除外は足さない）。
- 新しい秘密・環境変数・資格情報は無い。テストデータの名前は公開の地域の名前（`Asia/Tokyo` など）だけ。

## 3. 配備・戻し方・スモークテスト

### 3.1 内部DB の移行 V14（広げてから縮める）

| 項目 | 決定 |
|---|---|
| ファイル | `backend/src/main/resources/db/migration/V14__u2_display_time_zone.sql`。`ALTER TABLE users ADD COLUMN display_time_zone VARCHAR(64);` の 1 文だけ（ライセンスのコメントを除く）。NULL 可・既定なし・索引なし・制約なし |
| 広げる側だけ | 前の版のアプリが使う列は消さない。縮める移行は予定しない（この列は残り続ける）。`team.md` の「縮める側」の手順（先に読まない版を配備・列を消す移行を別の配備・複写を戻す戻し）は当たらない |
| 起動時 | 失敗したら起動しない（既存の移行と同じ）。H2 の列の追加は表を書き換えず、利用者の表は小さいため、起動の時間への影響は無視できる |
| 前の版との互換 | 前の版（V1〜V13 の移行だけを持つ）の Flyway（検証あり）が、V14 まで当てた内部DB で止まらず（実行 0 件・最大の版は 14）、V14 の前に前の版が書いた利用者の行が読め、足した列が `NULL` で、前の版の列だけで利用者の行を足せ、4 項目を更新しても足した列が変わらないことを、`AuditMigrationCompatibilityIT` と同じ形の新しい結合テストで確かめる（Spring を起動せず、Flyway とマイグレーションのファイルだけ） |
| 前の版の画面の PUT | 4 項目だけを送る PUT（`displayTimeZone` を送らない）が、保存済みの名前を消さずに 200 を返すこと（`MePreferencesApiIT` に足す） |
| 最後の版を固定した既存のテスト | `AuditMigrationCompatibilityIT` の `CURRENT_LAST_VERSION`（13 → 14）と説明の「V10〜V13」を直す（新しい最後の版に合わせるだけで、期待を緩めない）。`RoleMigrationCompatibilityIT` は版 10〜12 の写しで V14 の影響を受けない。`AuditSchemaIT` は最後の版を数えない。件数は計画で検索して確定する（4節 H3） |
| 文書 | `docs/deployment.md` の「スキーマの変更（Flyway）」に V14 の項を足す（`display_time_zone` の列、前進のみ、戻している間は選択が使えず管理の画面の時刻の書式が前の版に戻る、列と保存した名前は残る） |

### 3.2 配備の手順（内部DB の複写とアプリの入れ替え）

Dockerfile は `COPY backend/build/libs/mastersmith.war /app/mastersmith.war` だけで、**イメージを作り直しても WAR は作り直されない**。手元の WAR が古ければ V14 も画面も入らない（`project.md`）。

1. 配備の前に、`./gradlew verify` が通った状態の WAR を使う。WAR の SHA-256 を記録し、`unzip -l` で `V14__u2_display_time_zone.sql`（`WEB-INF/classes/db/migration/`）と画面の成果物が入っていることを確かめる。
2. **内部DB の複写（バックアップ）を取る**。理由: V14 は起動のたびに内部DB へ適用される前進のみの移行で、移行が途中で失敗したとき・データが壊れたときの戻し先が複写だけになる（`docs/deployment.md`・`docs/internal-db.md`）。H2 のファイルはアプリが動いている間は触れないため、**入れ替えのためにアプリを止める時間にまとめて取る**（止める時間は増えない。前の Intent で約 7〜53 秒）。複写はリポジトリの外（ホームの下の `~/.mastersmith-backup/`）に置き、権限 600、コミットしない。複写の中身を表示しない。
3. 戻し用のタグ（`pre-timezone-ui-polish` など）を、いま動いているイメージに付けて残す（`docs/deployment.md` の「戻し方」）。
4. `docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID が `mastersmith:local` の ID と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す（`project.md`・`team.md`）。監視のコンテナなどを入れ替えたときは `RepoDigests` も `compose.yaml` と照らす。
5. ヘルスチェックが通るまで配備完了とみなさない。起動のログで Flyway が V14 を 1 件適用したことを、件数と版の番号だけで確かめる（値を出さない）。
6. 配備の前の k6 は流さず、Build and Test の結果を正とする（3.5節）。

### 3.3 戻し方

- **戻し方**: 直前の版のイメージ（戻し用のタグ）での再配備だけ。`MASTERSMITH_IMAGE_TAG=<タグ> docker compose up -d --no-build app`（`--build` を付けない。戻している間は毎回タグを付ける）。内部DB の巻き戻しも `.env` の戻しも要らない。前の版のアプリは足した列を読まずに動く（3.1節の互換の結合テストが根拠）。
- **戻したときに起きること**（戻しの手順に書く）: 前の版の画面には表示のタイムゾーンの選択が無い／管理の画面の時刻の書式が前の版（略号つき）に戻る／列と保存した名前は残る（失われない）／新しい版へ進み直すと保存した名前が再び効く。権限のようなデータの形の変更ではないため、戻したときの権限のずれの確かめは当たらない。
- **複写を戻す戻し**: V14 の適用が失敗したとき・データが壊れたときだけ。失うもの: 複写の後に作られたデータと監査の行（`docs/internal-db.md`）。
- **戻しの練習**: 列を足すだけの前進のみの移行で、前の版が列を無視して動く根拠は互換の結合テストにある。実機の確かめとして、配備の後の複写（3.4節）を一時のボリュームに展開し、戻し用のタグのイメージを起動して健全になることを確かめる（colima の VM のメモリの余裕が約 100 MiB のため、練習の間は `lgtm` を起動しない）。練習をするかは Deployment Pipeline の手順で決め、省くときは未確認のまま残る前提（V14 を当てた配備の内部DB での前の版の起動）を Deployment Execution の記録に書く（`project.md`）。

### 3.4 スモークテスト（配備の後。手順は Deployment Pipeline、実行は Deployment Execution）

秘密情報が要る操作（初期管理者のログイン）は依頼者が行い、AI は複写とログで裏付ける（値は表示しない）。保存は **監査に残らない**（`SD4`）。ログインは既存の監査の種類で残る。依頼者が画面で何を操作したかをその場で聞き、監査の期待の件数を合わせてから数える（`project.md`）。

| 項目 | 確かめ | 期待 |
|---|---|---|
| S1 | ヘルスチェックと起動のログ | 健全。Flyway の適用が V14 の 1 件（版と件数だけ見る）。ERROR が増えない |
| S2 | 依頼者がログインし、プリファレンスの画面を開く | 表示のタイムゾーンの選択が出て、既存の利用者は「ブラウザに合わせる」が選ばれている（`NULL`）。選択肢が約 400 個あっても操作できる |
| S3 | 名前を 1 つ選んで保存（例: `Asia/Kolkata`）し、画面を読み込み直す | 保存した名前が選ばれたまま。管理の画面（利用者の一覧・招待・DSL の状態）の時刻が `YYYY-MM-DD HH:mm ±HH:mm` の形で、保存した時差（`+05:30`）になる |
| S4 | ログイン直後のブラウザの通信（開発者ツール） | `GET /api/me/preferences` が 1 回（ログイン後の配布の読み取り）。画面を移動しても増えない。プリファレンスの画面を開くと画面の読み取りが 1 回足される |
| S5 | 「ブラウザに合わせる」に戻して保存し、管理の画面の時刻 | ブラウザのタイムゾーンの時差になる。片付けとして、確かめ用に変えた値を元（未設定）に戻す |
| S6 | 無効な名前の拒否 | 画面からは作れない。スモークテストでは省き、結合テストと k6 の `invalid` で確かめ済みとして記録する。依頼者が望めば、依頼者が実行する 1 コマンド（トークンは変数に入れ表示しない）を Deployment Pipeline の手順に書く |
| S7 | 配備の後の複写（読み取りで開く）で、`users.display_time_zone` の列が在ること・`flyway_schema_history` の最大の版が 14・保存した名前の有無（値は表示せず、件数だけ） | 列が在り、最大の版 14。S5 の片付けの後は名前が入った行が 0 件 |
| S8 | 監査の件数 | 保存の操作で監査の行が増えない。ログインの分だけが増える（`FAILURE`・`UNKNOWN` は 0 件） |

**実行時のタイムゾーンの設定（4 か所）に変更が無いことの確認**: Dockerfile の `-Duser.timezone=Asia/Tokyo`、`compose.yaml` の `TZ: Asia/Tokyo`、テストの JVM の `user.timezone`（`backend/build.gradle.kts`）、E2E の起動、の 4 か所を、この単位の差分で触れていないことを配備の記録に書く（`team.md`。`-Dh2.compactThreads=1` と同じ扱い）。利用者ごとの表示のタイムゾーンは画面の側の値で、サーバーの JVM のタイムゾーンとは別。保存する時刻は UTC の時点のまま（`project.md`）。

### 3.5 配備の前の k6 の扱い

Build and Test が新しい場面と既存の 3 場面を使い捨ての環境で流す（2.4節）。配備の前の k6 は流さず、その結果を正とする（前の Intent と同じ。差があれば Deployment Pipeline の `cd-config.md` に記録する）。問題が出たら 3.3節の戻しで直前の版へ戻す。

## 4. コード生成へ引き継ぐこと

NFR 設計の `logical-components.md` と `reliability-design.md` の「後の段へ引き継ぐこと」に、この段の決定を合わせた一覧。実装の名前・ファイルの分け方はコード生成の計画で決める。

| # | 引き継ぐこと | 内容 | 出どころ |
|---|---|---|---|
| H1 | 移行と列の確かめ | `V14__u2_display_time_zone.sql`（3.1節）。`UserSchemaIT` に型・NULL 可・既定なし・既存の行が `NULL`・移行のファイルが列の追加 1 文だけ（列を消す文・ほかの表・索引・制約を含まない）の確かめを足す | `RD1`・`LC5` |
| H2 | 互換の結合テスト | 前の版（V1〜V13）の Flyway が V14 の内部DB で止まらない新しいテスト（`AuditMigrationCompatibilityIT` と同じ形）。`MePreferencesApiIT` に前の版の PUT・応答の 5 つ目のキーの確かめを足す | `RD1`・`RD3` |
| H3 | 影響の洗い出し（計画で検索して確定） | **(a) 移行の番号を前提にしたテスト**: `CURRENT_LAST_VERSION`・`V13`・`MIGRATIONS_AFTER_PREVIOUS`・`flyway_schema_history` を読むテスト。直すのは `AuditMigrationCompatibilityIT`（13 → 14 と説明の「V10〜V13」）の見込みで、`RoleMigrationCompatibilityIT`・`AuditSchemaIT`・`DatabasePersistenceIT` は件数に依らないことを確かめる。**(b) `updatePreferences` の呼び出し側**: 保存の 1 文は戻り値を行数（`int`）から 2 列の結果に変えるため、`UserRepositoryIT`（5 か所で呼び、「4 列だけ触れる」確かめを含む）と `UserPreferencesServiceTest`（`when`・`verify` で固定）が壊れる。「4 列だけ触れる」確かめは、期待を緩めず「5 列を決めた状態でだけ触れる」へ移す。`MePreferencesLockTimeoutLeakIT` は説明に出る保存の問い合わせの名前を新しい文に直す（期待を緩めない）。**(c) 結果・命令・要求・応答の型**: `PreferencesCommand`・`PreferencesResult`・`PreferencesResponse`・`PreferencesRequest` を 4 つの引数で作るテスト、`Preferences(` の生成。**(d) 画面**: `formatDateTime`・`formatLockedUntil`・`lockedUntil`・`timeZone` の props（管理の画面 3 つとテスト）、`preferencesSample`・`hasPreferencesShape`・`/api/me/preferences` を差し替える E2E（080・100・110）、`perf/README.md` の場面の表と閾値の表。洗い出した結果と件数を計画に書く | `RD1`〜`RD6`・`D7`・レビュー R-03 |
| H4 | ESLint の規則 | 2.2節のとおり。`timeZone` の値が未定義になりうる書き方の取りこぼしを、引数の型を省略不可の `string` にすることと、機械の既定のタイムゾーンを変えて同じ結果になる単体テストで補う。使われなくなる旧い整形の関数は **消す**（例外を置かない） | `SD6`・`LC9`・レビュー R-04 |
| H5 | 保存の 1 文の確かめ | 結合テストで Spring Data 上の動き（返す型の対応）とトランザクションの中の動きを確かめる。**確かめられなかったときは、読み取りを 1 つ足す形が要件の緩和になるため、緩める前に依頼者に諮る** | `D1`・`LC3` |
| H6 | 負荷の場面 | 2.4節。台本・`thresholds-test.sh`・`perf/README.md` を同じ変更で。測りは Build and Test | `PD6`・`LC10` |
| H7 | E2E の書き換え | 2.5節。回数の定数・`expect.poll`・110 の待つ対象（プリファレンスの画面を開いた後の最初の 401）。080・100 の取得の回数は差し替えを置く順を見て実測して確定する | `RD6`・`LC11` |
| H8 | 保存の SQL | 名前つきの束縛。`null` の束縛に `CAST` が要るか、`updatable = false` の列の読み取りがその後も通るか、H2 の `FINAL TABLE (UPDATE …)` が Hibernate を通るかを結合テストで確かめる。SpotBugs の除外を足さない | `T4`・2.6節 |
| H9 | 文書 | `docs/deployment.md`（V14 の項・戻したときに起きること）、`perf/README.md`、画面の説明がある文書（`docs/appearance.md` に表示のタイムゾーンの項を足すかは計画で決める）。パスは相対で書く | 3節 |
| H10 | カバレッジの確認 | `user` の 4 層と `packagesJudgedByTotal` の一覧のパッケージの扱い（2.3節）。除外を増やさない | `team.md` |
| H11 | 承認の場で依頼者に確かめる点（NFR 設計から引き継ぐ） | (1) 管理の画面で保存済みの名前を解釈できないとき案内を足さない扱いのままでよいか（`D8`）。(2) 整形の呼び出しの上限を「表示する時刻の数 + 1」と読む具体化（`D9`。NFR 設計のレビュー R-02 が、確かめる点の一覧に無いと指摘した）。承認の場でこの 2 点を並べて確かめる | `D8`・`D9`・レビュー R-02 |
| H12 | Deployment Pipeline／Execution へ | 3.2〜3.4節の手順（WAR の SHA-256 と中身・複写の取り方と置き場と権限・戻し用のタグ・入れ替えの後のイメージ ID の確かめ・スモークテスト S1〜S8・戻しの練習をするか・監査の件数の合わせ方・タイムゾーンの 4 か所の確認）を手順書にする | 3節 |

## 5. 上流との差（承認済みの記録は書き換えない）

| ID | 差 | 扱い |
|---|---|---|
| G1 | NFR 設計の `traceability.json` は、前の段（NFR 要件）の上流の行のうち **NFR5.1・NFR5.3 の行を持たない**（本文は、必須のテストと E2E の流れを足さないことを受け持つと書いている）。また、前の段の最上位の行と形が違い、上流に無い枝番（NFR1.4・NFR1.5・NFR2.3 など）が最上位の行として並ぶ。NFR1.1・NFR1.3 は他の単位が持ち主で（N/A）、この段の上流の一覧に出ない | NFR 設計の追跡は書き換えない（承認済み）。この段の追跡は、エンジンが出した上流の一覧（13 項目）のすべてに行を立て、NFR5.1・NFR5.3 を OK（割り付け先は 2.1節・2.5節）として足した。単位で足した枝番は、上流の最上位の行の target に寄せて書いた。Build and Test の要件の網羅の連鎖で、この段の追跡を正として読む |
| G2 | NFR 設計の `observability-design.md` と `logical-components.md` は、手元の監視が常に動いていないため、既存の警報が新しい項目に効くことの実機の確かめは行わない | 変えない。この段でも警報・指標を足さない（`monitoring-design.md`） |
| G3 | NFR 設計は、移行の最後の版を前提にした既存のテストを `AuditMigrationCompatibilityIT` の 1 つと見込む | 計画で検索して件数を確定する（H3）。別のテストが見つかったときは差として計画に書く |

## 6. 要注意の点

- **保存の 1 文が成り立つかは未確認**（H5）。捨ての試しは Hibernate の `createNativeQuery` までで、Spring Data と Spring のトランザクションの中の動きは結合テストで初めて分かる。要件（保存で読み取りを足さない・保存済みの値を返す）を緩める前に依頼者に諮る。
- **Dockerfile は WAR を写すだけ**（3.2節）: 古い WAR を配備しても V14 は入らない。配備の前の WAR の確かめを省かない。
- **戻している間は管理の画面の時刻の書式が前の版に戻る**。保存した名前は失われず、進み直すと再び効く（3.3節）。
- **ログインのたびに `GET /api/me/preferences` が 1 回増える**。想定内の増加で、警報のしきい値は変えない（`monitoring-design.md`）。
- **複数のタブは同期しない**（`SC4`）。スモークテストで 2 つの窓を使うときは、保存した窓以外は読み込み直すまで古い値のまま。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/`（`logical-components.md`・`security-design.md`・`performance-design.md`・`reliability-design.md`・`scalability-design.md`・`observability-design.md`・`reviews/review-01.md`・`traceability.json`）
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 前の単位の書き方: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/infrastructure-design/cicd-pipeline.md`・`construction/u3-target-db-time/infrastructure-design/cicd-pipeline.md`
- 既存の流れの実物（読み取り）: `build.gradle.kts`・`backend/build.gradle.kts`・`.github/workflows/ci.yml`・`Dockerfile`・`compose.yaml`・`backend/src/main/resources/db/migration/`・`perf/k6/scenarios.js`・`perf/README.md`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docs/deployment.md`・`docs/verification.md`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 内部DB の複写は、入れ替えのためにアプリを止める時間にまとめて取れ、止める時間は目に見えて増えない（前の Intent で約 7〜53 秒）。Deployment Pipeline の手順で確かめる。
- [assumption] 前の版の Flyway（V1〜V13）は、V14 を適用済みの内部DB を検証で止めない（既存の設定で、V9 などの前の戻しの記録と合う）。新しい互換の結合テストで確かめる。持ち主: Code Generation。
- [assumption] 戻しの練習（配備の後の複写を一時のボリュームで前の版のイメージに起動する）を行うかは、colima の VM のメモリの余裕（約 100 MiB）を見て Deployment Pipeline の手順で決める。省くときは未確認の前提を記録する。
- [assumption] `user` の 4 層は `packagesJudgedByTotal` に載っておらず、パッケージごとの下限を満たせる。一覧のパッケージに `src/main` で触れるかは計画で確かめる。
- [assumption] 080・100 の取得の回数は配布 1 回 + 画面 1 回の 2 回。差し替えを置く順を見て計画で実測して確定する。
- Unverified: 保存の 1 文の Spring Data 上の動き（H5）、jsdom での 400 個の選択肢の組み立ての時間（100 ミリ秒、5 回の中央値）、k6 の既存 3 場面と新しい場面の p95、実ブラウザ（Chromium 以外）での名前の一覧と時差の文字、`verify` と CI の実際の時間、V14 を適用した配備の内部DB での前の版の起動（戻しの練習をしたとき）。
- 承認の場で依頼者に確かめる点: H11 の 2 点（`D8`・`D9`）。
- 質問にした点は無い（判断が分かれる論点は、NFR 設計の決定と上の決定で決まっている）。
