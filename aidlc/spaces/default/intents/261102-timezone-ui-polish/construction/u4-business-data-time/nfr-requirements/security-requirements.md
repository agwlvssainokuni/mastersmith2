# セキュリティとその他の非機能の要件 — u4-business-data-time

出典: 要件（`inception/requirements-analysis/requirements.md` の NFR1・NFR3〜NFR6）、機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md` の D9〜D44・W5、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C1・C2・C4）、`team.md`（業務データ・他テーブル参照・日時のタイムゾーンの変換の必須のテスト、Code Style の業務データの決まり）、`project.md`（Forbidden・Mandated）、質問と答え（`nfr-requirements-questions.md`: Q1: A）。

この単位は種類 ui で、成果物は性能・セキュリティ・技術の選択の3つ。アクセシビリティ・互換・テスト・言語の要件も、この文書にまとめる（U5 と同じ）。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。この単位に当たる要件を、上流の枝番の下に `.1` から振る。上流に当たる ID が無いもの（漏えい・画面に出す値・依存）は、近い上流の NFR の枝番に寄せた。

| 寄せた要件 | 寄せた先 | 理由 |
|---|---|---|
| 業務データの値の漏えい・`labelValueType` の出し方・画面に出す値（NFR5.1.3〜NFR5.1.5） | NFR5.1（日時の変換のテストは `team.md` の必須の一覧に従う） | `team.md` の業務データ・他テーブル参照の必須のテストの項目として確かめるため |
| 新しい依存を足さないこと（NFR5.1.6） | NFR5.1 | 依存の確かめは検査（OSV-Scanner）とテストの範囲のため |
| 定義の応答の互換（NFR4.2.1） | NFR4.2（メニューの応答は足すだけ） | 同じ「応答に項目を足すだけ」の互換の決まりのため |

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR3.1.1 | 業務データの一覧・詳細・作成・更新・検索の画面のアクセシビリティを、実際のブラウザの axe（WCAG 2.1 AA）で違反 0 件にする。ブランドカラーとテーマのすべての組で、次の状態を含める: 案内の1行の3つの文（設定あり・ブラウザ・解釈できない名前）、時点を表す型の値（端数 6 桁・長い時差 `+05:45`）と長いカラムの名前、読めない値の誤り、存在しない時刻の誤り、重なる時刻の2択（未選択・選択済み）、表示のタイムゾーンの読み込みの失敗の帯。規則・要素を外さない（`disableRules`・`exclude` を使わない） | NFR3.1・D44・`project.md` の Forbidden・`project.md` の学び（誤りの状態で初めて出る不足） | 既存の `185-business-data-accessibility.e2e.ts` に状態を足す（API の差し替えと型を付けた見本で作る）。流れの本数に数えない。違反の件数を組と状態の名前で記録する |
| NFR3.1.2 | 入力欄はラベル・必須・案内（`aria-describedby`）・誤り（`aria-invalid` と文）を持ち、保存・検索の押下で最初の誤りの欄（2択の未選択は legend）へフォーカスが移る。2択は fieldset と legend で読まれ、矢印キーで移り Space で選べる。キーボードだけで、入力 → 2択の選択 → 保存ができる。画面の部品ごとに `vitest-axe` を1件（誤りの状態と2択を出した状態を含める） | NFR3.1・D18・D21・D39・AC5.2.3 | 画面の部品のテスト（Testing Library・user-event・vitest-axe）。make-you-chic-ui の部品（`TextInput`・`RadioGroup`・`FormField`）に頼る振る舞いも、このリポジトリのテストで確かめる |
| NFR3.1.3 | 画面全体の横のスクロールを出さない（375 px を含む）。入力の案内（長いタイムゾーンの名前と例）・2択・案内の1行が折り返して画面の中に収まる | NFR3.1・`project.md` の学び（はみ出しの確かめ） | 185 の axe の流れで、375 px の幅でも `scrollWidth` が画面の幅を超えないことを確かめる |
| NFR4.2.1 | 定義の応答の変更は、参照の項目に `labelValueType` を足すだけにする（`kind`・`status`・`options`・`picker` の名前・型・値を変えない）。状態が `AVAILABLE` でないときは載せない（`NON_NULL`）。前の版の画面は知らない項目として読み捨て、新しい画面は項目が無い・知らない値のとき「型が分からない」として今のまま文字で出す。業務データの値の形（C4: 行の値・要求の本文・検索の値）は変えない。内部DB・環境変数・設定の変更は無い | NFR4.2 と同じ読み方・D30・D31 | サーバーの単体テスト（`labelValueType` の有無と値、JSON の形）。画面の写しのテスト（項目が無い・`null`・知らない値で、応答を拒否しない） |
| NFR5.1.1 | 日時の変換のテストは、`team.md` の日時の必須の一覧に従う。画面の側で受け持つ項目: 日付の変わり目（`2026-11-01T15:00:00Z` → `Asia/Tokyo` で `2026-11-02 00:00:00 +09:00`、月末・年末・うるう日）、夏時間（`America/New_York` の切り替わりの前後の表示、存在しない時刻の誤り、重なる時刻の2択）と端数の時差（`+05:45`・`+09:30`・`-03:30`）、秒の端数（0・3・6 桁の表示・初めの値・送る値で落ちない）、範囲の端（読めない値を元の文字で出す・範囲の検索の両端と重なる時刻）、往復（変えていない欄を送らない・主キーと参照の値を元の文字で送り返す）。3種類の対象DB と型・サーバー側の往復の結合テストは U3 が受け持つ（統合済み） | NFR5.1・`team.md`・D41 | 画面の単体テスト（Vitest）。U3 の結合テストは変更なしで通ること |
| NFR5.1.2 | 性質ベースのテスト（fast-check）を、初めの値と送る値を作る関数・検索の条件を作る関数に当てる（D43: 変えていない欄は送られない、時点の初めの値を時点に直すと元の時点に戻る（重なる時刻を除く）、時差を持たない日時は表示のタイムゾーンを変えても変わらない、範囲の重なる時刻は始まり ≤ 終わり）。失敗したときの乱数の種を出力に残し、再現できるようにする | `team.md` の性質ベースのテスト・D43 | 画面の単体テスト（fast-check）。種を固定して再実行できること |
| NFR5.1.3 | `labelValueType` は型の名前（`ValueType` の語彙）だけで、参照先の行の値・ラベル・主キーの値を足さない。状態が `NO_PERMISSION`・`TOO_MANY` のときは載せない（参照先の権限を欠く利用者に、参照先のカラムの型も明かさない）。参照の項目の応答の `toString` は今のとおり値・ラベルを出さない | `team.md` の他テーブル参照の必須のテスト (a)・(d)・D30 | サーバーの単体テスト（状態ごとの有無、`toString`）。他テーブル参照の既存の認可・漏えいのテスト（`*SecretLeakIT` を含む）が変更なしで通ること |
| NFR5.1.4 | 業務データの値（時刻の値・主キー・参照のラベル）と、入力した日時の文字を、画面のコンソール・エラーの報告・E2E の題・注記・添付・報告の json に出さない。日時の変換の誤り（`gap`・`overlap`・`invalid`）は種類だけで扱い、値を誤りの記録に載せない | `project.md` の Forbidden・`team.md` の業務データの必須のテスト（漏えい） | 画面の単体テスト（コンソールへの出力が無いこと）。E2E の報告の json を文字列で検索し、流れで使った値・資格情報が入っていないこと |
| NFR5.1.5 | 画面に出す値（時刻の値・読めない値の元の文字・参照のラベル・タイムゾーンの名前）を信頼できない入力として扱い、`<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含んでも文字として出す（HTML として描かない、リンクの先にしない）。案内の1行のリンクの先は、登録したプリファレンスの道の定数だけ（タイムゾーンの名前や値から道を作らない） | `team.md` の業務データの必須のテスト（画面に出す値）・D9・D40 | 画面の単体テスト（危ない文字を含む読めない値・ラベル・名前で、要素が増えず文字として出ること） |
| NFR5.1.6 | 新しい依存（npm・Gradle）を足さない。変換は U1 の関数と `Intl`、部品は make-you-chic-ui の今の固定先の部品、テストは今の Vitest・Testing Library・vitest-axe・fast-check・Playwright・JUnit を使う | `team.md` の Code Style（依存のライセンスの確かめ）・`tech-stack-decisions.md` | lockfile（`package-lock.json`・`gradle.lockfile`）に差が出ないこと |
| NFR5.2.1 | 日時を扱うテストは、機械の既定のタイムゾーンに結果を預けない。画面の単体テストはタイムゾーンを引数で明示に渡し、今の時刻は注入した時計から取る。E2E は Playwright の `timezoneId` を今の固定の値のままにし、流れの中で表示のタイムゾーンをプリファレンスで `America/New_York` に変えて確かめる（ブラウザのタイムゾーンと表示のタイムゾーンが違う状態を通す） | NFR5.2・`team.md` | 画面の単体テストが、Node の `TZ` を変えても同じ結果になること（U1 と同じ確かめ）。E2E の設定を変えないこと |
| NFR5.3.1 | N6 の日時の代表の E2E を1本足す（Q1: A）。流れ: 自分で作った利用者で、プリファレンスの表示のタイムゾーンを `America/New_York` にする → 業務データの作成の画面で、存在しない時刻 `2026-03-08 02:30:00` の誤りが出て保存できないことを確かめる → 重なる時刻 `2026-11-01 01:30:00` を入れ、2択で「後（-05:00）」を選んで保存する → 一覧で `2026-11-01 01:30:00 -05:00` と見える → 更新で別の欄だけ直して保存し、日時が変わらない。使う表は、流れの専用のスキーマの、時点を表す型のカラムを持つ表（今の見本の表 `e2e_item` に日時のカラムが無いため足す。置き場と DSL の版の扱いはコード生成の計画で決める）。前のテストの状態・見本の行・初期管理者を変えず、資格情報はプロセスの環境変数で渡す。既存の E2E（180・185・190・195）の書き換えは本数に数えない | NFR5.3・Q1: A・`team.md` の E2E の決まり | E2E（`./gradlew e2eTest`）。Intent の最後（Build and Test）とリリースの前に全体を流す |
| NFR6.1.1 | 足す文言（案内の1行の3つの文とリンク、入力・検索の案内、読めない値・存在しない時刻・2択の未選択の誤り、2択の legend と選択肢）は、日本語と英語の両方を用意し、既定は日本語。タイムゾーンの名前と時差は文字として差し込む。使わなくなる「UTC で入れる」の文言は残さない | NFR6.1・D40・`frontend-components.md` の 5 節 | 画面の単体テスト（日英の文言の鍵がそろっていること。既存の文言の鍵の検査） |

## 脅威の見立て（U4 の範囲）

| 脅威 | 入口 | 守り |
|---|---|---|
| 業務データの値の漏えい（ログ・トレース・報告） | 画面のコンソール、E2E の報告、サーバーのログ | 値をコンソール・誤りの記録に出さない（NFR5.1.4）。サーバーは値・SQL を変えない（D29・D30）。既存の `*SecretLeakIT` が通ること |
| 参照先のカラムの存在・型の推測 | 定義の応答の `labelValueType` | `AVAILABLE` のときだけ載せる（NFR5.1.3）。権限を欠く利用者には参照先の情報を足さない |
| 画面への HTML・スクリプトの差し込み | 時刻の値・読めない値・ラベル・タイムゾーンの名前 | 文字として描く（NFR5.1.5）。リンクの先は定数だけ |
| 夏時間の扱いの誤りによる別の時点の保存 | 存在しない時刻・重なる時刻の入力 | 画面で拒否・2択（D20・D21）。サーバーは時点として存在する値だけを受ける（ADR-002）。API を直接呼ぶ人の存在しない時刻は別の時点として保存されうる（ADR-002 の受け入れた危険） |
| SQL の差し込み | 検索の値 | 画面は書式の確かめで止め、サーバーは今の SQL の組み立て（識別子の照合・バインド変数）のまま（D29） |

## この単位に当たらない inception の NFR

| ID | 理由 |
|---|---|
| NFR1.1 | 自作の SVG の安全は U6（この単位はアイコンを足さない） |
| NFR1.2 | 表示のタイムゾーンの設定の本人だけの読み書きは U2（この単位は読むだけ） |
| NFR1.3 | メニューの応答の `tableKind` は U5 |
| NFR2.2 | ホームの API は U5 |
| NFR3.2 | アイコンの読み上げは U5・U6（この単位はアイコンを足さない） |
| NFR3.3 | タイムゾーンの選択のキーボードと読み上げは U2 |
| NFR4.1 | 内部DB の列の追加は U2（この単位は内部DB を変えない） |

## 上流との差（承認済みの要件・設計は書き換えず、ここに記録する）

- 機能設計のレビュー（`construction/u4-business-data-time/functional-design/reviews/review-01.md`）の指摘は、決まり（`project.md`）に従い、承認の場で扱う。とくに Major（D11 と D12 の食い違い: 表示のタイムゾーンの読み込みが失敗したときのフォーム）は、直し方で NFR3.1.1 の axe の状態（失敗の帯の状態でフォームを出すか）が変わる。この文書は機能設計の今の書きぶりで書き、直したときに合わせる。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（NFR1・NFR3〜NFR6）
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C1・C2・C4）
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/nfr-requirements-questions.md`（[Q1] A）
- 今のコード（読み取りだけ）: `frontend/e2e/180-business-data-flow.e2e.ts`・`185-business-data-accessibility.e2e.ts`・`support/businessDataSchema.sql`・`support/businessDataDsl.ts`、`backend/src/main/java/cherry/mastersmith/businessdata/web/ReferenceFieldResponse.java`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] E2E の表を足す場所（今の実行のスキーマに表を足すか、別のスキーマか）と、共有の DSL（`businessDataDsl.ts`）の版の扱いは、180・190・195 への影響を洗い出してコード生成の計画で決める。
- [assumption] E2E の流れで2択の「後（-05:00）」の表示の時差は、実際のブラウザの `Intl` が `America/New_York` の 2026 年の切り替わりを持つ前提（U1 の捨ての試しと同じ）。
