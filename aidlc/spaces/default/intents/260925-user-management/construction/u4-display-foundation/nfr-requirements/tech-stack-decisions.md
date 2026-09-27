# Tech Stack Decisions — U4 表示の設定の土台（u4-display-foundation）

この文書は、U4 の技術の選定と、依存・アクセシビリティ・多言語・テストの要件をまとめたものです。技術は前の Intent から変えません（React・TypeScript・Vite・i18next・make-you-chic-ui・Vitest・Testing Library・vitest-axe・fast-check・Playwright。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）。答えは `nfr-requirements-questions.md` にあります（Q1: B、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md` の ID です。
- D・W・8節・9節は、この単位の `construction/u4-display-foundation/functional-design/functional-spec.md` の節です。
- 「部品 7節」は、同じフォルダの `frontend-components.md` の 7節です。
- Q と「要点 n」は、この段の `nfr-requirements-questions.md` の番号です。

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 表示の設定の置き場 | `frontend/src/app/display-settings/` と `frontend/src/app/login-handoff/` を新しく置く。既存の `frontend/src/app/`・`frontend/src/shared/api-client/`・`frontend/src/features/auth/` は広げる | 機能設計の2節 |
| 表示の設定の当て方 | make-you-chic-ui の ThemeProvider と `useTheme` の `setTheme`・`setFontSize`・`setBrand`・`setFontFamily` を使う。機能の側（`features/*`）は U4 の口（契約 C9）だけを使う | 機能設計の2節・D4・W3 |
| 文言 | 既存の i18next・react-i18next | NFR8.1 |
| ブラウザの保存 | localStorage に1つの鍵（JSON）を置く。例外は外へ出さない | 4.1・D7、NFR2.2 |
| 明朝体のフォント | `@fontsource/noto-serif-jp` を自前で配信する（NFR9.5） | 9.2（Q4 A） |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | `team.md` の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.9 |
| 実際のブラウザのアクセシビリティの検査 | Playwright（既存）＋ axe-core（devDependencies に明示で足す、NFR9.6） | Q2 B、NFR7.3 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | NFR6.3 |

## 2. 依存と make-you-chic-ui

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.5 | `@fontsource/noto-serif-jp` を実行時の依存（`dependencies`）に足す。版は 5.3.0（npm の公開の最新、2026-09-27 に確かめた）で、ライセンスは OFL-1.1、推移依存は無い。既存の `@fontsource/noto-sans-jp`（5.3.0、OFL-1.1）と同じ配布元・同じ版の系列。版は lockfile（`frontend/package-lock.json`）で固定し、CI では lockfile どおりに入れる（`npm ci`）。実行時の依存のため、依存関係の脆弱性検査で重大度 High 以上なら統合を止める対象に入る | コード生成で次を記録する: 足した版、lockfile の `resolved`・`integrity`、推移依存が無いこと。`./gradlew verify` の依存関係の脆弱性検査（OSV-Scanner）が通ることを確かめる | 要点 10、9.2、`team.md` の Deployment、`project.md` の Mandated |
| NFR9.6 | `axe-core` を開発時の依存（`devDependencies`）に明示で足す。版は 4.13.0（今の lockfile の vitest-axe の推移依存と同じ版で、npm の公開の最新でもある。2026-09-27 に確かめた）。ライセンスは MPL-2.0 で、推移依存は無い。採用の理由は 2.1 の記録のとおり。Playwright の検査（NFR7.3）が、この明示の依存から axe-core を読む。開発時の依存のため、依存関係の脆弱性は警告の扱い。悪意のあるパッケージ（OSV の `MAL-`）だけは止める（`team.md` の Deployment）。成果物を作る道具ではないため `config/npm-build-tools.txt` には足さない | コード生成で次を記録する: 足した版と、vitest-axe が求める範囲（`^4.4.2`）の中で lockfile の axe-core が1つの版にそろうこと。axe-core が画面の成果物（`dist`）に入らないこと | Q2 B、`team.md` の Code Style（ライセンス）・Deployment |
| NFR9.7 | make-you-chic-ui の固定先の更新（`edb1f943c0e66293494fa974605f34fcd7e258d7` → `origin/main` の `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、8 コミット）は、B4 で承認を得た専用のコミットで行う。更新の前後のコミットのハッシュを記録する。更新した版は、機能設計で確かめた U4 が使う API（ThemeProvider・`useTheme`・`design-system-*` の鍵・Alert・Button）を変えていないことを前提とする。make-you-chic-ui の中身は変えない。`react`・`react-dom` の `peerDependencies`（`^19.0.0`）は前後で同じ。更新した版の lockfile も、依存関係の脆弱性検査の対象に入る。統合は、固定先の更新の専用のコミットを残すため、短命のブランチから fast-forward でよい | コード生成で次を記録する: 前後のハッシュ。同梱版でフロントエンドのビルドと既存のテストが通ること。依存関係の脆弱性検査に make-you-chic-ui の lockfile が入っていること | 要点 10、機能設計の `frontend-components.md` の4節、`project.md` の Forbidden・Mandated、`team.md` の Way of Working・Code Style |

### 2.1 axe-core の採用の記録（ADR 形式。`team.md` の「Apache License 2.0 と異なるライセンスは採用の理由を設計の記録に残す」による）

- **Context**:
  - 要件 NFR7 と `interaction-spec.md` は、テーマ・文字の大きさのどの組み合わせでも崩れないことと、WCAG AA のコントラストを求めている。
  - 今の vitest-axe は jsdom で動くため、配置とコントラストを評価できない。
  - Q2 B で、実際のブラウザで axe-core を流す検査を足すことになった。
  - axe-core は、今も vitest-axe の推移依存として `frontend/node_modules` に入っている（4.13.0）。ただし、採用の理由の記録はどの文書にも無い。
  - ライセンスは Mozilla Public License 2.0（MPL-2.0）で、このリポジトリの Apache License 2.0 と異なる。
- **Decision**: axe-core 4.13.0 を開発時の依存として明示で足し、Playwright の検査の中でテストのページに読み込んで使う。Playwright 用の包み（`@axe-core/playwright`）は足さない。MPL-2.0 を採用してよいと判断した理由は次の4つ。
  1. テストの道具として使うだけで、画面の成果物（`dist`・WAR）に入らず、配布しない。
  2. axe-core のファイルを改変しない。MPL-2.0 の条件（改変したファイルの公開）はファイル単位で、改変しなければ触れない。
  3. MPL-2.0 は、使う側のコード（Apache License 2.0）に及ばない。
  4. すでに vitest-axe の推移依存として同じ版が入っており、扱いは増えない（明示にすることで、推移依存に頼らず版を固定できる）。
- **Consequences**:
  - 良い点: 実際のブラウザで、コントラストを含む WCAG の自動の検査ができる。新しい配布の条件は増えない。
  - 悪い点: devDependencies が1つ増える。Playwright の検査は `verify` と CI の外のため、実行を忘れると確かめられない。画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する決まりで補う（`team.md` の Testing Posture）。
  - 自動の検査は WCAG の一部しか見ない。見ないもの（読み上げの順序の自然さなど）は、画面部品のテストと画面の段の決まりで補う。
- **Alternatives Rejected**:
  - `@axe-core/playwright`（MPL-2.0）を足す: 読み込みの手間は減るが、依存が2つ増え、axe-core の版が包みに引きずられる。
  - 目で見る点検表で確かめる（Q2 A）: 依存は増えないが、確かめる人によって結果が揺れ、U5〜U7 の画面に同じく当てるたびに手間がかかる。
  - vitest-axe だけにする（Q2 C）: コントラストと崩れを確かめられず、NFR7 を満たしたと言えない。

### 2.2 Noto Serif JP・Noto Sans JP の採用の記録

機能設計の 9.2（Noto Serif JP）・9.3（Noto Sans JP）の ADR 形式の記録を正とし、ここでは繰り返しません。この段で足すのは、版（5.3.0）と推移依存が無いことの確認だけです（NFR9.5）。

## 3. アクセシビリティ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする。文字のコントラスト 4.5:1、部品 3:1、キーボードで操作できる、読み上げで名前が分かる、色だけに頼らない。テーマ（`light`・`dark`）・文字の大きさ（`sm`・`md`・`lg`）・ブランドカラー（4つ）のどの組み合わせでも満たす | NFR7.2・NFR7.3 | 要点 11、要件 NFR7、`inception/refined-mockups/interaction-spec.md` |
| NFR7.2 | U4 の画面部品（`LoginLanguageSwitch`・登録の完了の案内を出す `LoginForm`・氏名を出す `ShellLayout`）ごとに、vitest-axe の検査を1件入れ、違反 0 件とする | 画面部品のテスト（部品 7節） | 要点 11、`team.md` の Testing Posture |
| NFR7.3 | 実際のブラウザのアクセシビリティの検査を、Playwright の検査として1つ足す（`./gradlew e2eTest` の中、`./gradlew verify` と CI の外）。画面ごとに、次の組を切り替えて axe-core を流し、コントラストを含む違反 0 件を確かめる。(a) テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`）。(b) ブランドカラー4×テーマ2の8組（文字の大きさは `md`）。崩れは、各組で画面の幅を超える横のはみ出しが無いこと（文書の横の大きさが表示の幅を超えない）で確かめる。表示の幅は既存の設定（Playwright の `Desktop Chrome`）のまま。組の切り替え方（U4 の鍵を読み込みの前に置く、`<html>` の `data-brand` を書き換える など）は、コード生成で決める。アプリの CSP は変えず（NFR9.4）、axe-core の読み込みに必要な扱いは、この検査のブラウザのコンテキストだけで行う | Build and Test で `./gradlew e2eTest` を実行し、結果（違反の件数・組ごとの成否）を記録する。画面・認証に関わる変更を統合する前と、リリースの前にも手元で実行する（`team.md` の Testing Posture） | Q2 B、要件 NFR7 |
| NFR7.4 | 言語の選択肢は、それぞれの言語の名前（「日本語」「English」）に `lang` 属性を付けて示す。切り替えた後もフォーカスを選んだボタンに残す。画面の言語が変わったら `<html lang>` も同じ時点で変える。動きは `prefers-reduced-motion` のときに止める（make-you-chic-ui の扱いのまま） | 画面部品のテスト（部品 7節の `LoginLanguageSwitch`・`DisplaySettingsProvider`） | 要点 11、D12・D14・W7、CR1.3・CR6.6 |
| NFR7.5 | NFR7.3 の検査は、U4 の B4 ではログインの画面から始める。U5〜U7 の新しい画面（招待・招待の一覧・登録の完了・プリファレンス・パスワードの変更）は、B5 で同じ検査に足す。後の単位は、この確かめ方をそのまま当てる | B4・B5 のコード生成の計画に載せ、Build and Test で画面ごとの結果を記録する | Q2 B、要点 11 |

## 4. 多言語

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR8.1 | U4 が足す文言（`auth.login.registered`・`auth.language.label`・`display.theme.system`・`display.theme.light`・`display.theme.dark`・`display.fontSize.sm`・`display.fontSize.md`・`display.fontSize.lg`）は、ja・en の両方を用意する。ja と en の鍵をそろえる | 画面部品のテストで、足した鍵が ja・en の両方にあり、空でないことを見る。画面が切り替えた言語の文言で出ることは部品 7節のテストで見る | 要点 12、要件 NFR8、8節 |
| NFR8.2 | 言語の選択肢の名前（「日本語」「English」）は、訳さない固定の値として骨組みが持つ。U6・U7 も同じ値を使う | 画面部品のテスト（部品 7節の `LoginLanguageSwitch`）。画面の言語が en でも「日本語」と出ることを見る | 要点 12、D12、8節 |

## 5. テスト

テストの置き場と名前は既存の決まりどおりです。対象と同じ場所に `*.test.ts(x)` を置き、説明文は英語で書きます。E2E は `frontend/e2e/*.e2e.ts` に置きます。個々のテストの一覧は部品 7節を正とします。

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.8 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を守る。U4 のために計測の除外を増やさない | `./gradlew verify` の中のフロントエンドのカバレッジの検証で、値を記録する | 要点 13、要件 NFR9、`team.md` の Testing Posture |
| NFR9.9 | 性質ベースのテスト（fast-check）を次の4つの関数に当てる。保存の値の読み取りと検証、テーマの選択と OS の配色から解く関数、ブラウザの言語設定から言語を解く関数、画面の値を決める関数。性質は次の3つ。どの入力でも3つの軸が許される値になる、壊れた値・知らない値の項目は無いものになる、有効な値はそのまま。失敗時の乱数の種を記録する | 単体テスト（fast-check） | 要点 13、10節、`team.md` の Testing Posture |
| NFR9.10 | 既存の画面のテスト（`frontend/src/app/testing/renderWithProviders.tsx` を使うものを含む）と、ログイン・ログアウト・トークンの更新のテスト、既存の E2E（`frontend/e2e/` の 010〜040）が通り続ける。この Intent の代表の流れの E2E（招待から登録の完了まで）は U5・U6 が受け持ち、U4 では足さない | `./gradlew verify` と `./gradlew e2eTest` | 要点 13、要件 NFR9、機能設計の設計の要点 18 |
| NFR9.11 | NFR7.3 のアクセシビリティの検査は、画面からの一連の操作の流れではない。そのため、`team.md` の「機能の Intent ごとに代表の流れを1本まで足す」の本数に数えない。E2E と同じく `./gradlew e2eTest` で実行し、前のテストが作った状態に頼らない（ログインの後の画面の検査は、利用者などの前提を自分で作る） | B4・B5 のコード生成の計画で、検査のファイルが流れの E2E と分かれていることを確かめる | Q2 B（依頼者が受け入れた読み方）、`team.md` の Testing Posture |

## 6. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| アクセシビリティの検査を E2E の本数に数えない（Q2 B） | `aidlc/spaces/default/memory/team.md` の Testing Posture | 「本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す」 | 流れではない検査として本数に数えない読み方を、依頼者が受け入れた（NFR9.11）。`team.md` は書き換えない |
| 実際のブラウザの検査（Playwright＋axe-core）を足す | `team.md` の Testing Posture | 画面部品ごとに vitest-axe の検査を1件入れる | vitest-axe の検査はそのまま残し（NFR7.2）、実際のブラウザの検査を追加する（NFR7.3）。食い違いではない |
| NFR7 をログインの画面にも当てる | 要件の NFR7 | 対象は新しい画面（招待・招待の一覧・登録の完了・プリファレンス・パスワードの変更） | U4 が言語の切り替えと案内を足すログインの画面を、B4 の最初の対象にした（NFR7.5）。追加 |
| NFR7 の確かめ方を決めた | 機能設計の `frontend-components.md` の 7節、ストーリーの「後の段に回す点」 | 「確かめ方は NFR 要件の段の持ち主のまま」 | この段で決めた（NFR7.3・NFR7.5）。承認済みの文書は、持ち主を示すだけで書き換えは要らない |
| axe-core を明示の依存にし、採用の記録を残す | `frontend/package.json` と既存の設計の記録 | axe-core は vitest-axe の推移依存で、採用の記録が無い | 2.1 に ADR 形式で記録した（NFR9.6）。既存の vitest-axe の採用そのものは変えない |
| 横のはみ出しの確かめは、既定の表示の幅だけで行う | `inception/refined-mockups/interaction-spec.md` | 狭い幅（768px 未満）でも崩れずに読めて操作できる | Q2 B の答えは組の数だけを決め、幅は決めていない。NFR7.3 は既定の幅だけとし、狭い幅の自動の確かめは入れない。狭い幅は今までどおり画面の段の決まりとして扱う。狭い幅を検査に足すかは、B5 の計画で依頼者に確かめる |
| 固定先の更新の統合を fast-forward でよいとする | `team.md` の Way of Working | サブモジュールの固定先の更新を含む変更に限り、fast-forward でよい | 決まりどおり（NFR9.7）。食い違いではない |
