# 信頼性・互換と戻しの設計 — u2-display-time-zone

出典: NFR Requirements の `reliability-requirements.md`（NFR4.1.1〜NFR4.1.8・NFR4.3.1・NFR4.3.2）・`scalability-requirements.md`（NFR2.3.3）、機能設計（`rules.md` の BR2.1・BR2.10・BR2.11・BR2.14〜BR2.16・BR2.21、`functional-spec.md` の 1.2・1.3・5 節）、同じ段の `logical-components.md`、`team.md`（Deployment・Testing Posture）、既存のコード（読み取りだけ: `V7__u2_user_preferences.sql`・`V13__u2_business_data_audit_columns.sql`・`AuditMigrationCompatibilityIT`・`RoleMigrationCompatibilityIT`・`AuditSchemaIT`・`MePreferencesLockTimeoutLeakIT`・`WorkRoleProvider`・`frontend/e2e/`）。

可用性の目標は既存のまま変えない（新しい依存先・新しい常駐の処理を足さない）。この文書は、移行・前の版との互換・戻し、保存の一貫性、画面の読み込みの失敗と回復、E2E とテストへの影響を設計する。決定の番号は `RD<n>`。

## RD1. 移行（NFR4.1.1・NFR4.1.3・NFR4.1.4）

- **ファイル**: `backend/src/main/resources/db/migration/V14__u2_display_time_zone.sql`（今の最後の `V13` の次）。内容は `ALTER TABLE users ADD COLUMN display_time_zone VARCHAR(64);` の 1 文だけ（ライセンスのコメントを除く）。NULL 可・既定なし・索引なし・制約なし。既存の行は移行の直後に `NULL`（ブラウザに合わせる）。前進のみで、列を消す移行は作らない。
- 失敗したら起動しない（既存の移行と同じ）。H2 の列の追加は表の書き換えを伴わず、利用者の表は小さいため、起動時間への影響は無視できる。
- **広げてから縮める**: この Intent は「広げる」側だけで、前の版のアプリが使う列は消さない。縮める移行は予定しない（表示のタイムゾーンの列は残り続ける）。
- **確かめ（結合テスト）**:
  - 列の型・NULL 可・既定なしと、既存の行が `NULL` であること（`UserSchemaIT` に足す）。移行のファイルが列の追加 1 文だけで、列を消す文・ほかの表の変更・索引・制約を含まないこと。
  - **前の版との互換**（`AuditMigrationCompatibilityIT` と同じ形の新しいテスト）: 前の版は V1〜V13、今の版は V14 まで。前の版の Flyway（検証あり）が V14 まで当てた内部DB で止まらず（実行 0 件、最大の版は 14）、V14 の前に前の版が書いた利用者の行が V14 の後も読め、足した列が `NULL` であり、V14 の後に前の版の列だけで利用者の行を足せる・4 項目を更新できて、更新が足した列を変えないこと。
  - **前の版の画面の PUT**（4 項目だけで `displayTimeZone` を送らない）が、保存済みの名前を消さずに 200 を返すこと、ログイン・現在の利用者の応答に表示のタイムゾーンが無いこと（`MePreferencesApiIT` に足す）。
- **最後の版の番号を前提にした既存のテスト**（NFR4.1.4。D7）: 読み取りで確かめた結果、直すのは `AuditMigrationCompatibilityIT` の `CURRENT_LAST_VERSION`（13 → 14）と、その説明の「V10〜V13」の 1 つだけ（`MIGRATIONS_AFTER_PREVIOUS` は式のため自動で 4 → 5 になる）。直す理由は、テストを緩めることではなく、新しい最後の版に期待を合わせること。`RoleMigrationCompatibilityIT` は版 10・11・12 の写しを固定しており、V14 の影響を受けない。`AuditSchemaIT` は最後の版を数えず、V13 の列の確かめだけ。Code Generation の計画で、移行の番号・`flyway_schema_history` を読むテストを検索して件数を確定する。
- **戻し**（NFR4.1.3）: 前の版のイメージだけで戻す（`.env` の変更なし・内部DB の巻き戻しなし）。戻したときに起きることを戻しの手順（Deployment の段）に書く: 前の版の画面には選択が無く使えなくなる、管理の画面の時刻の書式が前の版（略号つき）に戻る、列は残る、保存した名前は失われない、新しい版へ進み直すと保存した名前が再び効く。前の版のアプリは足した列を読まず動く（上の互換のテストが根拠）。権限のようなデータの形の変更ではないため、戻したときの権限のずれの確かめは当たらない。

## RD2. 保存の一貫性と失敗（NFR2.3.3・NFR1.4.2）

- **5 項目は 1 文で確定する**（LC3）。`displayName` を含む 4 項目と表示のタイムゾーンの列が、同じ文で一緒に書かれるか、まったく書かれない。一部の項目だけ新しい値が残らない。検証に落ちた要求は文を出さない（内部DB を変えない）。
- 同じ利用者の保存が重なったときは、行の排他の順に後の保存が残る（今のプリファレンスと同じ後勝ち）。他の利用者の行に影響しない。確かめ: 2 つの保存を **待ち合わせ**（スレッドの数・時間に頼らない。既存の行の排他を持つ手伝い `RowLockHolder` の形）で重ね、保存後の 5 項目が片方の要求の値でそろっていること。
- 本人の行が無い（削除された）ときは、文の結果が空になり、今の `AUTHENTICATION_REQUIRED`（401）。確かめ: 行を消した後の PUT が 401 で、内部DB に行が増えないこと。
- **排他の待ちの上限切れ**: 例外の種類で扱いが決まる `GlobalExceptionHandler`（想定外の例外は例外のクラスの名前だけを ERROR に出し、応答は 500 `INTERNAL_ERROR`）に乗る。確かめ: 既存の `MePreferencesLockTimeoutLeakIT`（別の接続が行を持ち続ける）が、新しい保存の文でも通ること。テストの説明に出てくる保存の問い合わせの名前（`updatePreferences`）が変わる場合は、説明と照合する名前を新しい文に直す（期待を緩めない）。

## RD3. 前の版との互換（応答の形。NFR4.1.5）

- 保存・読み取りの応答は **項目を足すだけ**。既存の 4 項目（`displayName`・`language`・`theme`・`fontSize`）の名前・型・必須・並びを変えず、5 つ目を末尾に足す。5 つ目は **常に出し**（未設定は `null`、省略しない。アプリは null を省く設定を持たない）、名前の形の再検証はしない（BR2.6）。
- 誤りの形（`REQUIRED`・`INVALID_VALUE`）と並びを変えない。`displayTimeZone` の誤りは、既存の項目と同じ形で最後に並ぶ。
- ログイン・現在の利用者の応答の `preferences`（言語・テーマ・文字の大きさ）は変えない。表示のタイムゾーンはログイン後に 1 回 `GET /api/me/preferences` で読む（契約 C3）。
- 確かめ: 既存の保存・読み取り・ログインの結合テストが変更なしで通る（ただし 4 つの引数で結果の型・命令の型を作るテストは Code Generation の計画で洗い出して直す）。応答の JSON が既存の 4 項目だけを読む型で読めること、5 つ目のキーが未設定でも出ること。

## RD4. 画面の読み込みの失敗と回復（NFR4.3.1・NFR4.3.2）

提供元（LC6）の状態の遷移は、機能設計の状態図のとおり。この段では **世代の規則** と **失敗の扱い** を決める。

| 出来事 | 世代 | 結果 |
|---|---|---|
| ログインした（未ログイン → ログイン中） | 進める | `loading` で読み取りを 1 回出す |
| 読み取りの成功（世代が今と同じ） | — | 効く値を決めて `ready`。違う世代なら捨てる |
| 読み取りの失敗（世代が今と同じ）・`loading` | — | `error`（時刻の欄を出さない） |
| 読み直しの失敗（世代が今と同じ）・`ready` | — | 前の値を残して `ready` のまま。**遅れて届いた失敗で `ready` を `error` に落とさない** |
| 「再読み込み」（`error`） | 進める | `loading` で読み取りを出す（成功で `ready`） |
| プリファレンスの画面の読み込み・保存の成功（`applySaved`） | **進める** | 効く値を決め直して `ready`（`error` からも回復）。飛んでいる読み取りの答えは捨てる |
| 未ログインになった | 進める | 値と状態を初期に戻す。以後の答えは捨てる |

- **世代で古い答えを捨てる**ことで、(a) 遅れて届いた古い読み取りが新しい保存の値を上書きしない、(b) ログアウトの後の答えが値を作らない、(c) 別の利用者のログインに前の値が残らない、を同時に満たす。待ち合わせは時間に預けず、取得の関数の解決（`Promise`）を注入して順を入れ替えて作る。
- 失敗の種類（接続の失敗・5xx・形の誤り）はどれも `error`。**401** は共通の通信の部品が更新を試み、失敗すればログアウトの流れ（未ログインになって初期に戻す）に入るため、`error` の画面で止まらない。更新が成功すれば通信の部品が送り直し、提供元には成功として届く。
- `error` のとき、画面の側から診断の情報を送らない（新しい API を足さない。`OD3`）。時刻の欄には「表示のタイムゾーンを読み込めません」の短い文、画面の上の 1 か所に再読み込み（`DisplayTimeZoneErrorBar`。`error` 以外では何も描かない）。ブラウザのタイムゾーンへは落とさない。
- 確かめ（画面の単体テスト）: 失敗の 3 種類で `error`、再読み込みで `ready`、`ready` の読み直しの失敗で前の値が残ること、解決の順を入れ替えても新しい世代の値が残ること、未ログインになった後の答えが値を作らないこと、プリファレンスの成功で `error` から `ready` に戻ること、401 のときにログイン画面へ戻る既存の動作が変わらないこと。

## RD5. 管理の画面の単体テストへの影響（NFR4.1.8）

- 管理の画面（利用者・招待・DSL の状態と履歴・確認の表示）の `timeZone` の props を外し、配布の提供元で時差を固定する形に直す。提供元を差し込む共用のテストの手伝いを **1 つ**（`src/app/display-settings/testing/` に置く）作り、各テストが同じ入口を使う。管理の画面の時刻の期待は新しい書式（`YYYY-MM-DD HH:mm ±HH:mm`）に書き換える。旧書式（`JST`・`GMT+9`）を期待する記述は検索で 0 件にする。書き換えるテストの件数は Build and Test に記録する。
- 時刻の欄の `loading`・`error` の表示は、共通の部品（LC8）の単体テストで 1 回確かめ、各画面のテストでは `ready` を主にする。

## RD6. E2E への影響（NFR4.1.6・NFR4.1.7）

- **見本**: `preferencesFixtures.ts` の `preferencesSample` に `displayTimeZone`（文字列か `null`）を足し、`hasPreferencesShape` の項目の一致と型の確かめに 5 つ目を含める。本物の応答と見本の形の比べ（080 の測り）は 5 つ目を含めて行う。**5 つ目の無い見本は作らない**（配布が `error` になるため）。5 つ目の値の見本（名前・`null`・解釈できない名前）を見本の部品から作る。
- **回数の定数**: 取得の回数を名前つきの定数で持つ。`GETS_BY_DISTRIBUTION = 1`（ログインの直後に配布が読む回数）、`GETS_BY_PREFERENCES_SCREEN = 1`（プリファレンスの画面を開いたときの画面の読み取り）、`GETS_AFTER_OPENING_PREFERENCES`（上の 2 つの和）。080・100 は、差し替えをログインの前に置くため、ログインの後の配布の読み取りも差し替えに当たる。`toBe(1)` のまま放置せず、**配布の読み取りを含めた期待を定数にし、理由をコメントに書く**。
- **回数は待って確かめる**: 配布の読み取りは、ログイン状態の更新の後の効果で出るため、ログインの完了と取得の発生の間にずれがありうる。回数は `expect.poll` で、期待の値になるまで待って確かめる。ログインの直後に `GETS_BY_DISTRIBUTION` になることを確かめる手順を既存の流れに足し（流れは増やさず、本数に数えない）、これが実機でのログイン後の取得の回数（NFR2.1.1）の確かめになる。画面を開いた後に `GETS_AFTER_OPENING_PREFERENCES` になることも確かめる。
- **110（停止した利用者の 401）**: 配布の読み取りは停止の前（ログインの直後）に 200 で済み、停止の後にプリファレンスの画面を開いたときの 401 は画面の読み取りから出る。この順に依らず待てるよう、401 を待つ対象を「プリファレンスの画面を開いた後の最初の 401」にし、更新の 401 とログイン画面への戻りを待つ今の手順は変えない。停止の後に配布が読み直さない（ログイン後に 1 回）ことが前提で、違う場合は計画で待つ対象を直す。
- 解釈できない名前・読み込み失敗の状態は、差し替えた応答で作る。この単位は E2E の流れを足さない（日時の代表の流れ 1 本は業務データの単位が受け持つ）。書き換えた E2E の件数と axe の違反 0 件は Build and Test に記録する。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/reliability-requirements.md`・`scalability-requirements.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`・`functional-spec.md`
- 同じ段の文書: `logical-components.md`（D1・D7・T4）、`performance-design.md`
- 既存のコード（読み取りだけ）: `backend/src/main/resources/db/migration/`・`backend/src/test/java/cherry/mastersmith/audit/domain/AuditMigrationCompatibilityIT.java`・`role/RoleMigrationCompatibilityIT.java`・`user/web/MePreferencesLockTimeoutLeakIT.java`・`frontend/e2e/080-preferences-accessibility.e2e.ts`・`100-app-text-contrast.e2e.ts`・`110-user-admin-flow.e2e.ts`・`frontend/e2e/support/preferencesFixtures.ts`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 前の版のコードで新しい内部DB を起動する互換のテストは、前の版と同じ Flyway の設定でマイグレーションのファイルだけを使う形（Spring を起動しない）で足りる。前の版のアプリのイメージを実際に起動する確かめは、Deployment の段の戻しの練習の判断に任せる。
- [assumption] 110 で、停止の後に配布が読み直さない（ログイン後に 1 回）前提。読み直すなら、待つ対象を Code Generation の計画で直す。
- [assumption] 080・100 の取得の回数の期待は、配布 1 回 + 画面 1 回の 2 回。差し替えを置く順を見て Code Generation の計画で実測して確定する。
- 未確認: 保存の 1 文を通した `MePreferencesLockTimeoutLeakIT` の実際の結果（Code Generation）。
- 未解決の点は上のほか無い。
