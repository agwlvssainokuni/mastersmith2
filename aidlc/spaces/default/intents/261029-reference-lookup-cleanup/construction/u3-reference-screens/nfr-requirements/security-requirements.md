# セキュリティ・アクセシビリティ・テスト・言語・互換性・供給網の要件 — U3 u3-reference-screens

出典: 機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/frontend-components.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR4〜NFR7・NFR9）。U3 は種類 `ui` の単位で `rules.md` を持たない。

U3 は種類 `ui` の単位で、段の定義により performance・security・tech-stack・traceability だけを作る。そのため、この文書に NFR1・NFR4〜NFR7・NFR9 を節に分けて書く。U3 は SQL・対象DB の接続・権限の判定を持たないが、画面で参照先の行の値（選択肢の値とラベル・検索の値・検索の結果のセル）を描き、E2E では対象DB と利用者の資格情報を使う。

## 上流の ID との対応

枝番は U3 の割り振りのとおり `NFRx.41` から振る（質問の「決まっていること」）。NFR2・NFR3 は `performance-requirements.md` に書いた。

| 上流 | この単位の扱い | この文書の ID |
|---|---|---|
| NFR1 セキュリティ | 部分（画面の側。権限の判定・SQL・ログ・トレースは U1・U2） | NFR1.41〜NFR1.47 |
| NFR4 アクセシビリティ | 当たる（全部） | NFR4.41〜NFR4.45 |
| NFR5 テスト | 部分（画面のテストと E2E。サーバーのカバレッジ・結合テストは U1・U2） | NFR5.41〜NFR5.43 |
| NFR6 多言語 | 部分（画面の文言。`code` の固定は U1・U2） | NFR6.41・NFR6.42 |
| NFR7 互換性 | 部分（定義の応答に足す欄を画面が読み落としても壊れないこと） | NFR7.41 |
| NFR8 運用 | 当たらない（U4・U5） | — |
| NFR9 供給網 | 当たる（画面の依存） | NFR9.41 |

## 1. セキュリティ（NFR1）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.41 | 参照先の行の値（選択肢の値とラベル、検索の結果のセル、選んだ行のラベル、ラベルの1件読みの答え）と、カラムの表示名を、信頼できない入力として文字で描く（`team.md` の必須テスト (f)）。`dangerouslySetInnerHTML` を使わず（oxlint の `react/no-danger`）、値から `href` を作らない（`javascript:` の道を作らない）。i18next の補間は `escapeValue` を切らない（`{{label}}`・`{{field}}`）（D6・D25・D28・D34、`frontend-components.md` の 5節） | XSS の見本（`<script>`・`<img onerror>`・`"`・`'`・`javascript:`）を1つにまとめ、`ReferenceSelectField`・`LookupPickerDialog`・`LookupField`（ラベル）の3つの画面の部品のテストで共有し、文字として出ること、リンクが無いこと、スクリプトが動かないことを確かめる（D37） | Code Generation |
| NFR1.42 | 参照先の行の値を、次のどこにも置かない（`project.md` の Forbidden、D34）。<br>・`data-testid`・DOM の `id`（テストの印は位置の番号 `c<n>`・`r<n>` だけ）<br>・`document.title`・道（URL）・履歴の状態<br>・ブラウザの保存（`localStorage`・`sessionStorage`・IndexedDB）と機能の中の定義の記憶（D2。選択肢・ラベルは画面の状態にだけ持ち、離れたら捨てる。ダイアログは開くたびに空の状態から始める。D21）<br>・`console` の出力<br>・E2E の `test.step` の題・注記・添付・標準出力・失敗の文 | 画面の部品のテストで、`data-testid`・`id`・`document.title`・道に見本の値が無いこと。コードの検索で `console` と保存の呼び出しを洗い出す。E2E の json の報告を見本の値で検索する | Code Generation |
| NFR1.43 | 参照元のスキーマ・テーブル・カラム、検索の値、ラベルを引く値は、すべて要求の本文で送り、道（URL）にも問い合わせにも載せない（機能設計の 2節） | API の呼び出しの単体テストで、道に値が無く本文に入ること | Code Generation |
| NFR1.44 | 画面の出し分けを、サーバーの判定の代わりにしない（`project.md` の Mandated）。部品は定義の応答（`reference.kind`・`status`、`editableOnCreate`・`editableOnUpdate`・`truncatedColumns`）だけで選び、画面で権限を判定しない（D5）。入力不可の参照の項目の値は作成・更新の本文に入れない（D15）。画面の確かめ（空の条件・1,000 文字・型・必須）は要求を減らすためのもので、サーバーの判定（U1・U2）を正とする（D23） | 画面の部品のテストで、入力不可の項目が本文に入らないこと、部品の選び方の表（D5）の全行。サーバーの判定は U1・U2 の結合テスト | Code Generation |
| NFR1.45 | 応答の本文は `unknown` で受け、形を確かめてから写す（D3・D18）。<br>・定義の `reference` の形の崩れ、`searchFields`・`listColumns` の `valueType` の欠けと語彙に無い値は、通信の失敗にする<br>・知らない `status`・`kind` は安全側に倒して入力不可（権限不足の説明文）にし、値を入れさせない<br>・ラベルの1件読みは、`rows` がちょうど1件で `value`（文字）・`label`（文字か `null`）の形が正しいときだけラベルを出し、それ以外は値だけにする | 写しの関数の単体テストで、正しい形・`null`・形の崩れ・知らない値の各場合 | Code Generation |
| NFR1.46 | 誤りの表示に、DB の文・内部の語・参照先の表とカラムの名前を出さない（D27）。検索の 403 は、行が無いことと読めないことを見分けない同じ文にし（D18・D27）、保存の 409 `REFERENCE_NOT_FOUND` は参照先に無い値と読めない値を区別しない同じ文にする（D31） | 403・409・503 の応答の見本で、決めた文だけが出ることの画面の部品のテスト | Code Generation |
| NFR1.47 | E2E（190・195）の資格情報（初期管理者・自作の利用者のパスワード、トークン、対象DB の読み取り・書き込みのアカウント）と接続先は、Playwright の `webServer.env` に置かずプロセスの環境変数で渡す。報告の部品（`frontend/playwright-secret-check-reporter.ts`。`frontend/playwright.config.ts` の `SECRET_ENV_NAMES`）が json の報告と `test-results/` を文字列で検索し、値が無いことを確かめる。自作の利用者のパスワードとトークンも、既存の E2E と同じ仕組み（報告に残さない値の一覧）に入れる。見本の行は架空の値だけにし、メールアドレスは予約のドメイン（`example.com`）だけにする（`team.md`、`project.md` の学び） | E2E の全件の実行で、報告の部品が通ること | Code Generation、Build and Test |

脅威の見立て: U3 が新しく見せる値は、サーバー（U1）が参照先の権限で絞って返す値だけである。画面で気をつけるのは、(1) 値を HTML として描くこと、(2) 参照先の行の値が印・道・保存・記録・E2E の報告へ漏れること、(3) 画面の出し分けを判定とみなし入力不可の値を送ること、(4) 誤りの文から参照先の存在や名前を推測させること、(5) E2E の資格情報が報告に残ること、の5つで、NFR1.41〜NFR1.47 で受ける。権限を欠く利用者に選択肢を返さないこと（`team.md` の (a)）、識別子の照合、件数・時間切れ、検索のエスケープ、保存前の確かめ、接続の数（(b)〜(e)・(g)〜(k)）はサーバーの受け持ちで U1・U2 が扱う。

## 2. アクセシビリティ（NFR4）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR4.41 | WCAG 2.1 AA を満たし、キーボードだけで、作成の画面 → 「選ぶ…」でピッカーを開く → 条件を入れる → Enter で検索 → ページを送る → 行の「選ぶ」 → ダイアログが閉じる → 保存、を行える。フォーカスの移り方は D21 のとおり（開いたら最初の条件、閉じたら「選ぶ…」、中に閉じ込め、Escape と「閉じる」で閉じ、背景のクリックでは閉じない）。「クリア」の後は欄へ移す（D19）。入力不可の項目は `readOnly`・`aria-readonly` で `disabled` にせず、Tab で止まって値を読める（D11） | `LookupPickerDialog`・`LookupField`・`ReadonlyReferenceField` の画面の部品のテスト（user-event のキーボード操作）と、E2E 190 のキーボードだけの流れ | Code Generation |
| NFR4.42 | 名前と状態の知らせ。<br>・ボタンの読み上げの名前は見える文字で始める（WCAG 2.5.3。「選ぶ…（{項目名}）」「クリア（{項目名}）」。行の「選ぶ」は「{行のラベル}を選ぶ」。D17・D28）<br>・「検索中」「条件を入れてください」・0 件・初期の案内は `role="status"`、検索の失敗と 403 は `role="alert"`、件数の変化は `aria-live="polite"`（D23〜D27）<br>・結果の表は名前つきの横のスクロールの枠（`role="region"`・`tabIndex=0`）と表の `aria-label`（D25）<br>・入力不可の理由（権限不足・候補が多い）は色やアイコンでなく文字で分け、`aria-describedby` で結ぶ（D11・D12）<br>・ページ送りは総ページと件数を言わず、今のページと続きの有無を言う（D25） | 画面の部品のテストで、名前・`role`・`aria-*` を確かめる。axe の `label-content-name-mismatch` を含む規則を外さずに流す | Code Generation |
| NFR4.43 | 画面の部品ごとに vitest-axe の検査を1件入れる（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`）。`LookupPickerDialog` は、初期・結果・0 件・失敗の4つの状態で各1件を足す（D37） | 画面の部品のテストで違反が 0 件 | Code Generation |
| NFR4.44 | 実際のブラウザの axe（E2E 195。流れではないため本数に数えない）。<br>・状態: ピッカーを開いた状態・結果の一覧・0 件・検索の失敗・入力不可（権限不足・候補が多い）・作成の必須の入力不可の誤り・保存の 409 の誤り・長い名前（100 文字以上、`<` を含む）・2語以上の現実に近いデータ<br>・組: ブランドカラーとテーマのすべての組（既存の `frontend/e2e/support/displayCombos.ts`）。幅は既定と 375px<br>・375px では、開いたダイアログの矩形が画面の中に収まり、画面全体が横にスクロールしないことを既存の `frontend/e2e/support/overflow.ts` で確かめる（`project.md` の学び: 固定の部品のはみ出し）<br>・失敗・入力不可の状態は API の答えを差し替えて作り、差し替えの見本を本物の応答（190 で取る）と形で比べる<br>・axe の規則と要素を外さない（`disableRules`・規則の絞り込み・`exclude` を使わない。`project.md` の Forbidden）。違反が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから直す | E2E 195 の全件の通過と違反 0 件を件数で記録する | Code Generation、Build and Test |
| NFR4.45 | 作成の画面を開いた直後の、必須の入力不可の参照の項目の誤り（D13。`FormField` の誤りの文は `role="alert"`）を読み上げさせるかは、NFR 設計で必ず決める（機能設計の 9節・13節）。決めた側の動作を、画面の部品のテスト（開いた直後の `role` の有無）と E2E 195 の axe で確かめる。行き止まり（必須の参照の項目に値を入れられない作成）そのものは変えず、D13 の文で管理者への依頼を促す（M1: A）。<br>・NFR 設計で読み上げの扱いを決めた後、NFR4.44 の axe の状態の一覧（特に「作成の必須の入力不可の誤り」の状態）が決めた側の動作と食い違わないかを確かめ直し、食い違えば状態の一覧をそろえる | NFR 設計の決めと、決めた側を明示した画面の部品のテスト | NFR 設計、Code Generation |

## 3. テスト（NFR5）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR5.41 | 画面のテストは対象と同じ場所の `*.test.ts(x)` に、Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe で書く。テストの説明文は英語、データは架空の値。描画の後の効果で出る値は `waitFor` で待ち、時間の上限を原因を確かめずに延ばさない（`team.md`）。テストの一覧は `frontend-components.md` の 7節のとおり。既存の一覧・詳細・作成・更新の画面のテストに `reference` を含む定義の見本を流しても通ること（AC3.4.3） | 全件の通過 | Code Generation |
| NFR5.42 | 性質ベースのテスト（fast-check）を純粋な関数に当てる（D37）。部品の選び方（`AVAILABLE` 以外で入力の部品を返さない、用途で入力できないカラムでは `status` によらず既存の読み取りの表示を返す）、検索の条件の組み立て（空の値が入らない、1,000・1,001 文字の境界、型として読めない一致の条件で送らない）、ページの表示（2ページ目以降の 0 件で `Table` を使わない、総ページが今のページより小さくならない）、表示の文（`<`・`"`・`'` を含む値も文字のまま）。失敗したときの乱数の種を記録する。フロントエンドのカバレッジの下限は今の形（全体の合計に当てる `thresholds`、行 80%・分岐 70%）のまま満たし、`features/tables` の値を Build and Test で記録する | 全件の通過とカバレッジの下限の通過 | Code Generation、Build and Test |
| NFR5.43 | E2E は参照ピッカーの代表の流れを1本だけ足す（`190-reference-picker-flow.e2e.ts`。F8）。`./gradlew e2eTest` で流し、`verify` と CI の外に置く。<br>・前提（利用者・ロール・割り当て・専用のスキーマの参照先と参照元の行）は流れの中で自分で作り、前のテストが作った状態に頼らない。初期管理者と見本の行（見本の対象DB の行）は変えない（D39）<br>・DSL は、E2E 180 と 190 が共有する見本（`frontend/e2e/support/` に置く1つの DSL）に参照の表（参照先の顧客・商品と参照元の注文）を足す。190 は画面の流れの前の API の準備で、その DSL が適用されていなければ投入・適用し、画面の流れの中では適用し直さない（D39）<br>・共有の見本に参照の表を足した後も、既存の E2E 180 の全件が通ることを確かめる。参照の表を足す作業と、それに伴う 180 の前提の合わせ直しは、`team.md` の「機能の Intent ごとに代表の流れを1本まで」の本数に数えない<br>・見本の対象DB（PostgreSQL）が動いていることを前提にし、届かなければ既存の仕組みで起動の手順を示して失敗する<br>・権限を欠く利用者の確かめは E2E でせず、U1・U2 の結合テストと画面の部品のテスト（入力不可・値だけの表示・理由の表示を1件）で行う（AC2.5.4）<br>・axe の E2E 195 と既存の E2E の書き換えは本数に数えない<br>・統合の前に E2E を流すかは `team.md` の Way of Working のとおり（frontend の本体に触れるため流す） | E2E の全件の通過を件数で記録する | Code Generation、Build and Test |

## 4. 多言語（NFR6）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.41 | 画面の文言と誤りのメッセージを日本語と英語の両方で `features/tables` の文言（`messages.ts`）に置き、既定は日本語（D36）。前提の6つ（権限なし・件数が多い・0 件・検索中・検索の失敗・クリア）を必ず両方に置く。鍵の一覧は `frontend-components.md` の 5節。行の値を文言の鍵に使わない | 日本語と英語の鍵の集合が一致することのテスト。言語を英語に切り替えて、ダイアログと誤りの文が英語で出る画面の部品のテスト（AC2.4.6） | Code Generation |
| NFR6.42 | 画面は誤りの `code` を新しく作らず、U1・U2 が決めた `code` と `reason`（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・`REQUIRED` など）と状態コードから文を選ぶ（D27・D31〜D33）。知らない `code` は既存の汎用の文にする。既存の鍵 `tables.error.REFERENCE_NOT_FOUND` の文は差し替えるが、鍵の名前は変えない | 誤りの文の選び方の単体テスト（知らない `code` を含む） | Code Generation |

## 5. 互換性（NFR7）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR7.41 | 定義の応答に足す欄（`reference`）を読み落としても壊れないこと。<br>・一覧・詳細の画面は `purpose` を付けず、`reference` を読まず、表示を変えない（D1・7節）<br>・`reference` が `null` のカラムは既存の入力の部品で出す（D5）<br>・知らない `status`・`kind` は入力不可に倒す（NFR1.45）<br>・保存の誤りの分け方（`writeOutcome.ts`）は変えない（D33） | `reference` を含む定義の見本を既存の一覧・詳細・作成・更新の画面のテストに流して通ること（AC3.4.3）。知らない値の写しの単体テスト | Code Generation |

## 6. 供給網（NFR9）

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR9.41 | 新しい依存を足さない。今の React・i18next・make-you-chic-ui（固定先 `4c63d11`）・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・axe-core で作る（`tech-stack-decisions.md`）。足すことになったときは、採用の前にライセンスを確かめ、Apache License 2.0 と異なるなら理由を記録に残す。`frontend/.npmrc` の `ignore-scripts=true` と lockfile による版の固定を保つ。make-you-chic-ui への新しい依頼は出さない（機能設計の 11節） | `frontend/package.json` と `frontend/package-lock.json` の差に新しいパッケージが無いことを、コード生成の記録で確かめる | Code Generation |

## Sources

- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/nfr-requirements-questions.md`（Q1: A と「決まっていること」）
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR4〜NFR7・NFR9）
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D1〜D39、7節・9節・11節・13節）、`frontend-components.md`（5節・7節・8節）
- 画面イメージ: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/accessibility-checklist.md`
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/security-requirements.md`
- コード: `frontend/playwright.config.ts`（`SECRET_ENV_NAMES`・`webServer`）、`frontend/package.json`、`frontend/e2e/support/`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture の他テーブル参照の (a)〜(k) と参照ピッカーの E2E、Code Style のフロントエンド）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated、学び: `webServer.env`・axe の組・固定の部品のはみ出し）

## Assumptions & Open Questions

- [assumption] 自作の利用者のパスワードとトークンは、既存の E2E が報告に残さない値として扱っている仕組み（`secretValuesFile`）にそのまま入れられる。Code Generation で確かめる。
- [assumption] E2E のファイルの番号 190・195 は機能設計の仮の番号のまま使う。
- NFR4.45 の読み上げの扱いは NFR 設計で決める（未決）。
- 確かめていないため、NFR1.41〜NFR9.41 はすべて Code Generation と Build and Test で確かめるまで `Unverified` とする。
