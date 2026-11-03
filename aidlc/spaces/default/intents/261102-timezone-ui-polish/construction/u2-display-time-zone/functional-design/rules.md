# 業務の決まり — u2-display-time-zone

出典: 要件（`inception/requirements-analysis/requirements.md` の FR1・FR4・NFR1.2・NFR3.3・NFR4.1・NFR6.1）、契約 C1・C2・C3（`inception/contract-design/contract-summary.md`）、設計の決定 ADR-001・ADR-003（`inception/domain-design/decisions.md`）、画面イメージ S1（`inception/refined-mockups/mockups.md`）、質問と答え（`functional-design-questions.md` の Q1: B・Q2: C・Q3: A と、「コードを読んで設計で決めること」の採用）、`team.md` の Code Style・Testing Posture。

```yaml
rules:
  - id: BR2.1
    statement: 表示のタイムゾーンは、利用者の設定の行（内部DB の利用者の表）に NULL 可の列を1つ足して保存する。値が無い状態が「ブラウザに合わせる」。新しい表は作らない。移行は前進のみで、足した列を消す移行は無い。
    category: constraint
    applies_to: UserPreferences（内部DB の利用者の設定の行）
    trigger: 表示のタイムゾーンを保存する・読む・移行を流すとき
    logic: IF 保存する値が名前 THEN 列にその名前を置く。IF 値が消す（null） THEN 列を NULL にする。既存の行は移行の直後は NULL（ブラウザに合わせる）。列の名前は display_time_zone、VARCHAR(64)、NULL 可、既定なし。
    violation_behaviour: 移行が失敗したら起動しない（既存の移行と同じ）。
    source: FR1.2・ADR-001・NFR4.1・AC1.1.2・AC1.1.8・AC5.3.1

  - id: BR2.2
    statement: 保存の要求の名前は、サーバーの ZoneId が持つ地域の名前の集合への、大文字小文字を区別した完全一致だけを受ける。前後の空白は除かず、長さの上限は 64 文字。受けた名前は送られたまま保存し、別名を正式名に直さない。固定のオフセット表記（+09:00・Z・UTC+9 など）は集合に無いため受けない。
    category: validation
    applies_to: TimeZoneNamePolicy（純粋な判定）
    trigger: 保存の要求に名前の値があるとき
    logic: IF 名前が文字列で、長さが 1〜64 で、ZoneId.getAvailableZoneIds() の要素と完全に一致する THEN 有効。ELSE 無効。判定は時計にも DB にも触れない純粋な関数で、同じ入力は同じ結果になる。
    violation_behaviour: BR2.4 の拒否。
    source: FR1.3・C3・AC1.1.3・AC1.1.4・ADR-001

  - id: BR2.3
    statement: 保存の要求の displayTimeZone は、項目が無い（変えない）・明示の null（消す）・名前の値（置く）の3つを区別して受ける。項目が無いときは保存済みの値を変えず、応答にも保存済みの値が出る。
    category: policy
    applies_to: PUT /api/me/preferences の本文の読み取りと保存
    trigger: 保存の要求を受けるとき
    logic: IF 本文に displayTimeZone が無い THEN 列を書かない（前の版の画面と互換）。IF 明示の null THEN 列を NULL にする。IF 文字列 THEN BR2.2 で照らして有効なら置く。ほかの4項目の保存とは1つのトランザクションで確定し、4項目だけの保存でも列は変わらない。
    violation_behaviour: 文字列でも null でもない型は BR2.4 の拒否。
    source: C3・AC1.1.2

  - id: BR2.4
    statement: 空の文字列・固定のオフセット表記・サーバーが知らない名前・文字列でない型は、入力の誤りとして拒否し、保存済みの値を変えない。拒否のときは、ほかの4項目も保存しない（全体を保存しない）。
    category: validation
    applies_to: PUT /api/me/preferences
    trigger: displayTimeZone の値が BR2.2 を通らないとき
    logic: IF 値が無効 THEN 400 VALIDATION_FAILED。fieldErrors の field は displayTimeZone、reason は既存の INVALID_VALUE（4種類すべて同じ）。REQUIRED は使わない（null は有効な値）。誤りの並びは 氏名・言語・テーマ・文字の大きさ・displayTimeZone の順で、ほかの項目の誤りと同時に返す。入れた値は応答・ログに載せない。
    violation_behaviour: なし（これが拒否の動作）。内部DB は変えない。
    source: FR1.3・C3・AC1.1.3・AC1.1.4

  - id: BR2.5
    statement: 既存の4項目（氏名・言語・テーマ・文字の大きさ）の検証・保存・誤りの形は変えない。4項目が本文に無いときの REQUIRED も今のまま。
    category: constraint
    applies_to: PUT /api/me/preferences
    trigger: 保存の要求を受けるとき
    logic: IF 4項目のどれかが無い・決まりに合わない THEN 今の誤りを返す。displayTimeZone の有無は4項目の判定に影響しない。
    violation_behaviour: 既存のテストが落ちる形にする。
    source: C3・NFR4.2

  - id: BR2.6
    statement: 自分の設定の読み取りは、保存した名前（無ければ null）を displayTimeZone に出す。値が null のときも項目を出す。保存済みの名前を読み出しのたびに再検証しない（サーバーの知る名前が後から変わっても返す）。
    category: calculation
    applies_to: GET /api/me/preferences と PUT の応答
    trigger: 読み取り・保存の応答を作るとき
    logic: 応答は displayName・language・theme・fontSize・displayTimeZone の5つ。解釈できるかの判断は画面の側。
    violation_behaviour: なし。
    source: C3・AC1.1.2

  - id: BR2.7
    statement: 表示のタイムゾーンは本人だけが読み書きできる。未認証は 401（AUTHENTICATION_REQUIRED）。本文の利用者 ID・他人の設定・利用停止や管理者の印などの項目は読み捨て、自分の行だけが変わる。管理者が他人の分を変える API は作らない。
    category: authorization
    applies_to: GET/PUT /api/me/preferences
    trigger: 要求を受けるとき
    logic: 対象の行は認証の主体から決め、本文から決めない。本文の知らない項目は無視する。
    violation_behaviour: 401。本文の改ざんは無視されて自分の設定だけが変わる。
    source: NFR1.2・AC1.1.5・AC1.1.6・A5

  - id: BR2.8
    statement: 表示のタイムゾーンの保存は、成功も拒否も監査に残さない（今のプリファレンスと同じ）。
    category: policy
    applies_to: PUT /api/me/preferences
    trigger: 保存の成功・拒否
    logic: 監査の出来事を出さない。
    violation_behaviour: なし。
    source: C3

  - id: BR2.9
    statement: 保存の要求・応答・業務処理の入力を文字列にするとき、氏名は伏せたままにする（今のとおり）。タイムゾーンの名前は個人に関する値ではないため、載せてよい。
    category: constraint
    applies_to: 要求・応答・コマンドの record の toString
    trigger: 層をまたぐ引数・戻り値が TRACE のログに文字列で出るとき
    logic: 今の4つの toString の氏名の伏せ字を保つ。5つ目の名前は出してよい。
    violation_behaviour: 漏えいのテスト（TRACE を有効にした SecretLeakIT）が落ちる。
    source: C3・team.md の Code Style

  - id: BR2.10
    statement: 前の版のアプリは、足した列を読まずにそのまま動く。前の版の PUT（5つ目を送らない）は保存済みの値を変えない。前の版へ戻すと選択は使えなくなるが、列は残る。
    category: constraint
    applies_to: 配備・戻し
    trigger: 前の版のアプリを新しい内部DB で起動する・前の版の画面が新しい API を呼ぶ
    logic: 列は NULL 可で既定なし。応答の5つ目は前の版の画面が読み捨てる。
    violation_behaviour: 互換の結合テストが落ちる形にする。
    source: NFR4.1・AC1.1.8・AC5.3.1

  - id: BR2.11
    statement: ログインの応答と現在の利用者の応答は変えない。表示のタイムゾーンはプリファレンスの API だけで読み書きする。
    category: constraint
    applies_to: 認証の応答
    trigger: ログイン・現在の利用者の読み取り
    logic: ログイン状態の preferences は言語・テーマ・文字の大きさのまま。
    violation_behaviour: なし。
    source: C3

  - id: BR2.12
    statement: 効いている表示のタイムゾーンは次の順で決める。保存済みの名前を今のブラウザが解釈できればその名前（source は saved）。保存が無ければブラウザのタイムゾーン（browser）。保存済みの名前を解釈できなければ、保存の値は変えずブラウザのタイムゾーン（saved-unresolvable）で、savedName に保存済みの名前を持つ。ブラウザのタイムゾーンも解釈できないときは UTC。
    category: calculation
    applies_to: DisplayTimeZoneContext
    trigger: 設定を読み込んだとき・保存したとき
    logic: 解釈できるかは canInterpretTimeZone で判定する（固定のオフセットは解釈できない名前）。loading のときは timeZone が null。ready のときは timeZone が必ず解釈できる名前。
    violation_behaviour: なし。
    source: FR1.5・C2・AC1.2.2・AC1.2.4

  - id: BR2.13
    statement: ブラウザのタイムゾーンを読む場所は、配布の部品の1か所だけにする。取り出し方は差し替えられる形にし、テストで固定する。ほかの画面・関数は、ブラウザの既定のタイムゾーンを暗に使わず、配布された名前を明示に受ける。
    category: constraint
    applies_to: DisplayTimeZoneContext・各画面
    trigger: 表示のタイムゾーンを決めるとき・時刻を整形するとき
    logic: 読む場所は Intl.DateTimeFormat().resolvedOptions().timeZone の呼び出し1か所。
    violation_behaviour: 構造の検査（ESLint の制限か単体テスト）で、ほかの場所の暗黙の既定の使用を止める。
    source: FR4.2・AC1.2.6・ADR-003・team.md の Code Style

  - id: BR2.14
    statement: 設定の読み込みは、ログインした後に GET /api/me/preferences を1回読む。読み込みの間は loading で、時刻の欄を出さない（ブラウザのタイムゾーンの時刻を一瞬出して切り替えることはしない）。要求の世代で比べ、古い答えは捨てる。未ログインになったら値を捨てる。新しい API は足さない。
    category: policy
    applies_to: DisplayTimeZoneContext
    trigger: ログイン状態が変わったとき
    logic: IF ログイン中で値が無い THEN 読む。IF 未ログイン THEN 値と世代を捨てて何もしない。読み込みが済むまで status は loading。
    violation_behaviour: なし。
    source: C2・AC1.2.5・AC5.3.3

  - id: BR2.15
    statement: 設定の読み込みが失敗したときは、status を error にし（契約 C2 に足す状態）、ブラウザのタイムゾーンへは落とさず、時刻の欄を出さない。時刻の欄には「表示のタイムゾーンを読み込めません」の短い文を出し、画面の上の1か所に「再読み込み」を置く。再読み込みが成功するか、プリファレンスの画面の読み込み・保存が成功したら ready になる。
    category: policy
    applies_to: DisplayTimeZoneContext・時刻を出す画面
    trigger: 読み込みの失敗（接続の失敗・サーバーの 5xx・形が違う応答）
    logic: IF 読み込みが失敗 THEN error。IF すでに ready で、読み直しだけが失敗 THEN 前の値を残して ready のまま。
    violation_behaviour: なし。
    source: Q1: B・AC1.2.5

  - id: BR2.16
    statement: プリファレンスの保存が成功したら、画面を読み込み直さず、応答の displayTimeZone で効いている値を差し替える。差し替えは表示中の画面の時刻にすぐ効く。
    category: policy
    applies_to: DisplayTimeZoneContext・プリファレンスの画面
    trigger: PUT の成功
    logic: 差し替えの口（保存した名前または null を渡す）をプリファレンスの画面が呼ぶ。ログイン中でなければ何もしない。
    violation_behaviour: なし。
    source: FR1.4・C2・AC1.2.1

  - id: BR2.17
    statement: 選択肢は次のとおりに組み立てる。先頭が「ブラウザに合わせる」（保存は null）。続けてブラウザが返す名前（名前の昇順、地域でまとめない平らな並び）を Asia/Tokyo (+09:00) の形（IANA の名前と今の時差）で並べる。ブラウザの一覧に UTC が無いときは UTC を足す（時差は +00:00、名前の昇順の位置）。保存済みの名前が一覧に無いときは、その名前を「ブラウザに合わせる」の次に足す（解釈できる名前には時差を添え、解釈できない名前は名前だけ）。ブラウザが知らない名前は並べない。
    category: calculation
    applies_to: プリファレンスの画面の選択
    trigger: プリファレンスの画面を描くとき
    logic: 今の時差は、注入できる現在時刻を U1 の整形に渡して得た時差の部分を使う。選択肢は一覧と現在時刻が変わらない間、作り直さない。選択は保存値を映す（保存値が選択肢に無いまま先頭を選んでいるように見せない）。
    violation_behaviour: なし。
    source: FR1.1・S1・NFR3.3・NFR6.1・AC1.1.1・AC1.1.7・Q3: A

  - id: BR2.18
    statement: プリファレンスの画面は、5項目を常にまとめて送る（ブラウザに合わせるは null）。保存の拒否（400 の displayTimeZone の誤り）は選択を残し、Select の下に誤りの文を出す。保存済みの名前を解釈できないときは Select の上に Alert（warning）を出す。保存の値は触らずに選び直せる。「元に戻す」と保存の有無の判定（dirty）に5つ目を含める。
    category: policy
    applies_to: プリファレンスの画面
    trigger: 画面の読み込み・保存・元に戻す
    logic: 画面の型の Preferences は displayTimeZone（文字列か null）を持つ。応答の形が違えば形の誤り（今のプリファレンスの形の確かめに5つ目を足す）。400 の fieldErrors の displayTimeZone は文言の鍵に写す。
    violation_behaviour: 拒否の文言は一般の文で、サーバーの detail を受けない。
    source: S1・AC1.1.3・AC1.1.4・AC1.2.4

  - id: BR2.19
    statement: 管理の画面（利用者の一覧・招待の一覧・DSL の状態と履歴・確認の表示）の時刻は、U1 の整形の文字から秒以下を落として分までにして（例 2026-11-02 09:00 +09:00）、表示のタイムゾーンで出す。言語に依らない書式。既存の formatDateTime は管理の画面では使わなくなる。サーバーが返した時点を読めないときは、元の文字をそのまま出す。
    category: calculation
    applies_to: OtherScreenTimes
    trigger: 管理の画面が時刻を出すとき
    logic: 薄い関数（shared/format/ に置く）が formatInstant の ok の文字から分までと時差を取り出す。unreadable は元の文字、invalid-time-zone は起きない前提だが起きたら元の文字。変換はこの関数の中で U1 だけを使う。
    violation_behaviour: なし。
    source: Q2: C・AC1.2.3・AC1.2.6・ADR-003・FR4.1

  - id: BR2.20
    statement: 利用者の一覧のロックの解除の予定は、表示のタイムゾーンで今日なら時刻と時差だけ（09:00 +09:00）、今日でなければ日付つき（BR2.19 の書式）で出す。ロックしているかは応答の locked を使い、ここで時刻を比べて判定しない。今の時刻は引数で受け（テストで固定する）、日付の比べも表示のタイムゾーンで行う。
    category: calculation
    applies_to: 利用者の一覧のロックの解除の予定
    trigger: ロック中の行を出すとき
    logic: 今日かどうかは、解除の予定と今の時刻のそれぞれを U1 で整形した文字の日付の部分が同じかで決める。formatLockedUntil は使わなくなる。
    violation_behaviour: なし。
    source: Q2: C・AC1.2.3

  - id: BR2.21
    statement: 設定の読み込み中は、時刻の欄を出さず、読み込み中の表示（淡い短い文。読み上げには読み込み中と伝える）にする。error のときは BR2.15 の文にする。ready になって初めて時刻を出す。
    category: policy
    applies_to: 時刻を出すすべての管理の画面
    trigger: status が loading・error のとき
    logic: 時刻の欄を出す共通の部品が status で出し分ける。画面は部品だけを使い、時刻を直接整形しない。
    violation_behaviour: なし。
    source: AC1.2.5・Q1: B

  - id: BR2.22
    statement: 管理の画面は表示のタイムゾーンを配布から読み、時刻の整形に明示に渡す。画面の引数としての timeZone は外す。管理の画面に表示のタイムゾーンの案内の行は足さない（時刻に時差が付くため）。保存済みの名前を解釈できない旨の案内は、プリファレンスの画面と業務データの画面の案内の行に出す。
    category: policy
    applies_to: OtherScreenTimes
    trigger: 画面を描くとき
    logic: テストは配布の提供元で固定のタイムゾーンを渡す。
    violation_behaviour: なし。
    source: FR4.1・AC1.2.3・AC1.2.6

  - id: BR2.23
    statement: プリファレンスの Select と案内は、キーボードだけで操作でき、選んだ値と時差が読み上げられる。足す画面部品は WCAG 2.1 AA を満たし、ブランドカラーとテーマのすべての組・誤りの状態（Select の誤り）・解釈できない名前の Alert・長い名前（長い名前の選択肢）で axe の違反を 0 件にする。規則は外さない。画面部品ごとに vitest-axe を1件入れる。
    category: constraint
    applies_to: プリファレンスの表示のタイムゾーンの選択
    trigger: 画面部品を作る・変える
    logic: 部品は make-you-chic-ui の Select・FormField・Alert を使い、標準の select の動きを損なわない。
    violation_behaviour: axe の違反があれば直す（規則の除外は禁止）。
    source: NFR3.3・NFR3.1・AC1.1.7・AC5.2.1・AC5.2.2・AC5.2.3

  - id: BR2.24
    statement: 足す画面の文言（選択の名前・ブラウザに合わせる・説明・拒否の文・解釈できない名前の警告・読み込み失敗の文・再読み込み）は、日本語と英語の両方を用意し、既定は日本語にする。
    category: constraint
    applies_to: プリファレンスの画面・管理の画面
    trigger: 文言を足すとき
    logic: 文言は鍵で持ち、描画のたびに今の言語で引く。
    violation_behaviour: 鍵の抜けは文言のテストが落ちる。
    source: NFR6.1
```

## 決まりの要約（導出。YAML が正）

| 決まり | 対象 | 要点 |
|---|---|---|
| BR2.1・BR2.10・BR2.11 | 保存・互換 | 利用者の行に NULL 可の列を足すだけ。前の版は読まずに動く。認証の応答は変えない |
| BR2.2〜BR2.6 | API | 名前は集合への完全一致。項目なし・null・値の3状態。拒否は INVALID_VALUE で全体を保存しない |
| BR2.7〜BR2.9 | 本人・監査・漏えい | 本人だけ。監査しない。氏名の伏せ字を保つ |
| BR2.12〜BR2.16 | 配布 | 保存→ブラウザ→UTC の順。読み込み中は時刻を出さない。失敗は error で時刻を出さない。保存で差し替え |
| BR2.17・BR2.18・BR2.23・BR2.24 | プリファレンスの画面 | 選択肢の組み立て、5項目を送る、誤りと警告、アクセシビリティ、日英 |
| BR2.19〜BR2.22 | 管理の画面の時刻 | 分までの書式、ロックの解除の予定、読み込み中の表示、案内は足さない |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C1・C2・C3）・`inception/domain-design/decisions.md`（ADR-001・ADR-003）
- 画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/refined-mockups/mockups.md`（S1）
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-design-questions.md`（Q1: B・Q2: C・Q3: A）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] BR2.2 の集合への完全一致は、JDK 25 とブラウザ（V8）で確かめた内容に基づく。別の JDK の版で集合が変わっても、完全一致の規則は変わらない。
- [assumption] BR2.17 の「ブラウザの一覧に UTC が無い」は手元の V8 の結果。ほかのブラウザで UTC が入っていれば足さない（重複させない）。
- [assumption] BR2.19 の分までの書式は、U1 の整形の文字の形（`YYYY-MM-DD HH:mm:ss[.fraction] ±HH:mm`）が変わらないことに頼る。形が変わるときは U1 の契約の変更として扱う。
- 型の名前・置き場・列の書き分けの実装（保存を2つの文にするか1つにするか）は Code Generation で決める。
