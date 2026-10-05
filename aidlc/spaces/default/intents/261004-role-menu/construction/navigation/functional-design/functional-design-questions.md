# 機能設計の質問 — U5 navigation

対象の単位: U5 navigation（kind: service、大きさ M、Bolt B7）。業務のメニューの木を作業ロールの実効の主権限で絞って返す API（`/api/me` の下、ログインだけ）と、テーブルの画面の置き場の権限の問い合わせ、`NavigationBoundaryArchitectureTest`（US5.1 のバックエンドの部分、契約 C9 の持ち主）。

質問は 6 問です。上流・承認の場・先に確定した単位（dsl-v2・role・cross-cutting）・`team.md`・`project.md`・コードで決まっている点は、下の「決まっていること」に書き、質問にしていません。各段の承認の場（監査ログの GATE_APPROVED）の入力はすべて「Approve」で、設計の文書と違う決定はありませんでした。

---

## 決まっていること

### 業務の決まり（要件・ストーリー・契約）

- 業務のメニューは適用済みの DSL の `menus` の木から作り、管理のメニューは返さない（管理のメニューは画面の登録から U7 が作る）（FR9.1・FR10.5、契約 C9、components.md の Navigation）。
- テーブルを指す項目は、作業ロールでのそのテーブルの階層の実効の主権限が NONE なら出さない。カラムの値は見ない（テーブルが NONE でカラムに明示の READ・FULL があっても出さない）（FR10.1、AC5.1.2）。
- 子がすべて出ないまとまりは出さない（FR10.2）。テーブルと子の両方を持つ項目は、テーブルが READ 以上ならテーブルへ移れるまとまり、テーブルが NONE で子が1つでも見えれば移れないまとまり（`table` は null、`navigable` は false）、テーブルが NONE で子がすべて見えなければ出さない（AC5.1.2、契約 C9）。
- 作業ロールが無い・適用済みの DSL が無い・`menus` が空のときは、誤りにせず空の木を返す（FR9.5、AC5.1.6・AC5.1.17、契約 C9）。
- 判定はサーバー側で行い、権限の無い項目を応答に含めない。メニューを出さないことを、サーバー側の判定の代わりにしない（FR10.4・FR10.5、`project.md` の Mandated「役割・権限による判定は、API ごとにサーバー側で行い…」）。
- テーブルの置き場の問い合わせは、READ・FULL なら 200（表示名と主権限）、NONE と今の DSL に無いテーブルは同じ 403 `ACCESS_DENIED` で、表示名も返さない（FR10.4・FR8.3、AC5.1.4、契約 C9）。メニューに出ていないテーブルでも、DSL にあり READ 以上なら 200（判定は FR8 の口だけ）。
- `label` は DSL から来る信頼できない値としてそのまま返し、画面が文字としてエスケープして出す（FR9.6、AC5.1.7、`team.md` の N 階層のメニューのテストの決まり）。
- `team.md` の★「一覧に無い `icon` を拒否するか既定のアイコンにするか」は、要件で「既定のアイコンにする」と決まっている（FR9.6、AC5.1.7）。サーバーは許した名前でなければ既定（`list`）に置き換えて返す（契約 C9）。DSL の検証では icon の名前を問わない（dsl-v2 の entities.md の DslMenuItemV2）。
- `team.md` の★「変更の前に出したトークンの扱い」: 権限・作業ロールはトークンに入れず、要求ごとに内部DB から求めるため、作業ロールの切り替え・設定の変更の次の要求から木と問い合わせの結果が変わる（ADR-003、AC5.1.5・AC5.1.11）。
- 深さの上限は 5 段（`menus` の直下を 1 段目）。投入・復元では拒否し、起動時の読み直しでは深すぎる枝を落とした木を適用中にする（dsl-v2 の BR2.1〜BR2.3）。U5 が受け取る `DslModel.menus` はいつも 5 段以内である。
- メニューの項目はテーブルを `{schema, name}` の組で指し、適用中のモデルでは指す先が必ずある（dsl-v2 の BR1.6。起動時の読み直しも深さ以外の検証を通す）。AC5.1.14 の「DSL に無いテーブルを指す項目」は、絞る関数を単独で確かめるための入力で、解決の口が NONE を返すため落ちる。
- `label` は `DisplayName`（`ja`・`en`）で、空の文字列は「未設定」として許される（`dsl/domain/DisplayName.java`、dsl-v2 の DslMenuItemV2）。

### 使う口（先に確定した単位）

- 適用中の DSL は `dsl.service.ActiveDslModelProvider.current()`（`Present(model, dslHash)` か `Absent`）。版 2 のモデルは `schemas` と `menus` を持ち、組でテーブルを引ける。名前は大文字と小文字を区別する（契約 C3、dsl-v2 の BR8.1・BR8.2）。
- 実効の権限は `role.service.EffectivePermissionResolver`。1回の要求で `snapshotFor(userId)` を1回だけ呼び、写しの `main(schemaName, tableName, null)` でテーブルの階層の値を見る。写しを要求をまたいで持たない。作業ロールが無い・DSL が無い・DSL に無い対象はすべて NONE（契約 C5、role の BR5.3・BR5.4、role の functional-spec.md 10節の引き継ぎ）。
- API の分類の注釈は `common.security` の `ApiAccess`（値 `ApiAccessLevel` の PUBLIC・AUTHENTICATED・ADMIN）。U5 が足す2本の API には U5 が AUTHENTICATED の印を付ける（契約 C1、cross-cutting の entities.md ENT-001・ENT-002）。
- 403 の code は既存の `access.domain.AccessProblemTypes.ACCESS_DENIED` を使い回し、機能の側で重ねて定義しない（`team.md` の Code Style。`useradmin.web.UserAdminController` が同じ形で使っている）。
- make-you-chic-ui のアイコンの名前は 18 個（menu・chevron-down・chevron-up・close・check・bell・user・search・edit・trash・download・settings・home・list・info・success・warning・danger。`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Icon/registry.ts`、固定先 e82b651）。画面の側は `app/registry` に同じ 18 個の一覧を置き、型 `IconName` と一致することを型の検査で確かめる（cross-cutting の frontend-components.md）。
- make-you-chic-ui の入れ子のサイドバー（5bf1ffe、固定先の更新は B9）の `navSections` の項目は `id`・`label`・`icon?`・`href?`・`current?`・`children?` で、開閉の状態は `navExpandedIds`（項目の `id` の集まり）で持つ。業務と管理の項目の `id` の重なりを避ける形は U7 が決める（cross-cutting の frontend-components.md）。

### チームの決まり（`team.md`・`project.md`）

- 新しい機能 `navigation` の業務処理の層は、想定内の失敗（403 にする「見られない」）を結果の型（sealed interface の record）で返し、画面入出力の層が `switch` で `BusinessException` に変える（`team.md` の Code Style）。
- `navigation` のテストに `NavigationBoundaryArchitectureTest` を置き、依存してよい機能と依存される側を書く（`team.md` の Code Style、要件 C4）。
- 絞る関数は純粋な関数にし、jqwik の性質ベースのテストを当てる。失敗時の乱数の種を記録する（`team.md` の役割・権限と N 階層のメニューのテストの決まり、NFR6.2、AC5.1.15）。
- 足す API のすべてに、未認証 401・停止中の利用者は通らない、をサーバー側のテストで確かめる。2本ともログインだけの API のため「その権限を持たない 403」の組は無い（分類の網羅は U1 の検査が見る）（`team.md`、NFR1.2・NFR1.3、AC5.1.5）。
- 認証の主体は引数に出さず要求の文脈から読む（`team.md` の Code Style）。U5 は個人に関する値（メールアドレス・氏名）を受け取らず返さない。

---

## この段で決める設計の要点（質問にしない案。まとめの確認で確かめる）

1. **パッケージの形**: `navigation.domain`（木の値・絞る純粋な関数・アイコンの照らし合わせ・結果の型）、`navigation.service`（DSL の提供口と解決の口を読んで木を作る。内部DB に書かず、トランザクションは読み取りだけか不要）、`navigation.web`（2本の API、DTO の record、結果の型から `BusinessException` への変換）。`repository` は置かない（表を持たない）。
2. **依存の向き**: `navigation` → `dsl.service`・`dsl.domain`、`role.service`・`role.domain`、`access.domain`（`ACCESS_DENIED` だけ）、`auth.domain`（要求の文脈の主体 `AuthenticatedUser` を読むため）、`common`。どの機能も `navigation` に依存しない。これを `NavigationBoundaryArchitectureTest` に書く。既存の境界テストは緩めない（`role` の境界テストは `navigation` から読まれることを既に許している）。
3. **1回の要求の流れ（メニュー）**: 主体の `userId` → `current()` を1回 → `Absent` なら空の木 → `snapshotFor(userId)` を1回 → 純粋な関数で絞る。写しの `dslHash` と `current()` の `dslHash` が違う（読み取りの間に DSL が適用し直された）ときは、古いメニューの項目は新しい DSL に無ければ NONE で落ちるだけで、権限の無い項目は出ない。そのため読み直しはしない（決めた側の動作としてテストに書く）。
4. **絞る関数の性質（jqwik、AC5.1.15）**: (a) 残った項目のうちテーブルを指して移れる項目は、すべて実効が READ・FULL。(b) 子の無いまとまり（`table` が null で `items` が空）が無い。(c) 残った項目の親子と並びは元の木と同じ（元の木から項目を消しただけ）。(d) すべて NONE なら空。(e) 権限を強める（NONE を READ にする）と、残る項目は減らない（単調）。(f) 深さは元の木の深さを超えない。
5. **アイコンの置き換え**: `icon` が null・空・許した名前の一覧に無い（大文字と小文字を区別し、前後の空白を許さない）ときは `list` を返す。置き換えのたびのログは出さない（要求ごとに同じ行が出続けるため）。DSL を投入するときの警告にもしない（dsl-v2 で名前を問わないと決まっている）。
6. **テーブルの置き場の問い合わせ**: 主体と組を受け、`current()` が `Absent`・組が DSL に無い・実効が NONE はすべて同じ結果「見られない」にして 403 `ACCESS_DENIED`（本文は既存の共通の形だけ）。応答の時間で有無を見分けにくいよう、DSL に無いときも解決の口を通す（解決の口が NONE を返す）。名前の長さの上限（例: 各 256 文字）を超える入力は 400 `VALIDATION_FAILED`（有無によらず同じ）。
7. **応答の並びと値**: 木の並びは DSL の順のまま。`label` の空（未設定）はそのまま返し、画面が既定の表示（例: テーブルの表示名や「—」）を当てる（U7 への引き継ぎ）。応答に `dslHash`・ロールの ID・権限の値（メニューの木では `navigable` 以外）を載せない。
8. **監査・指標**: メニューの API は読み取りで監査に残さない。指標は Spring の既存の HTTP の指標（`http.server.requests`）だけで、新しい指標は足さない（NFR5.2 は管理の API と作業ロールの切り替えが対象）。応答時間の目標（NFR2.2・AC5.1.18）の測り方は NFR 要件の段で決める。
9. **TRACE のログ**: 受け渡す値はスキーマ名・テーブル名・表示名・権限の値で、個人に関する値・秘密を持たないため、伏せ字の型は使わない（`SecretLeakIT` は置かない）。主体は `userId` だけを引数に渡す。

---

## Q1 メニューの表示名（`label`）とテーブルの表示名の形

背景: 契約 C9 は `label: string`・`displayName` を文字列と書いていますが、DSL の表示名は `ja`・`en` の組で、既存の DSL の API は `{ja, en}` をそのまま返し、画面が表示の言語（`useDisplayLanguage`）で選んでいます（`frontend/src/features/dsl/DslMenuTree.tsx`）。dsl-v2 でもスキーマの表示名を同じ組にしました（dsl-v2 の Q1: A）。AC5.1.12 は言語を英語にしたときの表示を求めます。

A. 両方の言語を `{ja, en}` の組で返し、画面が表示の言語で選ぶ。テーブルの置き場の問い合わせの `displayName` も同じ組にする。契約 C9 は持ち主の U5 がこの形に直す（項目の型の変更のため、使う側の U7 の機能設計で受け入れを確かめる）（推奨: 既存の DSL の API と同じ形で、言語を切り替えても読み直しが要らない。サーバーは要求の言語を知らなくてよい）
B. 契約どおり文字列1つで返し、サーバーが要求の `Accept-Language`（既存の `AcceptLanguageResolver`）で言語を選ぶ。言語を切り替えたら画面がメニューを読み直す
C. 契約どおり文字列1つで、いつも日本語を返す（英語の表示は DSL の値では行わない）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 メニューの項目の識別（`id`）を返すか、その形

背景: 入れ子のサイドバー（`navSections`）は項目ごとに `id` を要し、開閉の状態（AC5.1.9）も `id` の集まりで持ちます。契約 C9 の `NavNode` に `id` はありません。まとまりは名前が重なりうるため、表示名からは一意の `id` を作れません。作業ロールを切り替えると絞った後の木の形が変わるため、絞った後の位置で `id` を作ると、開いていたまとまりと違うまとまりが開くことがあります。

A. サーバーが、絞る前の DSL の木での位置の道（例: 1段目の 3 番目の下の 1 番目なら `2.0` のような、0 から数えた番号を `.` でつないだ文字列）を `id` として返す。同じ DSL の間は作業ロールを切り替えても同じ項目は同じ `id` で、開閉の状態が保たれる。DSL を適用し直すと変わりうる（開閉が閉じに戻るだけ）。管理のメニューの `id` との重なりを避ける前置き（例: `biz:`）は U7 が付ける。契約 C9 に `id` を足す（足すだけの互換の変更）（推奨: 一意で、作業ロールの切り替えに強く、サーバーが DSL の元の木を知っているため作りやすい）
B. `id` は返さず、U7 が絞った後の木の位置から作る（契約のまま）。作業ロールを切り替えると、開いていたまとまりと違うまとまりが開くことがある
C. サーバーが、テーブルを指す項目は組（スキーマ名とテーブル名）、まとまりは A の位置の道を `id` にする。同じテーブルを指す項目が DSL に2つあると重なるため、そのときは位置の道に戻す
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 空の木の理由を返すか

背景: 契約 C9 は空の木を `items` が空で返すだけです。画面イメージのホーム（`mockups.md` 1.3）は、「作業ロールに見てよいテーブルが無い・ロールが無い」と「適用済みの DSL が無い・`menus` が空（業務のメニューが設定されていません）」で文言を分けています。`items` だけでは画面が2つを見分けられません。

A. 応答に、空のときの理由 `emptyReason` を足す。`NOT_CONFIGURED`（DSL が無い・`menus` が空）か `NOTHING_VISIBLE`（作業ロールが無い・すべて NONE で絞った結果が空）で、空でなければ null。契約 C9 に足す（足すだけの互換の変更）（推奨: 画面イメージの2つの文言をそのまま出せる。DSL の有無はログイン中の利用者に見えて困る値ではなく、表の名前や権限の値は出さない）
B. 理由は返さない（契約のまま）。画面は1つの文言にまとめ、画面イメージ 1.3 との差を U7 の設計に書く
C. 理由は返さず、画面が作業ロールの API（契約 C8 の `current`）と組み合わせて「ロールが無い」だけを見分ける。DSL が無い・`menus` が空・すべて NONE は同じ文言にする
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q4 テーブルの置き場の問い合わせの道の形

背景: 契約 C9 は `/api/me/tables/{schemaName}/{tableName}/access` で、名前を道の中に入れます。DSL のスキーマ名・テーブル名は空でない任意の文字列で（JSON Schema に文字の制限が無い）、対象DB によっては `/`・`.`・`%`・空白を含みえます。今の Spring Security の要求の検査は、エンコードした `/`（`%2F`）・`%25`・`..`・`;` を含む道を 400 `REQUEST_REJECTED` で拒否します（`access/web/AccessRequestRejectedHandler.java`）。そのため、権限のあるテーブルでも名前によっては問い合わせられません（有無によらず 400 になるため、有無が漏れることはありません）。

A. 名前を問い合わせの引数で受ける（例: `GET /api/me/table-access?schema=…&table=…`）。どの名前でも道の検査に当たらず、エンコードの扱いが単純になる。契約 C9 の道を変える（使う側の U7 の機能設計で受け入れを確かめる）（推奨: AC5.1.13 の `/`・`..`・`%` を含む名前でも、権限があれば 200 を返せる）
B. 契約どおり道の中に入れ、`/`・`%`・`..`・`;` を含む名前のテーブルは問い合わせられない（400）ことを既知の制約として記録する
C. 契約どおり道の中に入れ、この API に限って要求の検査を緩める（エンコードした `/` を許す）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q5 アイコンの許した名前の一覧の持ち方（サーバーと画面）

背景: 契約 C9 の未決です。サーバーが置き換えて返し、画面の側も `app/registry` に 18 個の一覧を持ちます（cross-cutting の決定）。make-you-chic-ui の固定先を上げてアイコンが増減したときに、2つの一覧がずれると、サーバーが許した名前を画面が知らない（または逆）ことになります。どちらの側も、知らない名前は既定（`list`）にするため、ずれても画面が壊れることはありません。

A. サーバーは `navigation.domain` に Java の定数で 18 個を持って置き換え、画面は受け取った `icon` を自分の一覧でもう一度照らす（知らなければ `list`）。2つの一覧の一致は、固定先を上げる手順（README の固定先の更新の手順）に「両方を直す」と書くだけで、テストでは突き合わせない
B. A に加え、2つの一覧の一致をテストで確かめる。サーバーの一覧を1つのテキストのファイル（`backend/src/main/resources/` の下）に置いてサーバーはそれを読み、画面のテストがそのファイルを読んで `app/registry` の一覧と一致することを確かめる（ずれれば `./gradlew verify` が落ちる）（推奨: 固定先を上げたときの直し忘れを関門で止められる。画面の側の型の検査（`IconName` との一致）と合わせて、make-you-chic-ui・画面・サーバーの3つがつながる）
C. サーバーは照らさず `icon` をそのまま返し、画面だけが照らす（契約 C9 の「サーバーが置き換えて返す」を変える）
X. Other (please specify)

[Answer]: B **Mode:** guided

## Q6 テーブルの置き場の問い合わせの拒否（403）を監査に残すか

背景: 今の監査の `ACCESS_DENIED` は、管理の API の入口（Spring Security のフィルターの段）で管理者でない人を拒否したときの記録です（`access/domain/AdminAccessDeniedEvent.java`）。テーブルの置き場の 403 は、業務データの権限による拒否で、今は準備中の画面の読み取りだけです。拒否は既存の例外の変換で WARN を1行（code と状態だけ、テーブルの名前は出さない）出します。

A. 監査に残さない（既存の WARN の1行だけ）。業務データの拒否をどう残すかは、業務データに触れる J・K で一覧・詳細の拒否とあわせて決める（推奨: 今は準備中の画面の表示だけで業務データに触れない。古いメニューや再読み込みで普通の利用者にも起きるため、監査が拒否の行で埋まりやすい）
B. 既存の `ACCESS_DENIED` の種類で監査に残す（操作した人・道。問い合わせの引数のテーブルの名前は残さない）。管理の API の拒否と同じ種類に業務の拒否が混ざる
C. 新しい監査の種類（例: `TABLE_ACCESS_DENIED`、32 文字以内）を足し、操作した人とスキーマ名・テーブル名を残す。`audit` に種類と出来事の受け取りを足す作業が U5 に付く
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（navigation）:

- Q1 A: メニューの表示名とテーブルの表示名は `{ja, en}` の組で返し、画面が表示の言語で選ぶ。契約 C9 は持ち主の U5 がこの形に直す（使う側の U7 の機能設計で受け入れを確かめる）。
- Q2 A: 項目の `id` は、絞る前の DSL の木での位置の道（例 `2.0`）を返す。管理のメニューと重ならない前置きは U7 が付ける。契約 C9 に足す。
- Q3 A: 空の木のとき `emptyReason`（`NOT_CONFIGURED` か `NOTHING_VISIBLE`）を返す。契約 C9 に足す。
- Q4 A: テーブルの置き場の権限の問い合わせは、名前を問い合わせの引数で受ける（例 `GET /api/me/table-access?schema=…&table=…`）。契約 C9 の道を変える。画面の道 `/tables/{スキーマ名}/{テーブル名}` が同じ検査に当たる点は U7 に引き継ぐ。
- Q5 B: アイコンの許した名前の一覧は resources のテキストのファイル1つに置き、サーバーはそれを読み、画面のテストがそのファイルと `app/registry` の一覧の一致を確かめる。
- Q6 A: テーブルの置き場の 403 は監査に残さない（既存の WARN の1行だけ）。業務データの拒否の残し方は J・K で決める。
- 「この段で決める設計の要点」（パッケージは domain・service・web、依存の向きと境界テスト、1回の要求で `current()` と `snapshotFor` を1回ずつ、絞る関数の6つの性質、アイコンの置き換えのログを出さない、置き場の問い合わせの拒否の寄せ方と名前の長さ、応答の並びと値、監査と指標、TRACE）もこのまま設計に入れる。同じテーブルを指す項目が複数あるときの今の項目の決め方は U7 に引き継ぐ。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
