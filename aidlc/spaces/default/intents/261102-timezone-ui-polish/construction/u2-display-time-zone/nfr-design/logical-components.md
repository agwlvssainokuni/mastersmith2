# 論理の部品 — u2-display-time-zone

出典: 機能設計（`construction/u2-display-time-zone/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`frontend-components.md`）、NFR Requirements（同じ単位の `nfr-requirements/` の 5 つの要件と `tech-stack-decisions.md`・`reviews/review-01.md`）、同じ段の `security-design.md`・`performance-design.md`・`reliability-design.md`・`scalability-design.md`・`observability-design.md`、既存のコード（読み取りだけ: `user` の `UserPreferencesService`・`UserRepository`・`MeController`・`PreferencesRequest`・`Preferences`・`User`、`frontend/src/app/work-role/WorkRoleProvider.tsx`・`app/display-settings/`・`shared/format/timeZone/`・`features/preferences/`・`features/useradmin/`・`features/invitation/`・`features/dsl/`、`frontend/eslint.config.js`、`perf/k6/scenarios.js`、`frontend/e2e/`）。

U2 は、表示のタイムゾーンの設定（バックエンドの API・表の列・移行）と、その配布・選択・管理の画面の時刻の単位（種類 service。アプリと画面の両方に入る）。この段で設計するのは、NFR Requirements の要件を、どの部品がどの守りで受け持つか、その置き場と依存、失敗の及ぶ範囲、共有する資源である。実装のファイル名・関数名は Code Generation で決める（ここの部品の名前は論理の名前）。決定の番号は、この文書の中では `LC<n>`、ほかの文書の中ではそれぞれ `SD<n>`（セキュリティ）・`PD<n>`（性能）・`RD<n>`（信頼性）・`SC<n>`（拡張）・`OD<n>`（観測）とする。

質問は作らない。決める論点は、いずれも捨ての試し（下の「捨ての試しの結果」）と承認済みの文書で決まり、依頼者の判断が要る選択肢が残らなかったため（`project.md` の決まり: 新しく決める論点が無い設計の段は、要点の確認で進める）。承認済みの文書との差と、承認の場で確かめる点は、下の「承認済みの記録との差」と「Assumptions & Open Questions」に置く。

## LC1〜LC11. 部品の一覧

### バックエンド（`cherry.mastersmith.user`。層の決まりと既存の境界テストを緩めず、足す形にする）

| ID | 論理の部品 | 置き場（層） | 責務 | 持つ状態 |
|---|---|---|---|---|
| LC1 | 名前の門 | `user.domain` | 文字列 1 つが、JDK の地域の名前の集合に完全一致するか（大文字小文字を区別、1〜64 文字）を答える純粋な関数。集合は不変の集合を **クラスの読み込み時に 1 回だけ** 作り、呼び出しごとに作り直さない（`SD1`・`PD5`）。時計・DB・ログに触れない。検証を通った名前だけを持つ値の型（名前の値）も、この部品が作る | 不変の名前の集合（変わらない） |
| LC2 | 3 状態の入力 | `user.web`（受け取り）と `user.service`（運ぶ型） | 本文の項目の有無と `null` を区別して、`Keep`（項目が無い）・`Clear`（`null`）・`Text`（文字列）・`NotText`（文字列でない型）の 4 つに分ける。受け取りの型は本文の項目を `JsonNode` で持ち、web の層の変換が上の 4 つにする。`Text` と `NotText` と要求の型・命令の型は、検証の前の値を持つため `toString` で値を伏せる（`SD2`） | なし |
| LC3 | 保存の 1 文 | `user.repository`（`UserRepository` に足す） | 4 項目の置き換えと表示のタイムゾーンの 3 状態を、**1 つの文** で行い、保存後の表示のタイムゾーンと本人の行の有無を同じ文の結果として返す（`RD2`・`PD2`）。H2 の `FINAL TABLE (UPDATE …)`。`Keep` のときは列を書かない。行が無ければ結果が空 | なし |
| LC4 | 読み取りと応答 | `user.domain`（`User` の列の対応）・`user.service`（結果の型）・`user.web`（応答の型） | `User` に列 `display_time_zone` を対応づけ（更新不可の印を付け、保存の 1 文だけが書く。`SD3`）、読み取りの結果と応答に 5 つ目を足す。応答は項目を足すだけで、5 つ目は常に出し（未設定は `null`）、名前は再検証しない。ログインの応答・現在の利用者の応答は変えない | なし |
| LC5 | 移行 | `db/migration` | 利用者の表に `display_time_zone`（`VARCHAR(64)`・NULL 可・既定なし）を足すファイル 1 つ（`V14__u2_display_time_zone.sql`）。前進のみ。表・索引・制約・既存の列に触れない | なし |

### 画面（`frontend/src`。機能どうしは import し合わず、共有は `src/shared/` と `src/app/` を通す）

| ID | 論理の部品 | 置き場（層） | 責務 | 持つ状態 |
|---|---|---|---|---|
| LC6 | 配布の提供元 | `src/app/display-settings/`（`DisplayTimeZoneProvider`・`useDisplayTimeZone`・`useDisplayTimeZoneActions`・`displayTimeZoneApi`・`decideDisplayTimeZone`・`browserTimeZone`） | ログイン後に `GET /api/me/preferences` を 1 回読み、`loading`・`ready`・`error` と効く値（`saved`・`browser`・`saved-unresolvable`）を決める。要求の世代で古い答えを捨てる。未ログインで捨てる。差し替え（`applySaved`）と再読み込み（`reload`）を別のコンテキストで出す（`PD1`・`RD4`・`SD7`） | 状態 1 つと世代の数。保存先は持たない |
| LC7 | 選択肢の組み立て | `src/features/preferences/`（`buildTimeZoneOptions`・`TimeZoneSelect`） | ブラウザの名前の一覧（約 400 個）に `UTC` と保存済みの名前を足し、先頭に「ブラウザに合わせる」を置き、各名前に今の時差を添える。2 段で作る: 不変の名前の列（整列済み、取り出した配列ごとに 1 回）と、時差の文字（開いた時点の時刻で、画面を開いている間 1 回）。部品は `memo` で包み、ほかの項目の入力で作り直さない（`PD3`） | 画面を開いている間の時刻の写し |
| LC8 | 時刻の欄の部品 | `src/app/display-settings/`（`DisplayTime`・`DisplayTimeZoneErrorBar`）・`src/shared/format/`（`formatInstantToMinutes`）・`src/features/useradmin/`（ロックの解除の予定） | 管理の画面の時刻の欄を `loading`・`error`・`ready` で出し分け、`ready` のときだけ時刻変換の部品を **1 つの時刻につき 1 回** 通して分までにする。`error` のとき画面の上の 1 か所に再読み込みを出す。管理の画面から `timeZone` の props を外し、旧い `formatDateTime`・`formatLockedUntil` を消す（`PD7`・`RD5`） | なし |
| LC9 | 構造の検査 | `frontend/eslint.config.js` | ブラウザの既定のタイムゾーンを暗に使う書き方を、`src/` のテストでないファイル全体で止める規則 1 つ。許す場所は `browserTimeZone` の 1 か所（`SD6`） | なし |

### 試験の道具

| ID | 論理の部品 | 置き場 | 責務 |
|---|---|---|---|
| LC10 | 負荷の場面 | `perf/k6/scenarios.js`・`perf/k6/thresholds-test.sh`・`perf/README.md` | 新しい場面 `preferencesSaveTimeZone` 1 つ。1 回の繰り返しに要求 1 つで、`set`・`clear`・`invalid` を交互に流す。setup は自分で前提（試験用の利用者の表示のタイムゾーンを消す）を作る（`PD6`） |
| LC11 | E2E の見本と回数の定数 | `frontend/e2e/support/preferencesFixtures.ts` と 080・100・110 | 見本に 5 つ目を足し、形の確かめに含める。取得の回数を名前つきの定数で持ち、回数は待って（`expect.poll`）確かめる（`RD6`） |

## 依存（text の図）

- 名前の門（LC1）← 検証（`PreferencesValidation`）← 業務処理（`UserPreferencesService`）← web の層の変換（LC2）。LC1 は ほかの部品に依存しない。
- 保存の 1 文（LC3）← 業務処理だけが呼ぶ（画面入出力の層は呼ばない。既存の境界の決まりのまま）。トランザクションは業務処理の層の 1 つだけ。
- 提供元（LC6）→ ログインの状態（`LoginStateGate`）・時刻変換の部品（U1）・API の呼び出し（`apiRequest`）。時刻変換の部品は提供元を知らない。
- 時刻の欄の部品（LC8）→ 提供元（読むだけ）・時刻変換の部品。管理の画面（`useradmin`・`invitation`・`dsl`）→ LC8。管理の画面どうしは import しない。
- プリファレンスの画面（LC7）→ 提供元の差し替えの口（`useDisplayTimeZoneActions`）。`features` が `app` を読む向きだけで、逆は無い。
- 提供元の置き場: `AdminForbiddenProvider` の内側・`WorkRoleProvider` の外側（ログイン状態と表示の設定の内側）。`WorkRoleProvider` と同じ並び・同じ作り（ログイン中だけ読む、世代、未ログインで捨てる）にし、再読み込みのきっかけ（`useReloadTriggers`）は **使わない**（画面の移動で読み直さないため。`PD1`）。

## NFR のパターンの当てはめ

| NFR（この単位の枝番） | パターン | 当てはめる部品 |
|---|---|---|
| NFR1.2.1・NFR1.2.2・NFR1.2.3 | 本人の行だけを認証の主体から決める・知らない項目は読み捨て・書く列を保存の 1 文だけに限る | LC2・LC3・LC4（`SD2`・`SD3`） |
| NFR1.4.1・NFR1.4.2 | 名前の完全一致・3 状態・誤りがあれば全体を保存しない | LC1・LC2（`SD1`・`SD2`） |
| NFR1.4.3・NFR1.4.4 | 検証の前の値を伏せる・監査に残さない | LC2・LC4（`SD4`） |
| NFR1.4.5・NFR1.4.7 | 画面は値を文字として描く・配布は未ログインで捨てる | LC6・LC7・LC8（`SD5`・`SD7`） |
| NFR1.4.6 | 暗黙の既定のタイムゾーンを構造で止める | LC9（`SD6`） |
| NFR1.5.1・NFR1.5.2・NFR1.5.3 | 依存を足さない・層と境界テストを緩めない・code を足さない | 全部品（`SD8`） |
| NFR2.1.1 | ログイン後に 1 回・世代 | LC6・LC11（`PD1`・`RD6`） |
| NFR2.1.2 | 保存・読み取りの文の数を増やさない | LC3・LC4（`PD2`） |
| NFR2.1.3・NFR2.1.4 | 既存の場面を変えず、新しい場面を 1 つ足す（測りは Build and Test） | LC10（`PD6`） |
| NFR2.1.5 | 不変の集合を 1 回 | LC1（`PD5`） |
| NFR2.1.6 | 2 段の組み立て・作り直さない・判定は 5 回の中央値 | LC7（`PD3`・`PD4`） |
| NFR2.1.7 | 1 つの時刻につき 1 回の変換・loading と error では変換しない | LC8（`PD7`） |
| NFR2.3.1・NFR2.3.2・NFR2.3.3・NFR2.3.4 | 負荷の形を変えない・列 1 つ・後勝ちを 1 文で・複数タブは同期しない | LC3・LC5・LC6（`SC1`〜`SC4`） |
| NFR2.3.5・NFR2.3.6・NFR2.3.7 | 既存の指標・警報のまま・値をログに出さない・画面から診断を送らない | LC2・LC6（`OD1`〜`OD3`） |
| NFR3.1.1・NFR3.1.2・NFR3.3.1・NFR3.3.2 | 標準の選択を損なわない・読み込み中と失敗の状態を読み上げる | LC7・LC8（`SD9`） |
| NFR4.1.1・NFR4.1.2・NFR4.1.3・NFR4.1.4・NFR4.1.5 | 列 1 つの前進のみの移行・前の版が読まず動く・応答は項目を足すだけ・戻しはイメージだけ | LC3・LC4・LC5（`RD1`〜`RD3`） |
| NFR4.1.6・NFR4.1.7・NFR4.1.8・NFR4.3.1・NFR4.3.2 | E2E の見本と回数・既存の単体テストの書き換え・失敗と回復・古い答えの競り合い | LC6・LC8・LC11（`RD4`〜`RD6`） |
| NFR5.1.x・NFR5.2.x・NFR5.3.1 | 必須のテスト・時差を固定する・E2E の流れを足さない | 全部品（`SD9`） |
| NFR6.1.1 | 文言は日本語と英語・鍵で持つ・時差の表記は言語に依らない | LC7・LC8 |

## 失敗の領域と、及ぶ範囲（爆発半径）

| ID | 失敗 | 及ぶ範囲 | 守り |
|---|---|---|---|
| LC12.1 | 保存の 1 文の失敗（接続・ロック待ち・型の誤り） | その要求だけ。5 項目は 1 文で確定するため、一部だけ新しい値が残らない。他の利用者の行に及ばない | 既存のプリファレンスの保存と同じ失敗の扱い。排他の待ちの上限切れは、`GlobalExceptionHandler` の想定外の例外の扱い（例外のクラスの名前だけを ERROR に出し、応答は 500 `INTERNAL_ERROR`。例外の種類で決まり、問い合わせの種類には依らない）に乗る。既存の `MePreferencesLockTimeoutLeakIT` を新しい文でも通す（`RD2`・`OD2`） |
| LC12.2 | 提供元の読み込みの失敗 | その利用者の画面の時刻の欄と、プリファレンスの選択の初期表示だけ（`error`）。他の機能・サーバーに及ばない | ブラウザのタイムゾーンへ落とさず、時刻の欄を出さず、再読み込みを出す（`RD4`） |
| LC12.3 | 古い答えの競り合い | 画面の 1 つの値 | 世代で比べ、遅れた答え・ログアウト後の答えを捨てる。`ready` を遅れた失敗で `error` に落とさない（`RD4`） |
| LC12.4 | 保存済みの名前を今のブラウザが解釈できない | 管理の画面の時刻の欄だけ | ブラウザのタイムゾーンで出し、時差の数字を必ず付ける（`saved-unresolvable`。AC1.2.4 と BR2.22 の差は「承認済みの記録との差」の D8） |
| LC12.5 | 移行の失敗 | アプリの起動 | 既存の移行と同じく失敗したら起動しない。列を足すだけで、前の版のアプリは足した列を読まずに動く（`RD1`） |
| LC12.6 | 構造の検査の取りこぼし | 暗黙のタイムゾーンの書き方が画面に入る | 規則の対象を `src/` のテストでないファイル全体にし、規則の効きを `Linter` の単体テストで固定する（`SD6`） |

可用性・拡張・復旧のために新しい常駐の処理・外部の依存・キャッシュは足さない（`SC`）。

## 共有する資源

- 共有する資源は、名前の不変の集合（LC1。読み取りだけ）と、提供元の 1 つの状態（LC6。ログイン中の利用者 1 人に 1 つ）と、時刻変換の部品の整形器のキャッシュ（U1 の部品。上限 128 個、いっぱいで全部消す）だけ。
- 選択肢の組み立て（LC7）は約 400 個の整形器を作るため、整形器のキャッシュを使い切って消す。その後の管理の画面の時刻の整形は、使う 1 つの名前の整形器を作り直すだけ（約 0.05 ミリ秒）で、結果は変わらない。キャッシュの上限は、時刻変換の部品の承認済みの決まりのまま変えない。
- 内部DB の接続は、読み取りも保存も 1 要求で 1 本（既存のプリファレンスと同じ）。監査を書かないため、確定の後の 2 本目は無い（`SC2`）。

## 捨ての試しの結果（要点。試しのコードはリポジトリの外に置き、コミットしていない。依存は足していない）

JDK 25.0.5・Node 26.13.0（Apple M2）・Jackson 3.1.7・H2 2.4.240・Hibernate 7.4.13（いずれもアプリの lockfile と同じ版）。見本の対象DB・アプリの内部DB は使っていない。

- **T1（JDK の名前の集合）**: `ZoneId.getAvailableZoneIds()` は **呼び出しのたびに別の可変の集合の写しを返す**（同じ集合ではない）。604 個で、`UTC`・`GMT` を含み、`Z` と `+09:00` のような固定のオフセットは含まない。最長は 32 文字。呼び出しごとの写しの費用は約 0.0055 ミリ秒（5.5 マイクロ秒）、作り置いた不変の集合の探索は約 0.000005〜0.00002 ミリ秒で、差は約 300〜1000 倍。絶対値は小さい（要求ごとに作り直しても約 5 マイクロ秒）が、不変の集合を 1 回持つ設計の根拠になる。`Set.copyOf` で作った集合に `contains(null)` を呼ぶと `NullPointerException` になる。**文字列でない型・欠けた値で `contains` を呼ばない** 作りにする（`SD1`）。
- **T2（ブラウザの名前の一覧と時差の文字）**: Node の `Intl.supportedValuesOf('timeZone')` は 418 個で、`UTC` を含まない（足す設計が要る）。時刻変換の部品と同じ整形器の作り方（同じオプション・上限 128 個で全部消すキャッシュ）で 418 個の時差を求める費用（Intl の部分だけ。時刻変換の部品の解析と jsdom の費用は含まない）は、新しい別のプロセス 5 回で **29〜54 ミリ秒**（最初の 1 回だけ 54.1、残りは 28.9・29.1・29.1・31.6）、同じプロセスの 2 回目（キャッシュが全部入れ替わる）で約 19〜22 ミリ秒、キャッシュの上限なしで 418 個の整形器を作る費用は約 16.5 ミリ秒、作り置いた整形器で整形するだけで約 3〜4 ミリ秒。名前の昇順への整列と文字の組み立ては 0.1〜0.3 ミリ秒。目標（100 ミリ秒）に対し、Intl の部分だけで 1.9 倍（冷たい最初の 1 回）〜3 倍以上の余裕がある。時刻変換の部品の解析の上乗せと jsdom での値は **この段では測っていない**（Code Generation の単体テストで測って記録する。`PD4`）。
- **T3（本文の 3 状態の受け取り）**: `Optional<String>` は、項目が無いときも `null` のときも `Optional.empty()` になり **区別できない**（使わない）。`JsonNode` は、項目が無い → Java の `null`、`null` → `NullNode`、文字列 → テキスト、数・オブジェクト・配列 → そのままの種類の値、で **区別できる**。`Object` は `null` と欠けた値を区別できない。文字列でない型を `Optional<String>` で受けると、数は文字列に変わり、オブジェクト・配列は読み取りの例外になる（400 の形が既存の本文の誤りと同じになるが、項目の誤りとしては出ない）。`JsonNode` なら、文字列でない型も受けて `INVALID_VALUE` の誤りにできる（BR2.2）。
- **T4（保存の 1 文）**: H2 の `SELECT user_id, display_time_zone FROM FINAL TABLE (UPDATE users SET … , display_time_zone = CASE WHEN :change THEN :tz ELSE display_time_zone END WHERE user_id = :id)` は、(a) 3 状態（変えない・消す・置く）を 1 つの文で表せ、(b) 保存後の列の値を同じ文の結果として返し、(c) 行が無ければ結果が空になる。Hibernate 7.4.13 の `createNativeQuery`（名前つきの束縛）で、`null` の束縛に `CAST` を付けなくても通ること、`updatable = false` の列を持つ実体の読み取りがその後も通ることを確かめた。Spring Data JPA 上の動き（返す型の対応）と Spring のトランザクションの中での動きは **この段では確かめていない**（Code Generation の結合テストで確かめる）。

- **T5（構造の検査の選択子）**: `security-design.md` の SD6 の 4 つの選択子（`:has` と正規表現つきの属性を使う）を、リポジトリの ESLint（`frontend/node_modules`）の `Linter`（フラットな設定）に流した。13 の断片（止める 8・通す 5）が期待どおりに止まる・通る。止める: `resolvedOptions` の呼び出し、`timeZone` の無い `Intl.DateTimeFormat`（`new` あり・なし）、引数なし・ロケールだけ・日時の項目だけの `toLocaleString`、`timeZone` の無い `toLocaleDateString`・`toLocaleTimeString`。通す: `timeZone` を持つ整形、数値の整形の項目を持つ `toLocaleString`、`Intl.NumberFormat`。実物の設定への組み込み（既存の選択子の持ち越し・例外の 1 ファイル）は Code Generation で確かめる。

## 承認済みの記録との差（承認済みの文書は書き換えず、ここに記録する）

| ID | 差 | 理由 | 決定 |
|---|---|---|---|
| D1 | NFR Requirements の NFR2.1.2 は、保存で「追加の読み取り・存在確認の問い合わせを足さない」とし、機能設計の 1.2 は `Keep` のときも「保存済みの値を返す」とする。そのままでは、`Keep` で保存した後に表示のタイムゾーンを返すために読み取りが 1 つ増え、両立しない | 1 文で更新と結果の取り出しを行う（LC3）。T4 で H2・Hibernate の両方で成り立つことを確かめた。両方の要件を緩めずに満たす具体化 | 質問にせず決定。**Code Generation の結合テストで Spring Data 上の動きが確かめられなかったときは、読み取りを 1 つ足す形が要件の緩和になるため、依頼者に諮る**（`Assumptions & Open Questions`） |
| D2 | NFR2.1.4 は新しい場面の判定を「成功と拒否を `name` で分けた p95 1 秒」とする。この Intent と過去の Intent で、VM の時計のずれが `http_req_duration` を崩した記録がある | 1 秒の目標は変えない。`name` の p95（NFR のとおり）と、操作の種類（`op`）ごとの繰り返しの時間の p95（時計のずれに強い。1 回の繰り返しに要求 1 つ）の **両方** を判定に置く。判定が厳しくなる向きの追加で、どちらかが落ちたら落ちとして扱い、時計のずれを疑うのは原因を確かめた後 | 質問にせず決定（`PD6`） |
| D3 | NFR Requirements のレビュー R-02: NFR2.1.6 の 100 ミリ秒の判定が負荷の高い CI で揺れる | 100 ミリ秒は延ばさない。判定は 5 回の測りの中央値、失敗時は 5 回の値・個数・環境を出す。時刻変換の部品の性能の測りと同じ形 | 質問にせず決定（`PD4`） |
| D4 | NFR Requirements のレビュー R-03: NFR1.4.6 の規則が数値の `toLocaleString()` を誤検知しうる・`src/shared/` が止める範囲から漏れる | 規則の対象を `src/` のテストでないファイル全体にし、日時の整形の疑いのある書き方は止める。数値の整形を許す書き方は、`Intl.NumberFormat`、または数値の整形の項目だけを持つ `toLocaleString` の呼び出し。いまの `src/` に数値の `toLocale…` は 0 件で、書き換えは要らない（`SD6`） | 質問にせず決定 |
| D5 | NFR Requirements のレビュー R-01: 追跡の NFR2.1 が OK のまま、測っていない部分が括弧書きにしか出ない | 追跡の NFR2.1 を `Deferred` にし、設計済みの部分と、Build and Test が持ち主の測っていない部分を target の冒頭に分けて書く（過去の Intent と同じ扱い） | 質問にせず決定 |
| D6 | NFR Requirements の [assumption]（3 状態の受け取り方は Code Generation で決める）を、この段で決める | T3 のとおり `Optional` は使えず、`JsonNode` を使う | 質問にせず決定 |
| D7 | NFR Requirements の NFR4.1.4 と機能設計は、移行の最後の版を前提にした既存のテストを「3 つ」程度と見込んだ | 読み取りで確かめた結果、最後の版の番号を固定しているのは `AuditMigrationCompatibilityIT` の 1 つだけ（`CURRENT_LAST_VERSION = 13`。14 にし、コメントの「V10〜V13」を直す）。`RoleMigrationCompatibilityIT` は版 10・11・12 の写しで固定しており V14 で変わらない。`AuditSchemaIT` は最後の版を数えず、V13 の列の確かめだけ。ただし件数は Code Generation の計画で検索して確定する | 質問にせず決定（`RD3`） |
| D9 | NFR2.1.7 は「1 ページの描画で、整形の呼び出し回数が表示する時刻の数を超えない」とする。一方、機能設計の BR2.20 は、ロックの解除の予定の表示で、解除の予定と今の時刻の **両方** を整形して日付を比べると定める。今の時刻の整形は表示される時刻ではないため、そのままでは数を超える | 今の時刻の整形は **行ごとではなく 1 ページにつき 1 回** にし、上限を「表示する時刻の数 + 1」と読む（`PD7`）。表示する時刻 1 つにつき 1 回という趣旨と、行数に比例した無駄な整形をしない目的は保たれる。目標（1 つの値の変換が平均 0.05 ミリ秒以内、1 ページ最大 1 万個で 500 ミリ秒以内）は変えない | 質問にせず決定（要件の趣旨の範囲内の具体化。承認の場で伝える） |
| D8 | レビュー R-02（AC1.2.4 と BR2.22）: 管理の画面は、保存済みの名前を解釈できないとき案内を出さず、ブラウザのタイムゾーンで時差の数字つきの時刻を出す | NFR Requirements で承認の場に諮る点とされている。この段の設計は、案内を足さない形（承認済みの機能設計のとおり）で書く。依頼者が案内を足す形を選んだ場合は、機能設計に戻す。設計の部品（LC8 の `DisplayTime`）は、案内の行を足す口を既に持てる形（`source` を読むだけ）にしてあり、足しても提供元・選択肢の設計は変わらない | 承認の場で確かめる（設計は未決のまま変えない） |

## 後の段へ引き継ぐこと

- **Code Generation の計画で洗い出す検索**（NFR4.1.4・NFR4.1.7・NFR4.1.8）: 最後の移行の番号（`CURRENT_LAST_VERSION`・`V13`・`MIGRATIONS_AFTER_PREVIOUS`）、`PreferencesCommand`・`PreferencesResult`・`PreferencesResponse`・`PreferencesRequest` を 4 つの引数で作るテスト、`Preferences(` の生成（`Preferences` は変えないため影響は小さい見込み）、`formatDateTime`・`formatLockedUntil`・`lockedUntil`・`timeZone` の props（管理の画面 3 つとテスト）、`preferencesSample`・`hasPreferencesShape`・`/api/me/preferences` を差し替える E2E（080・100・110）、`perf/README.md` の場面の表と閾値の表。
- **Build and Test が持ち主の確かめ**（測っていない。`Deferred`）: 既存の 3 場面と新しい場面の p95 と `checks`、実機でのログイン後の取得の回数、書き換えた E2E の全件の通過と axe の違反 0 件、カバレッジ、`./gradlew verify` と CI の時間。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`frontend-components.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/`（5 つの要件・`tech-stack-decisions.md`・`reviews/review-01.md`）
- 同じ段の文書: `security-design.md`・`performance-design.md`・`reliability-design.md`・`scalability-design.md`・`observability-design.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C2・C3）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/user/`・`backend/src/test/java/cherry/mastersmith/audit/domain/AuditMigrationCompatibilityIT.java`・`role/RoleMigrationCompatibilityIT.java`・`frontend/src/app/`・`frontend/src/shared/format/timeZone/`・`frontend/eslint.config.js`・`perf/k6/scenarios.js`・`frontend/e2e/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 部品の置き場（LC1〜LC11 を既存のどのクラス・ファイルの中に置くか）と、実際のクラス・関数の分け方は Code Generation の計画で決める。既存の境界テストと SpotBugs の除外は緩めない前提。
- [assumption] 保存の 1 文（LC3）は、Spring Data JPA の `@Query(nativeQuery = true)` で返す型（行の有無と、列が `null` の違いを区別できる 2 列の結果）を使えば成り立つ。T4 は Hibernate の `createNativeQuery` までで、Spring Data 上の動きは未確認。**確かめられなかったときは、読み取りを 1 つ足す形か、更新の後に別の文で読む形になり、NFR2.1.2 の緩和にあたるため、緩める前に依頼者に諮る。**
- [assumption] 時刻変換の部品の解析の上乗せ（T2 は Intl の部分だけ）を足しても、418 個の組み立てが 100 ミリ秒（jsdom・5 回の中央値）に収まる。Code Generation の単体テストで測り、外れたときは目標を緩めず原因を確かめて依頼者に諮る。
- 承認の場で依頼者に確かめる点: D8（AC1.2.4 と BR2.22 の食い違い。管理の画面に案内を足さない扱いのままでよいか、足す形に機能設計へ戻すか）。NFR Requirements から引き継ぐ。
- 未確認: Spring のトランザクションの中での保存の 1 文の動き・実ブラウザ（Chromium 以外）での名前の一覧の個数と時差の文字・描画（約 400 個の `option`）の時間。
