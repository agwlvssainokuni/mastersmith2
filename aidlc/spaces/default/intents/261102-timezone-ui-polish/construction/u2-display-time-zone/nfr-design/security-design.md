# セキュリティの設計 — u2-display-time-zone

出典: NFR Requirements の `security-requirements.md`（NFR1.2.x・NFR1.4.x・NFR1.5.x・NFR3.x・NFR6.1.1）・`tech-stack-decisions.md`（NFR5.x）・`reviews/review-01.md`（R-03）、機能設計（`rules.md` の BR2.1〜BR2.24・`functional-spec.md`）、`team.md`（Testing Posture・Code Style）、`project.md`（Forbidden・Mandated）、同じ段の `logical-components.md` の捨ての試し（T1・T3）、既存のコード（読み取りだけ: `MeController`・`PreferencesRequest`・`PreferencesCommand`・`UserBoundaryArchitectureTest`・`GlobalExceptionHandler`・`MePreferencesLockTimeoutLeakIT`・`frontend/eslint.config.js`）。

この文書は、NFR1.2.x・NFR1.4.x・NFR1.5.x・NFR3.x・NFR5.x を、部品（`logical-components.md` の LC）とテストに割り付ける。決定の番号は `SD<n>`。

## SD1. 名前の門（NFR1.4.1・NFR5.1.2・NFR1.4.3）

- **入口は文字列だけ**。web の層の変換（LC2）が `Text`（文字列）と分けた値だけが門に入る。`Keep`・`Clear`・`NotText` は門を呼ばない（`Set.copyOf` で作った集合の `contains(null)` が `NullPointerException` になるため。試し T1）。門は `null` を受けず、受けた場合は誤りとする。
- **判定は 2 段**。(1) 長さ 1〜64 文字（集合の最長は 32 文字。長い値を集合の探索に入れない）。(2) 不変の集合への大文字小文字を区別した完全一致。前後の空白・制御文字・NUL・改行を付けた値、別名の大文字小文字違い（`asia/tokyo`）、固定のオフセット（`+09:00`・`Z`）は集合に無く、すべて誤り。値は **整形も削り直しもしない**（保存する値と検証した値が同じ）。
- **集合は JDK の地域の名前**（試しで 604 個、`UTC`・`GMT`・旧い別名を含む）。ブラウザが知らない名前はサーバーが受けて保存し、画面は `saved-unresolvable` で扱う（BR2.12）。JDK の版を上げると集合が変わりうるが、**保存済みの名前は読み取りで再検証しない**（BR2.6）。上げた後に集合から消えた名前は、返され続け、画面側が解釈できるかで扱いが決まる。保存するときだけ、そのときの集合で判定する。
- **純粋な関数**（時計・DB・ログに触れない）で、jqwik の性質ベースのテストを付ける（失敗時の乱数の種を記録する）。性質: (P1) 集合の任意の要素は通る。(P2) 集合の要素に 1 文字足す・削る・変える・大文字小文字を変えた値は通らない（集合の要素でない場合）。(P3) 任意の文字列について、`[+-]HH`・`[+-]HH:MM`・`[+-]HHMM` の形は常に通らない。(P4) 65 文字以上は常に通らず、集合を引かない。(P5) 前後に空白・制御文字を付けた集合の要素は通らない。(P6) 同じ入力に何度でも同じ結果。あわせて、集合が作り置きの 1 つで（呼び出しのたびに同じ集合の参照を返す）、変更できないこと（`PD5`）。

## SD2. 3 状態の受け取りと値の扱い（NFR1.4.2・NFR1.2.2・NFR1.4.3）

| 本文の `displayTimeZone` | web の層の変換の結果 | 業務処理の結果 |
|---|---|---|
| 項目が無い | `Keep` | 列を書かない。保存後の値は保存済みのまま返す |
| `null` | `Clear` | 列を `NULL` にする（ブラウザに合わせる） |
| 文字列 | `Text` | 門を通れば `Set`、通らなければ `displayTimeZone` の `INVALID_VALUE` |
| 数・真偽・オブジェクト・配列 | `NotText` | `displayTimeZone` の `INVALID_VALUE`（文字列でない型は名前として通らない） |
| 空の文字列 | `Text` | 門の長さの確かめで誤り |

- 受け取りの型は本文の項目を `JsonNode` で持つ（試し T3: `Optional` は項目が無いときと `null` のときを区別できない）。項目が重なる本文（`displayTimeZone` が 2 回出る）は、Jackson の既定どおり **後の値が勝つ**。この動作を決めた側の動作として結合テストで固定する（前の版の画面は重ねて送らない）。
- 誤りが 1 件でもあれば全体を保存しない（BR2.4）。誤りは項目の順（氏名・言語・テーマ・文字の大きさ・`displayTimeZone`）に並べ、400 `VALIDATION_FAILED` の `fieldErrors` に `field` と `reason`（`INVALID_VALUE`）だけを載せる。**入力の値を応答の `detail`・`fieldErrors` に載せない**。新しい code・状態コードを足さない（NFR1.5.3）。
- 本文の知らない項目・他人を指す項目（利用者 ID・メールアドレス・停止・管理者の印）は、型に対応する項目が無いため読み捨てる。**今の 4 項目の誤り（`REQUIRED`・`INVALID_VALUE`）の判定と並びは変えない**。
- 本文が壊れている・JSON でないときは既存の `MALFORMED_REQUEST`（変えない）。極端に長い値（10 万文字）は、サーバーの本文の大きさの上限で先に拒否されることがあるため、**400 か 413 かを実測で決め、決めた側の動作を明示した結合テストにする**（NFR Requirements の [assumption] を引き継ぐ。どちらでも値は応答・ログに出ない）。
- **伏せ字**: 検証の前の値を持つ型は `toString` で値を伏せる。対象は、保存の要求の型（氏名を今も伏せ、`displayTimeZone` の中身も伏せる）、保存の命令の型、`Text`・`NotText`（`Text[***]` のように値を出さない）。検証を通った名前の値の型は名前を出してよい（個人に関する値でも秘密でもなく、集合の要素のため）。BR2.4 と BR2.9 の食い違い（機能設計のレビュー R-03）は、この区別で解く。

## SD3. 本人だけ・一括代入・他の更新との干渉（NFR1.2.1・NFR1.2.2・NFR1.2.3）

- 対象の行は **認証の主体から決める**（`MeRequestContextResolver` の現在の利用者）。本文・道・問い合わせから決めない。`/api/me/**` の分類（ログインだけ）は変えない。管理者が他人の設定を変える API・道は作らない。未認証は 401 `AUTHENTICATION_REQUIRED`。
- 保存の 1 文（LC3）の `SET` は **4 項目と表示のタイムゾーンの列だけ**、`WHERE` は利用者 ID だけ。`admin_flag`・停止・失敗回数・メールアドレス・パスワードのハッシュを書く口が構造として無く、本文に足しても反映されない。
- `User` の実体は列 `display_time_zone` を **更新不可** で対応づける（`updatable = false`）。保存の 1 文以外の更新（パスワード変更・停止と再開・ロックの解除・管理者の印・氏名や言語の変更を含む実体経由の更新）が、読み込んだ古い値で列を上書きしない。
- 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の 3 つの入口の今の判定のまま（変えない）。GET・PUT とも拒否され、停止を解いた直後に受け付ける。
- 同じ利用者の 2 つの保存が重なったときは後の保存が残る。1 文が 5 項目を一緒に確定するため、途中の値は残らない（`SC3`）。

## SD4. 漏えいの防止（NFR1.4.3・NFR1.4.4）

- ログ・トレースの属性・外部へ送る設定を有効にしたときの出力・エラー応答に、**検証の前の名前の値**が出ない。出る可能性がある経路（`TraceAspect` が web・service・domain・repository の引数と戻り値を TRACE で文字列にする）を、SD2 の伏せ字で閉じる。検証を通った名前は SQL の束縛に届くが、それは集合の要素であり、秘密ではない。**検証の前の値が SQL に届く経路は作らない**（保存の 1 文の前に必ず検証を通す）。
- 例外は、`GlobalExceptionHandler` が 1 か所で変換する今の扱いのまま。排他の待ちの上限切れの連なりは、例外のクラスの名前だけを ERROR に出す（既存の決まり）。保存の 1 文でも同じ道を通ることを、既存の `MePreferencesLockTimeoutLeakIT` を新しい文に当てて確かめる（`RD2`）。
- 400 は想定内の業務エラーで、WARN 以下・スタックトレースなし・境界で 1 回だけ出す。項目の名前と誤りの種類だけを出し、値を出さない（`OD2`）。
- 表示のタイムゾーンの保存は **成功も拒否も監査に残さない**。監査の種類・列を足さない（今のプリファレンスと同じ。NFR1.4.4）。
- **確かめ（`PreferencesSecretLeakIT`、`MeSecretLeakIT` と同じ形）**: TRACE を有効にして、目印のある無効な名前で PUT し、ログ・トレースの属性・応答に目印が出ないこと。氏名が出ないこと。外部へ送る設定を有効にした結合テストで、トレースの属性に名前の値が出ないこと。各型の `toString` の単体テスト。成功と 400 の後、監査の行の数が増えないこと。

## SD5. 画面は値を信頼できない入力として扱う（NFR1.4.5）

- サーバーが返した名前・保存済みの名前・ブラウザが返した名前は、**React の文字のノードとしてだけ描く**。`dangerouslySetInnerHTML` を使わず、文言の差し込み値を HTML として解釈する部品（HTML を許す `Trans` の差し込みなど）に渡さず、属性の文字列に連結しない。解釈できない名前の案内の `{名前}`、選択肢の文字、読み込み失敗の文のいずれでも同じ。
- 提供元の応答の形の確かめ（`displayTimeZoneApi`）: `displayTimeZone` は `string` か `null` のときだけ受け、ほかの型は形の誤りとして `error` にする。文字列は長さ 64 以下のときだけ受ける（サーバーが 64 を超える値を返さない前提を、画面でも守る防御）。名前の中身の形は確かめない（解釈できるかは時刻変換の部品の門が決める）。
- 確かめ: 保存済みの名前が `<img src=x onerror=…>`・`"`・`'`・`javascript:` を含む応答（形は有効）を読んだとき、案内と選択肢に **文字として** 出て、要素が増えないこと（画面の単体テスト）。

## SD6. ブラウザの既定のタイムゾーンを暗に使わない構造の検査（NFR1.4.6・レビュー R-03）

**規則 1 つ** を `frontend/eslint.config.js` に足す（`no-restricted-syntax` の選択子を足す。`LC9`）。選択子の本体は 1 つのモジュールに置き、設定とテストが同じものを読む。

| 選択子 | 止めるもの | 例外 |
|---|---|---|
| S1 `CallExpression[callee.property.name='resolvedOptions']` | ブラウザのタイムゾーンを読む呼び出し | `browserTimeZone.ts` の 1 か所だけ |
| S2 `Intl.DateTimeFormat` の生成・呼び出しで、オプションに `timeZone` の項目が無いもの | 暗黙の既定のタイムゾーンでの整形 | `browserTimeZone.ts` の 1 か所だけ（既定のタイムゾーンを読む呼び出しそのもののため） |
| S3 `toLocaleDateString`・`toLocaleTimeString` の呼び出しで、`timeZone` の項目が無いもの | `Date` だけが持つ整形（数値などには無い名前なので誤検知が出ない） | なし |
| S4 `toLocaleString` の呼び出しで、`timeZone` の項目が無く、数値の整形の項目（`style`・`currency`・`unit`・`notation`・`useGrouping`・`minimumFractionDigits`・`maximumFractionDigits`・`minimumIntegerDigits`・有効桁数など）も無いもの | `Date` の `toLocaleString()`（引数なし・ロケールだけ・日時の項目だけを含むものを含む） | なし |

- **範囲**: `src/` のテストでないファイルの **全体**（`src/features/`・`src/app/` に加えて `src/shared/` を含む。レビュー R-03 の漏れを閉じる）。`e2e/`・`vendor/` は対象外。時刻変換の部品（`src/shared/format/timeZone/`）は、整形器を作るときに必ず `timeZone` の項目を持つため、例外を置かずに通る。
- **数値の整形を許す書き方**（レビュー R-03 の「許す書き方」）: (a) `Intl.NumberFormat`（選択子の対象外）。(b) 数値の整形の項目だけを持つ `toLocaleString`（S4 の対象外）。曖昧な呼び出し（`x.toLocaleString()`・`x.toLocaleString('ja-JP')`）は、`Date` が暗黙の既定のタイムゾーンで整形する危険のほうが、数値の書き換えの手間より大きいため、**日時として止める**。いまの `src/` に数値の `toLocale…` は 0 件で、書き換えは要らない（読み取りで確かめた）。
- **設定の落とし穴**: フラットな設定では、同じ規則を設定した後の設定が前を置き換える。そのため、新しい設定は既存の 2 つの選択子（`export default` の禁止・`enum` の禁止）を含めて書く（定数に取り出して共有する）。例外の 1 か所は、S1・S2 を除いた並びを別の設定で当てる。
- **確かめ**: ESLint の `Linter` を使う単体テストで、断片と設定の対応づけを固定する。止める: `x.resolvedOptions().timeZone`（別のファイル名で）・`new Intl.DateTimeFormat('ja')`・`Intl.DateTimeFormat('ja', {})`・`d.toLocaleDateString()`・`d.toLocaleTimeString('en', {hour: '2-digit'})`・`d.toLocaleString()`・`d.toLocaleString('ja-JP')`・`d.toLocaleString('ja', {hour: '2-digit'})`。通す: `new Intl.DateTimeFormat('en', {timeZone: name})`・`d.toLocaleString('ja', {timeZone: tz})`・`n.toLocaleString('ja', {minimumFractionDigits: 2})`・`new Intl.NumberFormat('ja').format(n)`。ファイル名の組（`src/shared/…`・`src/features/…`・`src/app/…`・`browserTimeZone.ts`・テストのファイル）で S1・S2 の例外が `browserTimeZone.ts` だけに効くこと、`export default` と `enum` が今も止まること。実物の `npm run lint` の違反 0 件。
- 取りこぼし（構造の検査の限界）: 型を知らないこと、オプションを変数に入れた呼び出し（`opts` の中身が見えない。S4 は止める側に倒れる）、間接呼び出し（`Reflect` など）。補いは、画面のテストを機械の既定のタイムゾーンを変えても同じ結果にすること（NFR5.2.1）。
- 使われなくなる `formatDateTime`・`formatLockedUntil`・`lockedUntil.ts` は **消す**（規則を通すための例外を置かない）。消した後、旧書式（`JST`・`GMT+9`）を期待する記述と import が検索で 0 件。

## SD7. 配布の値（NFR1.4.7）

- 値は React の状態だけで持ち、ブラウザの保存先（`localStorage`・`sessionStorage` など）・URL・`console` に置かない。未ログインになったら値と世代を捨て（初期状態）、別の利用者のログインに前の利用者の表示のタイムゾーンが残らない。古い答えは世代で捨てる（`RD4`）。
- 新しいファイルに `no-console` を当てる（既存の `businessDataConsoleRule` の `files` に `src/app/display-settings/` の新しいファイルを足す。規則を足すだけで緩めない）。

## SD8. 依存とソース（NFR1.5.1・NFR1.5.2・NFR1.5.3）

- 新しい依存を足さない（バックエンドは JDK の `ZoneId`・Spring・既存の JPA、画面は既存の `Select`・`FormField`・`Alert` と `Intl`）。コード生成の差分で `build.gradle.kts`・`gradle.lockfile`・`package.json`・`package-lock.json` が増えていないことを確かめる。
- ソースの先頭に Apache License 2.0 のヘッダー、フォーマット・リンタ・SpotBugs。層の決まり（画面入出力は DB アクセスを呼ばない・トランザクションは業務処理の層・要求と応答は `record`）を守る。保存の 1 文は定数の文字列で、利用者の値を文字として組み込まない（値はバインド変数、識別子は固定）ため、SpotBugs の `SQL_` の指摘の対象にならない見込み（除外を足さない）。`UserBoundaryArchitectureTest` は既にあり、依存の向きを変えない。既存の境界テストと除外は緩めない。
- 新しい code・状態コードを足さない。問題の種類の一覧のテストが変更なしで通る。

## SD9. 確かめの設計（NFR3.x・NFR5.x）

| 確かめ | 手段 | 対応する要件 |
|---|---|---|
| 名前の門の性質 P1〜P6・作り置きの集合の同一性と変更不可 | jqwik と単体テスト | NFR1.4.1・NFR2.1.5・NFR5.1.2 |
| 3 状態（無い・null・名前・数/真偽/オブジェクト/配列・空・重なる項目）と、400 の形・全体を保存しない・並び | 結合テスト（H2。コンテナは使わない） | NFR1.4.2・NFR1.4.1・NFR5.1.3 |
| 本人だけ・未認証 401・要求の本文の改ざん（知らない項目で他の列・他人の行が変わらない）・停止中の拒否と解除直後・他の更新の後も列が残る・「ログインだけ」の分類が変わらない | 結合テスト（別の接続で読み直す） | NFR1.2.1〜NFR1.2.3・NFR5.1.3 |
| TRACE・外部へ送る設定の有効時に、検証の前の値が出ない。型の `toString`。監査の行が増えない | `PreferencesSecretLeakIT`・結合テスト・単体テスト | NFR1.4.3・NFR1.4.4 |
| 画面に出す値が文字のノードのまま | 画面の単体テスト | NFR1.4.5 |
| 構造の検査（SD6 の断片と設定） | `Linter` の単体テスト・`npm run lint` | NFR1.4.6 |
| 未ログインで値を捨てる・別の利用者で前の値が出ない・古い答えを捨てる・保存先を使わない | 画面の単体テスト（待ち合わせで決定的に） | NFR1.4.7 |
| 依存・ヘッダー・層・code が増えない | `./gradlew verify`・差分の確認 | NFR1.5.1〜NFR1.5.3 |
| 選択の WCAG 2.1 AA・キーボード・はみ出し・読み込み中と失敗の状態の読み上げ | vitest-axe・実際のブラウザの axe と E2E の書き換え（本数に数えない） | NFR3.1.1・NFR3.1.2・NFR3.3.1・NFR3.3.2 |
| 時差を固定したテスト（機械の既定に頼らない）・夏時間と端数の時差を画面を通して確かめる | 画面の単体テスト（時差・現在時刻を注入）・実際のブラウザの `timezoneId` | NFR5.1.1・NFR5.2.1・NFR5.2.2 |

## 脅威の見立ての更新（U2 の範囲）

| 脅威 | 見立て | 守り |
|---|---|---|
| 他人の表示のタイムゾーンを読む・書く | 道・本文に利用者 ID の口が無く、対象は認証の主体だけ | SD3 |
| 一括代入で管理者の印や停止を変える | 保存の 1 文の `SET` が固定の列だけ | SD3 |
| 不正な名前を保存して画面を壊す | 集合への完全一致で拒否。保存済みでも画面が解釈できなければ `saved-unresolvable` | SD1・SD5 |
| 検証の前の値がログ・トレースに出る | 型の伏せ字・SQL に届く前に検証 | SD2・SD4 |
| 名前を使った画面の注入（スクリプト・属性） | 文字のノードだけで描く | SD5 |
| 暗黙の既定のタイムゾーンで時刻がずれる | 構造の検査と時差を固定したテスト | SD6 |
| 前の利用者の値が次の利用者の画面に残る | 未ログインで捨てる・保存先なし | SD7 |
| 他の更新が古い値で列を上書きする | 列を更新不可にし、1 文だけが書く | SD3 |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`・`functional-spec.md`
- 同じ段の文書: `logical-components.md`（捨ての試し T1・T3）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/user/web/MeController.java`・`PreferencesRequest.java`・`common/error/web/GlobalExceptionHandler.java`・`backend/src/test/java/cherry/mastersmith/user/UserBoundaryArchitectureTest.java`・`user/web/MePreferencesLockTimeoutLeakIT.java`・`frontend/eslint.config.js`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 他の更新（パスワード変更・停止と再開・ロックの解除・管理者の印）が `User` の実体を通して書くか、問い合わせで書くかは、この段で全部は確かめていない。どちらでも、列を更新不可にし、保存の後に他の更新を行っても列が残ることを結合テストで確かめれば足りる。
- [assumption] 項目が重なる本文で後の値が勝つのは Jackson の既定で、アプリが重複キーの検出を有効にしていない前提。違えば、決めた側の動作を明示したテストに書き換える。
- 確かめ済み（`logical-components.md` の T5）: 4 つの選択子（`:has`・正規表現つきの属性）は、リポジトリの ESLint で、断片 13 件（止める 8・通す 5）が期待どおりに動く。実物の設定への組み込みは Code Generation で確かめる。
- 未確認: 10 万文字の値の拒否が 400 か 413 か（Code Generation の結合テストで実測して決める）。Firefox・Safari での名前の一覧の個数。
- 未解決の点は上のほか無い。
