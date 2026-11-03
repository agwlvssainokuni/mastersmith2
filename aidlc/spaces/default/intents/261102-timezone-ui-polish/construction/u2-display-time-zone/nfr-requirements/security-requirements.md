# セキュリティの要件 — u2-display-time-zone

出典: 機能設計（`construction/u2-display-time-zone/functional-design/functional-spec.md`・`rules.md` の BR2.1〜BR2.24・`reviews/review-01.md`）、要件（`inception/requirements-analysis/requirements.md` の NFR1〜NFR6）、契約 C2・C3（`inception/contract-design/contract-summary.md`）、質問と答え（`nfr-requirements-questions.md`: Q1: A・Q2: A）、`team.md`（Testing Posture・Code Style・Deployment）、`project.md`（Forbidden・Mandated）、既存のコード（読み取りだけ: `PreferencesRequest`・`TraceAspect`・`frontend/eslint.config.js`・`frontend/e2e/`）。

U2 は、表示のタイムゾーンの設定の API（利用者の表に列を1つ足す）・プリファレンスの選択・配布・管理の画面の時刻の単位（種類 service）。新しい API の道は足さず、既存の `/api/me/preferences` の項目を1つ足し、画面がログイン後に1回読む。この文書は、セキュリティ（本人だけ・信頼できない入力・漏えい・依存とソースの決まり）、アクセシビリティ、言語の要件を置く。性能は `performance-requirements.md`、拡張は `scalability-requirements.md`、観測は `observability-requirements.md`、信頼性・互換と戻しは `reliability-requirements.md`、技術の選択とテストは `tech-stack-decisions.md`。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使い、細目は末尾に枝番を足す（例: NFR1.2 の細目は NFR1.2.1・NFR1.2.2）。上流の NFR に当たる行が無い追加の要件は、上流の最後の枝番の次から振る（NFR1.4・NFR1.5）。

## 1. セキュリティ（NFR1）

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR1.2.1 | 表示のタイムゾーンは本人だけが読み書きする。対象の行は認証の主体から決め、本文から決めない。未認証は 401（`AUTHENTICATION_REQUIRED`）。管理者が他人の分を変える API・道は作らない | NFR1.2・BR2.7・`project.md` Mandated（役割・権限の判定はサーバー側） | サーバー側の結合テスト: 未認証の GET・PUT が 401、認証済みの GET・PUT が自分の行だけを読み書きする。道に利用者 ID を取る口が無いこと（API の分類の網羅のテストが、既存の `/api/me/**`＝ログインだけの分類のまま変わらず通ること） |
| NFR1.2.2 | 一括代入の防止: 保存の要求の本文に、利用者 ID・他人の設定・利用停止や管理者の印・メールアドレスなどの知らない項目を足しても読み捨て、自分の行の5項目だけが変わる。他人の行と、自分の行のほかの列（停止・管理の権限・失敗回数・メールアドレス）は変わらない | NFR1.2・BR2.7・AC1.1.5・AC1.1.6 | 結合テスト: 知らない項目を足した PUT の後、別の接続で読み直して自分の5項目以外・他人の行が変わっていないこと。表示のタイムゾーンの保存で `admin_flag` などの列が変わらないこと |
| NFR1.2.3 | 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のどれでも拒否される今の動作を変えない。停止中の利用者の GET・PUT は表示のタイムゾーンを読み書きできない | `project.md` Mandated（利用者の状態の判定はすべての入口で）・BR2.7 | 結合テスト: 停止した利用者のアクセストークンで GET・PUT が拒否され（既存の停止のテストと同じ形）、停止を解いた直後に受け付けること |
| NFR1.4.1 | 名前の値は信頼できない入力として扱い、サーバー側の検証を正とする（画面の選択肢に頼らない）。受けるのは、長さ 1〜64 文字で、サーバーの名前の集合（`ZoneId` が持つ地域の名前）への大文字小文字を区別した完全一致だけ。前後の空白は除かず、固定のオフセット表記（`+09:00`・`Z`・`UTC+9`）・空の文字列・文字列でない型（数・真偽・配列・オブジェクト）・集合に無い名前は 400 `VALIDATION_FAILED`（`fieldErrors` の field は `displayTimeZone`、reason は既存の `INVALID_VALUE`）。拒否のときはほかの4項目も保存せず、内部DB を変えない。長さの確かめは集合の照合より先に行い、極端に長い文字列で集合の探索や文字列の複写を重ねない | BR2.2〜BR2.4・AC1.1.3・AC1.1.4・`team.md` Code Style（検証はサーバー側が正） | 結合テスト（パラメーターを使う表）: 長さ 64 は受け付け・65 は拒否、空、前後の空白つき、大文字小文字違い（`asia/tokyo`）、`+09:00`・`Z`・`UTC+9`・`GMT+9`、数・真偽・配列・オブジェクト、`'`・`"`・`;`・`--`・改行・NUL を含む文字列、10 万文字の文字列が、すべて 400 で内部DB の値が変わらないこと。jqwik の性質ベース（失敗時の乱数の種を記録）: 集合の要素は常に有効、集合の外・オフセット表記・65 文字以上は常に無効、任意の文字列で例外が外へ出ない |
| NFR1.4.2 | 項目が無い（変えない）・`null`（消す）・名前（置く）の3つを区別し、混同して値を消さない。項目が無いときに保存済みの値を変えないこと、`null` で確実に NULL になること、4項目だけの保存で列が変わらないことを、サーバー側で保証する。前の版の画面の PUT（5つ目を送らない）が保存済みの値を消さない | BR2.3・BR2.10・AC1.1.2 | 結合テスト: 保存済みの名前がある利用者に、項目なしの PUT → 値が残る、`null` の PUT → NULL、名前の PUT → 置き換わる。いずれも別の接続で読み直して確かめる |
| NFR1.4.3 | 漏えいの防止: 検証を通る前の名前の値（無効な入力の文字列）を、アプリのログ・トレースの属性・エラー応答に載せない。検証を通る前の値を持つ型（保存の要求の型と、検証の前に値を持つ業務処理の入力の型）は `toString` で5つ目の値も伏せる（`TraceAspect` が web・service・domain・repository の引数と戻り値を TRACE のログに文字列で出すため）。検証を通った名前だけを持つ型（応答・名前の値の型）は名前を出してよい。氏名の伏せ字は今のとおり保つ。400 の応答の `detail`・`fieldErrors` に入れた値を含めない | BR2.4・BR2.9・レビュー R-03・`team.md` Code Style（個人に関する値を持つ record の `toString`）・`project.md` Forbidden | TRACE を有効にした `PreferencesSecretLeakIT`（既存の `MeSecretLeakIT` と同じ形）: 無効な名前（目印のある文字列）で PUT しても、ログ・トレースの属性・応答に目印が出ないこと。氏名が出ないこと。外部へ送る設定を有効にした結合テストで、トレースの属性に名前の値が出ないこと。型の `toString` の単体テスト |
| NFR1.4.4 | 表示のタイムゾーンの保存は、成功も拒否も監査に残さない（今のプリファレンスと同じ）。監査の対象の種類・列を足さない | BR2.8・契約 C3 | 結合テスト: 保存の成功・400 の後、監査の行の数が増えないこと |
| NFR1.4.5 | 画面は、サーバーが返した名前・保存済みの名前・ブラウザが返した名前を信頼できない入力として扱い、文字として描く（HTML として描かない・リンクの先にしない・属性に連結しない）。案内の Alert、選択肢の名前、読み込み失敗の文に `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含む名前が入っても、文字として出る。`dangerouslySetInnerHTML` を使わない | `team.md` Testing Posture（画面に出す値）・Code Style（`react/no-danger`） | 画面の単体テスト: 保存済みの名前が `<img src=x onerror=…>` を含む応答（形は有効）を読んだとき、Alert と選択肢に文字として出て、要素が増えないこと |
| NFR1.4.6 | ブラウザの既定のタイムゾーンを暗に使わない構造の検査を、ESLint の規則1つに決める（レビュー R-04）。ブラウザのタイムゾーンを読む呼び出し（`Intl.DateTimeFormat` の `resolvedOptions` の取り出し）は、配布の部品の1か所（取り出し方を差し替えられる関数）にだけ許し、ほかの `src/` のソース（テストを除く）では止める。あわせて、`timeZone` を省略した整形（`Date` の `toLocale*String` と `Intl.DateTimeFormat` の生成）を、時刻変換の部品の外の `src/features/`・`src/app/` で止める。使われなくなる既存の `formatDateTime`・`formatLockedUntil` は消す（残さない）。管理の画面の部品の props の `timeZone`（省略可）は外す | BR2.13・BR2.19・BR2.22・AC1.2.6・レビュー R-04・`team.md` Code Style（暗黙の既定を使わない） | `npm run lint` の違反 0 件。規則が効くことは、ESLint の `Linter` API で違反の断片（`resolvedOptions` の別の場所での呼び出し・`toLocaleString()` の省略形）を流して報告が出る Vitest の単体テストで確かめる。`formatDateTime`・`formatLockedUntil` の import が 0 件で、ファイルが無いこと |
| NFR1.4.7 | 配布は、ログアウト・未ログインになったら値と世代を捨て、別の利用者のログインに前の利用者の表示のタイムゾーンが残らない。古い答え（前の要求の世代）で今の値を上書きしない。値は保存先を持たない（ブラウザの `localStorage` などに置かない） | BR2.14 | 画面の単体テスト: ログアウト→別の利用者でログインで前の値が出ないこと、遅れて届いた古い答えが捨てられること（待ち合わせで確実に作り、時間に預けない）。ブラウザの保存先を使っていないこと |
| NFR1.5.1 | 新しい依存を足さない（バックエンドは JDK の `ZoneId`・Spring・既存の JPA、画面は既存の make-you-chic-ui の `Select`・`FormField`・`Alert` と `Intl`）。足すことになったときは、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残し、lockfile と OSV-Scanner の対象に入れる | `team.md` Code Style（新しい依存） | コード生成の差分で `backend/build.gradle.kts`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` に新しい依存が増えていないこと |
| NFR1.5.2 | ソースの先頭に Apache License 2.0 のヘッダー（`/* ... */` の形、年 `2026`、著作権者名 `agwlvssainokuni`）を置き、フォーマット・リンタ・静的解析（SpotBugs ＋ FindSecBugs）を通す。新しい例外のメッセージ・文言にサーバーの内部情報を載せない。層の決まり（画面入出力は DB アクセスを直接呼ばない・トランザクションは業務処理の層・要求と応答は `record` の DTO）を守り、既存の境界テストと SpotBugs の除外は緩めず、足す形にする。`user` の機能の境界テストが無ければ、手を入れる Bolt で今の依存をそのまま書いて足す | `project.md` Mandated・`team.md` Code Style | `./gradlew verify`（Spotless・SpotBugs・ArchUnit・ライセンスヘッダー検査） |
| NFR1.5.3 | 新しい code・状態コードを足さない。入力の誤りは既存の `VALIDATION_FAILED`（400）、未認証は既存の `AUTHENTICATION_REQUIRED`（401）、reason は既存の `INVALID_VALUE`。問題の種類の定数（`XxxProblemTypes`）に新しい行を足さない | `team.md` Code Style（共通の code は使い回す） | 問題の種類の一覧のテスト（既存の `*ProblemTypeCatalog` のテスト）が変更なしで通ること。400 の応答の code・reason の結合テスト |

## 2. アクセシビリティ（NFR3）

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR3.1.1 | プリファレンスの画面に足す表示のタイムゾーンの選択が、WCAG 2.1 AA を満たし、実際のブラウザの axe で違反 0 件。状態は、通常・保存済みの名前を解釈できない Alert（warning）・Select の下の誤りの文（400 の `displayTimeZone`）・長い名前の選択肢を含め、ブランドカラーとテーマのすべての組で行う。規則は外さない（`disableRules`・要素の `exclude` を使わない） | NFR3.1・BR2.23・`project.md` Forbidden（axe の規則を外さない）・Testing Posture | E2E（既存の 080 の状態に追加。足す状態は本数に数えない）: 通常・解釈できない名前・誤り・長い名前で、ブランドカラーとテーマのすべての組の違反 0 件を件数で記録。画面部品ごとに vitest-axe を1件（選択・案内・読み込み失敗の文・再読み込みの部品） |
| NFR3.1.2 | 管理の画面の時刻の欄（読み込み中の表示・読み込み失敗の文・再読み込み）も、WCAG 2.1 AA を満たす。読み込み中は読み上げに「読み込み中」と伝わり、色だけで状態を伝えない。淡い文の文字のコントラストが AA（4.5:1）を下回らない（ブランドカラーとテーマのすべての組） | NFR3.1・BR2.15・BR2.21 | 時刻を出す共通の部品の vitest-axe。既存の管理の画面の E2E の axe に、読み込み中と読み込み失敗の状態は差し替えた応答で足し、違反 0 件を件数で記録（本数に数えない） |
| NFR3.3.1 | タイムゾーンの選択は、キーボードだけで選べ、選んだ値と時差が読み上げられる。標準の `<select>` の動き（矢印・先頭の文字での移動・Enter・Esc）を損なわず、アクセシブルな名前を持ち（ラベルと結び付く）、各選択肢の文字が `Asia/Tokyo (+09:00)` の形で読み上げられる。「ブラウザに合わせる」は選択肢の文字から意味が分かる。誤りの文・案内は `aria-describedby` などで結び付く | NFR3.3・BR2.23・AC1.1.7 | 画面の単体テスト（user-event のキーボードだけの操作）で選び、値が変わること。実際のブラウザ（Playwright）で、キーボードだけで選んで保存でき、選択のアクセシブルな名前と選んだ項目の文字（名前と時差）が取れること |
| NFR3.3.2 | 約 400 個の長い名前の選択肢（例: 名前の最も長い地域）と狭い幅（375px）で、選択が画面からはみ出さず、画面全体の横のスクロールを出さない。開いた選択の一覧は画面に固定で置く部品として画面の中に収まる | `project.md` Testing Posture（画面のはみ出しの確かめ）・NFR3.3 | E2E の既存のはみ出しの確かめ（375px と通常の幅）に選択を含め、画面全体と選択の外枠が画面の中に収まること |
| NFR3.2 | N/A（この単位の画面にアイコンは足さない） | — | — |

## 3. 言語（NFR6）

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR6.1.1 | 足す画面の文言（選択の名前・ブラウザに合わせる・説明・拒否の文・解釈できない名前の警告・読み込み中・読み込み失敗の文・再読み込み）は、日本語と英語の両方を用意し、既定は日本語にする。文言は鍵で持ち、描画のたびに今の言語で引く。タイムゾーンの表記は言語に依らず IANA の名前と今の時差を `Asia/Tokyo (+09:00)` の形で示す。拒否の文は一般の文で、サーバーの `detail` を画面に出さない | NFR6.1・BR2.24・BR2.18 | 文言のテスト: 日本語・英語のすべての鍵が存在し、空でなく、鍵の抜けで落ちること。言語を切り替えて選択の説明と警告が切り替わること。選択肢の文字が両言語で同じ形であること |

## 4. 上流との差（承認済みの機能設計・契約・要件は書き換えず、ここに記録する）

- **機能設計のレビュー R-02 — AC1.2.4 と BR2.22 の食い違い（承認の場で依頼者に諮る）**: ストーリーの AC1.2.4 は、保存済みの名前を今のブラウザが解釈できないとき、時刻を見る画面で設定を解釈できないことを画面に出すと定める。機能設計の BR2.22 は、管理の画面（利用者の一覧・招待の一覧・DSL の状態と履歴・確認の表示）に案内の行を足さず、案内をプリファレンスの画面と業務データの画面だけにする。そのため、**保存済みの名前を解釈できないとき、管理の画面は案内を出さず、黙ってブラウザのタイムゾーンで時刻を出す**（理由は、管理の画面の時刻には時差の数字が付き、どのタイムゾーンで出しているかが時刻自体から分かるため）。機能設計のトレーサビリティは AC1.2.4 を OK にしており、差の節も「ストーリー・要件との差: 無い」と書いていたが、実際は差がある。この段では要件として次を置く: (1) 差を隠さず、この節に記録する。(2) 管理の画面の時刻の整形は、`saved-unresolvable` のとき配布が返すブラウザのタイムゾーンで出し、時差の数字を必ず付ける（時差を省かない）。(3) 管理の画面に案内を足す形（案内の行を1つ足す）に直すかは**承認の場で依頼者に確かめる**。直す場合は機能設計の再実施になるため、直すなら機能設計に戻す。この段の要件は、どちらの結論でも変わらない（案内の文言は NFR6.1.1 と NFR3.1.2 の対象に加わるだけ）。
- **R-03（BR2.4 と BR2.9 の食い違い）の解消**: 承認済みの規則は書き換えず、この段の要件（NFR1.4.3）で「検証を通る前の値は伏せる。検証を通った名前だけ出してよい」に統一する。BR2.9 の「5つ目の名前は出してよい」は、検証を通った名前を持つ型にだけ当てる。
- **R-04（暗黙の既定の検査と整形の扱い）の決め**: NFR1.4.6 のとおり、ESLint の規則1つに決め、使われなくなる `formatDateTime`・`formatLockedUntil` は消す。機能設計の 5 節の「ファイルを消すか残すかは Code Generation の計画で決める」を、消す方針に決めた。
- **R-05（トレーサビリティの扱い）**: ログイン後の読み取りが1回で性能の目標を変えない点（AC5.3.3）は、この段で要件（NFR2.1.1〜NFR2.1.4）に置くが、**測っていない**。目標の確かめは Build and Test（k6 と問い合わせの数の結合テスト）が持ち主で、それまで Deferred。メニューの応答の互換（AC5.3.2）は、メニューの応答を変える単位が受け持ち、この単位は応答を変えないため確かめない。実行時のタイムゾーンの設定（AC5.3.4）は、この単位が変えない（NFR5.2.3）ことを確かめ、変えるときの4か所をそろえる決まりは変更が出た単位が受け持つ。
- **R-01（E2E への影響）の取り込み**: `reliability-requirements.md` の NFR4.1.6〜NFR4.1.8 に、E2E の見本・差し替えの回数・401 を待つ流れ・管理の画面の単体テストへの影響を要件として置き、件数の洗い出しは Code Generation の計画に渡す。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-spec.md`・`rules.md`・`reviews/review-01.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- ストーリー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/user-stories/stories.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 保存の要求の型の `toString` で5つ目を伏せる範囲（要求の型と、検証の前に値を持つ入力の型）の具体は、Code Generation で型を決めるときに確定する。要件は「検証を通る前の値を持つ型は伏せる」まで。
- [assumption] ESLint の規則の書き方（`no-restricted-syntax` の選択子か `no-restricted-properties`）は Code Generation で決める。要件は「規則1つ、許す場所は配布の1か所」まで。
- [assumption] 極端に長い文字列（10 万文字）の確かめは、既存の本文の大きさの上限（サーバーの設定）より前に拒否されることがあるため、拒否の状態コードが 400 か 413 かは実測で決め、決めた側の動作を明示したテストにする。
- 承認の場で依頼者に確かめる点: R-02（管理の画面に案内を足さない扱いをこのまま受け入れるか、案内を足す形に機能設計へ戻すか）。
