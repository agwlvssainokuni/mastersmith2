# Tech Stack Decisions — U5 招待の管理の画面（u5-invitation-ui）

この文書は、U5 の技術の選定と、依存・アクセシビリティ・多言語・テストの要件をまとめたものです。技術は前の Intent と U4 から変えません（React・TypeScript・Vite・i18next・make-you-chic-ui・Vitest・Testing Library・vitest-axe・fast-check・Playwright・axe-core。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、U4 の `tech-stack-decisions.md`）。U5 は新しい依存を足しません。答えは `nfr-requirements-questions.md` にあります（Q1: B、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md` の ID です。
- D・W・7節〜9節は、この単位の `construction/u5-invitation-ui/functional-design/functional-spec.md` の節です。
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節です。
- Q と「要点 n」は、この段の `nfr-requirements-questions.md` の番号です。
- U4 の NFRx.y は `construction/u4-display-foundation/nfr-requirements/` の ID です。

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 画面の置き場 | `frontend/src/features/invitation/` を新しく置く。日時の書式 `formatDateTime` は `frontend/src/features/dsl/format.ts` から `frontend/src/shared/format/` へ移す | 機能設計の 2節・9節の (d)（Q1 A） |
| 表・Modal・ボタン・知らせ | make-you-chic-ui の Table（`labels` で ja・en）・Modal（`closeOnBackdropClick`・`role="alertdialog"`・`closeLabel`）・Button（`loading`）・Alert・Badge・RadioGroup・`useToast`。素の `table` と自前のページ送りは作らない | 機能設計の W1 の 5・D9・D12・9節の (j)（B4 の固定先の更新の後の版） |
| 表示の設定 | U4 の口 `useDisplaySettings()`（契約 C9）の `language` と `LANGUAGE_NAMES` | 機能設計の 2節 |
| API | 既存の ApiClient（`frontend/src/shared/api-client/`）を通す。独自の時間切れを置かない | 機能設計の 2節、`performance-requirements.md` の NFR6.5 |
| 文言 | 既存の i18next・react-i18next（機能の登録の文言、鍵は `invitation.`） | NFR8.1 |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | `team.md` の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.7 |
| 実際のブラウザのアクセシビリティの検査 | Playwright（既存）＋ axe-core（U4 の NFR9.6 で B4 に devDependencies へ明示で足すもの） | U4 の NFR7.3、NFR7.3・NFR7.4 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | `performance-requirements.md` の NFR6.6 |

## 2. 依存と make-you-chic-ui

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.4 | U5 は新しい依存（実行時・開発時とも）を足さない。`frontend/package.json` と lockfile の差分は、B4 で入る make-you-chic-ui の版と axe-core（U4 の NFR9.6）の外に U5 の分を持たない | コード生成で、U5 の変更による `frontend/package.json`・`frontend/package-lock.json` の差分が無いことを確かめて記録する | 要点 4・8、`team.md` の Code Style（新しい依存のライセンス） |
| NFR9.5 | make-you-chic-ui の新しい Table・Modal・Button は、B4 の固定先の更新（edb1f94 → 735ef04）の後の版を使う。固定先の更新は承認を得た専用のコミットで、前後のコミットのハッシュを記録する（B4 の受け持ち）。U5 は `vendor/make-you-chic-ui` の中身を変えない。make-you-chic-ui に無い機能（表の名前の `caption`・行ごとの class・横に動く領域の名前）は、Table の外側（見える見出し・`aria-label`・機能の CSS）で足す | コード生成（B5）の計画で、B4 の固定先の更新が済んでいることを前提として確かめる。`vendor/make-you-chic-ui` に差分が無いことを確かめる | 要点 8、機能設計の 9節の (j)、`project.md` の Forbidden・Mandated |

## 3. アクセシビリティ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする（U4 の NFR7.1 のまま）。U5 の画面では次を守る。送信の結果（「送信済み」「送信に失敗」）と有効期限の状態（「期限内」「期限切れ」）を文字で示し、色と記号は補助にする。行の「送り直す」「取り消す」の読み上げの名前にその行のメールアドレスを含める。言語の名前に `lang` 属性を付ける。取り消しの確かめは `role="alertdialog"` で、はじめのフォーカスを「やめる」に置く。Modal は背景のクリックで閉じず、閉じたら開く前のボタンへフォーカスを戻す。目立たせた行は色だけでなく枠の線でも示す。送信の間はフォーカスを押したボタンに保つ | 画面部品のテスト（部品 7節） | 要点 9、D5・D9・D12・D13・W1・W7、CR6.5〜CR6.7 |
| NFR7.2 | U5 の画面部品（`InvitationList`・`InvitationUnavailableAlert`・`InviteDialog`・`CancelConfirmDialog`・`InvitationAdminPage`）ごとに、vitest-axe の検査を1件入れ、違反 0 件とする | 画面部品のテスト（部品 7節） | 要点 9、`team.md` の Testing Posture |
| NFR7.3 | U4 の NFR7.3 の実際のブラウザの検査（Playwright＋axe-core、`./gradlew e2eTest` の中、`./gradlew verify` と CI の外）に、B5 で U5 の画面を足す（U4 の NFR7.5）。対象は、一覧の画面（行ありの状態）・招待を使えないときの警告の表示・招待の入力の Modal・取り消しの確かめの Modal。既定の幅（Playwright の `Desktop Chrome`）で、(a) テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`）と、(b) ブランドカラー4×テーマ2の8組（文字の大きさは `md`）を切り替え、コントラストを含む違反 0 件と、文書の横の大きさが表示の幅を超えないことを確かめる。行を置くための招待と、警告の表示に要る招待を使えない状態は、検査の中で自分で用意する（前のテストの状態に頼らない、U4 の NFR9.11） | Build and Test で `./gradlew e2eTest` を実行し、画面・組ごとの結果（違反の件数・成否）を記録する。画面・認証に関わる変更を統合する前と、リリースの前にも手元で実行する（`team.md` の Testing Posture） | 要点 10、U4 の NFR7.3・NFR7.5、要件 NFR7 |
| NFR7.4 | NFR7.3 の検査に狭い幅を1つ足す。表示の幅 375px（高さ 812px）で、テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`）を切り替え、文書の横の大きさが表示の幅を超えないこと（一覧の表は Table の包む要素の中だけで横に動く）と、axe-core の違反 0 件（横に動く領域にキーボードで届くことを見る `scrollable-region-focusable` を含む）を確かめる。対象は一覧の画面（行ありの状態）・招待の入力の Modal・取り消しの確かめの Modal。ブランドカラーの8組は幅に関わらないため、既定の幅だけのまま。B5 で U6・U7 の画面にも同じく当てる。新しい依存は足さない | NFR7.3 と同じ。狭い幅の組の結果も画面ごとに記録する | Q1 B、要点 10、`inception/refined-mockups/interaction-spec.md`（狭い幅（768px 未満）でも崩れずに読めて操作できる） |

## 4. 多言語

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR8.1 | U5 の文言（機能設計の 7節の `invitation.*`）は ja・en の両方を持ち、既定は日本語。言語の名前は U4 の `LANGUAGE_NAMES`（訳さない）を使う。ja と en の文言の鍵がそろっている | 画面部品のテストで、文言の鍵の一覧が ja と en で一致することを見る。en の画面で en の文言が出ることを見る（部品 7節の `InvitationAdminPage`） | 要点 11、7節、要件 NFR8、CR1.1・CR1.4 |
| NFR8.2 | make-you-chic-ui の Table の `labels` の7項目と Modal の `closeLabel` を画面の言語で渡し、en の画面に make-you-chic-ui の日本語の既定の文言を出さない | 画面部品のテスト（部品 7節の `InvitationList`・`InviteDialog`・`CancelConfirmDialog`）で、en の画面に Table と Modal の日本語の既定の文言が無いことを見る | 要点 11、7節 |

## 5. テスト

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.6 | フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守る。U5 のために計測の除外を増やさない | `./gradlew verify` の中の `@vitest/coverage-v8` の `thresholds` | 要点 12、要件 NFR9、`team.md` の Testing Posture |
| NFR9.7 | 性質ベースのテスト（fast-check、失敗時の乱数の種を記録）を、件数の範囲・ページの数・ページの補正の純粋な関数（`paging.ts`）に当てる。任意の全件数と有効なページで、件数の範囲の始まり ≦ 終わり ≦ 全件数、1ページの件数が 20 以下、補正の結果が 1 以上ページの数以下になることを見る | 画面部品のテスト（部品 7節の `paging.ts`） | 要点 12、機能設計の 8節、`team.md` の Testing Posture |
| NFR9.8 | `formatDateTime` を `frontend/src/shared/format/` へ移した後も、移した先で同じテスト（ja・en・不正な値、時差を固定）が通り、DSL の画面のテスト（`DslStatusPanel`・`DslConfirmDialog`・`DslHistoryTable`）がそのまま通る | `./gradlew verify` | 要点 12、機能設計の 8節・9節の (d) |
| NFR9.9 | U5 では流れの E2E を足さない（招待から登録の完了までの E2E-1 は U6 が持つ、`team.md` の「機能の Intent ごとに代表の流れを1本まで」）。NFR7.3・NFR7.4 の検査と NFR6.1・NFR6.2 の測定は流れではないため、この本数に数えない（U4 の NFR9.11）。E2E-1 は U5 の画面を通るため、U5 の変更の後に E2E-1 と既存の E2E（`frontend/e2e/` の 010〜040）が通ることを確かめる | B5 で `./gradlew e2eTest` を実行して記録する | 要点 12、機能設計の 8節、U6 の機能設計の W13、`team.md` の Testing Posture |

## 6. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 実際のブラウザの検査に狭い幅（375px）の6組を足す（Q1: B） | U4 の `tech-stack-decisions.md` の NFR7.3 と 6節の上流との差 | 横のはみ出しの確かめは既定の幅（`Desktop Chrome`）だけ。狭い幅を検査に足すかは B5 の計画で依頼者に確かめる | この段で依頼者が決めた（NFR7.4）。U4 の NFR7.3 への追加で、U4 の文書は書き換えない。B5 で U5 の画面から当て、U6・U7 の画面にも同じく当てる（依頼者の決定） |
| 狭い幅の自動の確かめ | `inception/refined-mockups/interaction-spec.md` | 狭い幅（768px 未満）でも崩れずに読めて操作できる（確かめ方の決まりは無い） | 375px の1つの幅で確かめる形にした（NFR7.4）。768px 未満の幅のすべてを確かめるわけではない |
| U5 の画面を実際のブラウザの検査に足す | U4 の NFR7.5 | U5〜U7 の新しい画面は B5 で同じ検査に足す | 対象の状態（行あり・警告・2つの Modal）を決めた（NFR7.3）。U4 のとおりで、食い違いではない |
| 画面の側の依存と make-you-chic-ui の要件を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 依存に当たる ID が無い | NFR9.4・NFR9.5 に寄せた（U4 の前例と同じ）。traceability.json の NFR9 の target に書く |
