# Tech Stack Decisions — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

この文書は、U7 の技術の選定と、アクセシビリティ・多言語・テストと依存の要件をまとめたものです。技術は前の Intent と U4 から変えず、新しい依存は足しません（React・TypeScript・Vite・i18next・make-you-chic-ui・Vitest・Testing Library・vitest-axe・fast-check・Playwright、U4 が B4 で明示で足す axe-core。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md`）。答えは `nfr-requirements-questions.md` にあります（Q1: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md` の ID です。
- D・W・7節〜10節は、この単位の `construction/u7-preferences-ui/functional-design/functional-spec.md` の節です。
- 「部品 7節・8節」は、同じフォルダの `frontend-components.md` の節です。
- Q1 と「要点 n」は、この段の `nfr-requirements-questions.md` の番号です。
- 「共通の決定」は、U5〜U7 の画面に共通する依頼者の決定（`construction/u5-invitation-ui/nfr-requirements/` の段で出たもの）です。

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 置き場 | `frontend/src/features/preferences/` を新しく置く。骨組みの `frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`frontend/src/app/layout/ShellLayout.tsx` を広げる | 機能設計の2節・9節 |
| 画面の部品 | make-you-chic-ui の Button（`loading`）・Alert・FormField・TextInput・Toast・RadioGroup（`legend`・選択肢の `lang`）・Dropdown（`MenuItem` の `href`）。どれも固定先の更新（`edb1f94` → `735ef04`、U4 の B4）の後の版を使う。make-you-chic-ui は変更しない | 部品 8節、機能設計の 10節の (b)・(g)、`project.md` の Forbidden |
| 表示の設定 | U4 の口（契約 C9 の `useDisplaySettings`・`setPreview`・`clearPreview`・`applyUserPreferences`）だけを使い、make-you-chic-ui の `useTheme` とブラウザの保存に直接触れない | 機能設計の2節、NFR2.1 |
| API | 既存の ApiClient（`frontend/src/shared/api-client/`）を通す。独自の時間切れ・再送は足さない | 機能設計の2節、NFR6.5 |
| 入力の確かめ | U6 が作る共用の純粋な関数（`frontend/src/shared/validation/`）を使い、画面に同じ決まりを別に書かない | D7、機能設計の 10節の (d)・(d2) |
| 文言 | 既存の i18next・react-i18next | NFR8.1 |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | `team.md` の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.6 |
| 実際のブラウザのアクセシビリティの検査と画面の時間の測り | Playwright（既存）＋ axe-core（U4 が B4 で devDependencies に明示で足す、U4 の NFR9.6） | NFR7.3・NFR7.4、NFR6.1〜NFR6.3 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | NFR6.6 |

## 2. アクセシビリティ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする（U4 の NFR7.1 のまま）。文字のコントラスト 4.5:1、部品 3:1、キーボードで操作できる、読み上げで名前が分かる、色だけに頼らない。テーマ（`light`・`dark`）・文字の大きさ（`sm`・`md`・`lg`）・ブランドカラー（4つ）のどの組み合わせでも満たす | NFR7.2〜NFR7.4 | 要点 9、要件 NFR7、U4 の NFR7.1 |
| NFR7.2 | 画面部品（`PreferencesPage`・`PasswordChangePage`）ごとに、vitest-axe の検査を1件入れ、違反 0 件とする | 画面部品のテスト（部品 7節の「アクセシビリティ」） | 要点 9、`team.md` の Testing Posture |
| NFR7.3 | U4 の実際のブラウザのアクセシビリティの検査（U4 の NFR7.3、Playwright＋axe-core、`./gradlew e2eTest` の中、`./gradlew verify` と CI の外）に、プリファレンスとパスワードの変更の2つの画面を B5 で足す。画面ごとに (a) テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`）、(b) ブランドカラー4×テーマ2の8組（文字の大きさは `md`）を切り替え、コントラストを含む axe の違反 0 件と、既定の表示の幅（`Desktop Chrome`）で横のはみ出しが無いことを確かめる。ログインの後の画面のため、検査は利用者などの前提を自分で作る | Build and Test で `./gradlew e2eTest` を実行し、画面ごと・組ごとの結果（違反の件数・成否）を記録する。画面・認証に関わる変更を統合する前と、リリースの前にも手元で実行する（`team.md` の Testing Posture） | 要点 9、U4 の NFR7.3・NFR7.5・NFR9.11 |
| NFR7.4 | 狭い幅: NFR7.3 の検査に、表示の幅 375px を1つ足す。2つの画面ごとに、テーマ2×文字の大きさ3の6組で、横にはみ出さないこと（文書の横の大きさが表示の幅を超えない）と axe の違反 0 件を確かめる。画面は `interaction-spec.md` のとおり 768px 未満で1列に積み、ラジオの選択肢は折り返す | NFR7.3 と同じく Build and Test で記録する。B5 で U5・U6 の画面と同じ検査に入れる | 要点 10、共通の決定、`inception/refined-mockups/interaction-spec.md` |
| NFR7.5 | 言語・テーマ・文字の大きさの選択は、まとまりごとに `fieldset`・`legend`（make-you-chic-ui の RadioGroup に `legend` を渡す）で作り、言語の選択肢には `lang` を付ける。項目の誤りは `aria-describedby`・`aria-invalid` で結び付け（選択のまとまりの誤りは `legend` の中の文字）、最初の誤りの項目へフォーカスを移す。成功は Toast（`aria-live="polite"`）、失敗は `role="alert"` で知らせる | 画面部品のテスト（部品 7節の「アクセシビリティ」「画面の確かめ」「サーバーの誤り」） | 要点 9、D9・D11・D14、CR6.1・CR6.4・CR6.6 |

## 3. 多言語

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR8.1 | U7 が足す文言（`preferences.` で始まる鍵、機能設計の7節）は、ja・en の両方を用意し、鍵をそろえる。言語の選択肢の名前は U4 の訳さない固定の値（U4 の NFR8.2、「日本語」「English」）を使う | 画面部品のテスト（部品 7節の `registration`）で、足した鍵が ja・en の両方にあり、空でなく、`preferences.` で始まることを見る | 要点 11、要件 NFR8、W13 |
| NFR8.2 | 画面の確かめの理由とサーバーの項目ごとの誤りの理由、項目の名前のすべての組が、ja・en の両方に文言を持つ鍵になる。知らない理由は項目ごとの一般の文言にする | `errorMessages.ts` のテスト（部品 7節） | 要点 11、W11・W12、D13 |

## 4. テストと依存

テストの置き場と名前は既存の決まりどおりです。対象と同じ場所に `*.test.ts(x)` を置き、説明文は英語で書きます。個々のテストの一覧は部品 7節を正とします。

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.5 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を守る。U7 のために計測の除外を増やさない | `./gradlew verify` の中のフロントエンドのカバレッジの検証で、値を記録する | 要点 12、要件 NFR9、`team.md` の Testing Posture |
| NFR9.6 | 性質ベースのテスト（fast-check）を、項目ごとの誤りの読み取り（`fieldErrors.ts`）に当てる。どんな JSON の値を渡しても例外を出さず、知っている項目の名前だけを返す。失敗時の乱数の種を記録する。共用の確かめの関数の境界と性質ベースのテストは U6 が持つ | 単体テスト（fast-check） | 要点 12、W12、機能設計の 8節 |
| NFR9.7 | `team.md` のパスワードの変更の必須テストのうち、今のパスワードの確かめ・変更の後のリフレッシュトークンの扱いのサーバーの判定は U2 が持つ。U7 は、画面の確かめの境界（新しいパスワードの 11・12 コードポイント、72・73 バイト、絵文字 11・12 文字、確かめの不一致、今のパスワードに規則を当てないこと）と、今のパスワードの誤りの表示（NFR9.3）を、共用の関数を差し替えずに画面のテストで確かめる | 画面部品のテスト（部品 7節の `PasswordChangePage` の「画面の確かめ」「今のパスワードの誤り」） | 要点 12、`team.md` の Testing Posture、W11、AC5.1.3 |
| NFR9.8 | 既存の画面のテスト（`renderWithProviders` を使うものを含む）、骨組みのテスト（`validateRegistrations`・`ShellLayout`・`navigationItems`）、既存の E2E（`frontend/e2e/`）が通り続ける。U7 は流れの E2E を足さない（この Intent の代表の流れは招待から登録の完了までの1本で U6 が持つ）。画面・認証に関わる変更のため、統合の前に既存の E2E を手元で流す | `./gradlew verify` と `./gradlew e2eTest` | 要点 8・12、機能設計の 8節、`team.md` の Testing Posture |
| NFR9.9 | NFR7.3・NFR7.4 の検査と、NFR6.1〜NFR6.3 の画面の時間の測りは、画面からの一連の操作の流れではない。そのため `team.md` の「機能の Intent ごとに代表の流れを1本まで足す」の本数に数えない。前のテストが作った状態に頼らず、利用者などの前提を自分で作る | B5 のコード生成の計画で、検査と測りのファイルが流れの E2E と分かれていることを確かめる | U4 の NFR9.11、共通の決定 |
| NFR9.10 | 新しい依存を足さない。make-you-chic-ui の新しい版（`735ef04`）の機能（RadioGroup の `legend`・選択肢の `lang`、Dropdown の `MenuItem` の `href`、Button の `loading` のフォーカスの保持）は、U4 の B4 の固定先の更新の後に使う。U7 は U6 の共用の確かめの関数に頼るため、B5 のコード生成では U6 の関数を U7 より先に作る | コード生成で、`frontend/package.json` と lockfile の依存が U7 で増えていないこと、固定先が `735ef04` であることを確かめる | 部品 8節、機能設計の 10節の (b)・(d2)、U4 の NFR9.7 |

## 5. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| U4 の実際のブラウザの検査に表示の幅 375px を足す（共通の決定） | U4 の NFR 要件（`construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md` の NFR7.3 と 6節） | 横のはみ出しの確かめは既定の表示の幅だけ。狭い幅を検査に足すかは B5 の計画で依頼者に確かめる | U5 の段の依頼者の決定で、375px・6組を足すことが決まった。U7 の2つの画面に当てる（NFR7.4）。U4 の文書は書き換えず、B5 のコード生成の計画で U4 の検査に足す |
| 画面の時間の測りを E2E の本数に数えない | `team.md` の Testing Posture | 「機能の Intent ごとに代表の流れを1本まで足す」 | U4 の NFR9.11 と同じ読み方で、流れではない測りとして数えない（NFR9.9）。`team.md` は書き換えない |
| U7 が骨組み（部品 AppFrame、持ち主は U4）を変える | `inception/units-generation/unit-of-work.md` の U7 の境界、U4 の機能設計 | 骨組みと表示の設定を当てる仕組みは U4 | 機能設計の Q1 A と 10節の (a) のとおり。この段では、既存の骨組みのテストと E2E が通り続けること（NFR9.8）と、外の URL へ移る道を作らないこと（`security-requirements.md` の NFR9.4）を要件にした |
| U7 が U6 の共用の確かめの関数に頼る | `inception/units-generation/unit-of-work-dependency.md`（U7 は U2・U4 に依存） | U6 への依存が無い | 機能設計の 10節の (d2) のとおり。同じ Bolt B5 の中で U6 の関数を先に作ることを要件にした（NFR9.10） |
| 実際のブラウザの検査を U7 の画面に当てる | 要件の NFR7 | 画面部品ごとにアクセシビリティの検査を1件 | vitest-axe の検査はそのまま（NFR7.2）、実際のブラウザの検査（NFR7.3・NFR7.4）は追加。食い違いではない |
