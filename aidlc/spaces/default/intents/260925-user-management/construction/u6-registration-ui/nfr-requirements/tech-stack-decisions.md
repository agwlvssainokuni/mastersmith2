# Tech Stack Decisions — U6 登録の完了の画面（u6-registration-ui）

この文書は、U6 の技術の選定と、依存・アクセシビリティ・多言語・テストの要件をまとめたものです。技術は前の Intent と U4 から変えません（React・TypeScript・Vite・React Router・i18next・make-you-chic-ui・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・axe-core。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md` と `construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md`）。U6 が足す依存はありません。答えは `nfr-requirements-questions.md` にあります（Q1: B、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md` の ID です。
- D・W と「n節」は、この単位の `construction/u6-registration-ui/functional-design/functional-spec.md` の節です。
- 「部品 n節」は、同じフォルダの `frontend-components.md` の n節です。
- Q と「要点 n」は、この段の `nfr-requirements-questions.md` の番号です。
- 「共通の決定」は、U5 の NFR 要件の質問で出た、画面の3つ（U5〜U7）に共通する依頼者の決定です（狭い幅は、U4 の Playwright＋axe-core の検査に表示の幅 375px を1つ足し、テーマ2×文字の大きさ3の6組で確かめ、B5 で U5・U6・U7 のすべての画面に当てる。画面の時間の目標を置き、5回ずつ測って記録し、統合の関門にはしない）。

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。この文書に置くのは、依存と make-you-chic-ui（NFR9.6・NFR9.7）、アクセシビリティ（NFR7.1〜NFR7.5）、多言語（NFR8.1・NFR8.2）、テスト（NFR9.8〜NFR9.11）です。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 画面の置き場 | `frontend/src/features/registration/` を新しく置き、機能の登録の仕組みで遅延読み込みにする。入力の確かめの関数は `frontend/src/shared/validation/`（新しい、U7 と共用） | 部品 2節、NFR6.3 |
| 画面の部品 | make-you-chic-ui の Card・FormField・TextInput・Button（`loading`）・Alert・RadioGroup（`legend`・選択肢ごとの `lang`）。自前のラジオの部品は作らない | 部品 1節・6節、10節 |
| API の呼び出し | 既存の ApiClient（`apiRequest`）。公開の API のパスの扱いは U4 の ApiClient のまま | 部品 3節、NFR9.1 |
| 表示の設定と受け渡し | U4 の口（`useDisplaySettings`・`saveBrowserDisplaySettings`・`handOffToLogin`・`LANGUAGE_NAMES`） | 部品 7節、契約 C9 |
| 文言 | 既存の i18next・react-i18next | NFR8.1 |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | `team.md` の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.9 |
| 実際のブラウザのアクセシビリティの検査 | Playwright（既存）＋ axe-core（U4 の NFR9.6 で明示の開発時の依存にしたもの）。フォームの状態は Playwright の要求の差し替え（`page.route`、既存の機能）で出す | Q2 B、NFR7.3・NFR7.5 |
| 代表の流れの E2E（E2E-1） | Playwright（既存）、`./gradlew e2eTest` | W13、NFR9.10 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | NFR6.3 |

## 2. 依存と make-you-chic-ui

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.6 | U6 は新しい依存（実行時・開発時とも）を足さない。`frontend/src/shared/validation/` は自前のコードで、新しい依存を持たない。アクセシビリティの検査の要求の差し替えは Playwright の既存の機能（`page.route`）で行い、差し替えのための部品を足さない | B5 のコード生成で、`frontend/package.json` と lockfile に U6 のための差分が無いことを確かめる（make-you-chic-ui の固定先の更新と U4 の axe-core による差分は除く） | 要点 12、`team.md` の Code Style（新しい依存のライセンス） |
| NFR9.7 | make-you-chic-ui の新しい版（RadioGroup の `legend`・選択肢ごとの `lang`、Button の `loading` の `aria-disabled`・`aria-busy`）に頼る。固定先の更新（`edb1f94` から `735ef04`）は B4 で、承認を得た専用のコミットで行い、更新前後のコミットハッシュを記録する（U4 の NFR9.7）。U6 の B5 はその後に進める。make-you-chic-ui の中身はこのリポジトリから変えない | B5 のコード生成の計画の前提に、B4 の固定先の更新が済んでいることを書く。サブモジュールに差分が無いことを確かめる | 要点 12、部品 6節・9節、`project.md` の Mandated・Forbidden |

## 3. アクセシビリティ

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする（U4 の NFR7.1 のまま）。文字のコントラスト 4.5:1、部品 3:1、キーボードで操作できる、読み上げで名前が分かる、色だけに頼らない。テーマ（`light`・`dark`）・文字の大きさ（`sm`・`md`・`lg`）・ブランドカラー（4つ）のどの組み合わせでも満たし、狭い幅（768px 未満）でも崩れずに読めて操作できる | NFR7.2・NFR7.3 | 要点 13、要件 NFR7、`inception/refined-mockups/interaction-spec.md` |
| NFR7.2 | U6 の画面部品（`RegistrationPage`・`RegistrationStatus`・`RegistrationUnavailable`・`RegistrationLoggedInNotice`・`RegistrationForm`）ごとに、vitest-axe の検査を1件入れ、違反 0 件とする。`RegistrationPage` の検査で確かめ中・使えない・ログイン中の案内・読み込めないの各表示をどう含めるかは、コード生成で決める | 画面部品のテスト（部品 8節） | 要点 13、`team.md` の Testing Posture |
| NFR7.3 | U4 の実際のブラウザのアクセシビリティの検査（U4 の NFR7.3、`./gradlew e2eTest` の中、`./gradlew verify` と CI の外）に、B5 で登録の完了の画面を足す。検査する状態は、フォーム（`ready`）と「リンクが使えない」（`unavailable`）の2つ。状態ごとに次の組を切り替えて axe-core を流し、コントラストを含む違反 0 件と、各組で画面の幅を超える横のはみ出しが無いこと（文書の横の大きさが表示の幅を超えない）を確かめる。(a) テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`、表示の幅は既定の `Desktop Chrome`）。(b) ブランドカラー4×テーマ2の8組（文字の大きさは `md`、既定の幅）。(c) 表示の幅 375px で、テーマ2×文字の大きさ3の6組（ブランドカラーは既定の `blue`。共通の決定） | Build and Test で `./gradlew e2eTest` を実行し、状態と組ごとの成否と違反の件数を記録する。画面・認証に関わる変更を統合する前と、リリースの前にも手元で実行する（`team.md` の Testing Posture） | Q2 B、共通の決定、U4 の NFR7.3・NFR7.5、要件 NFR7 |
| NFR7.4 | 言語の選択肢は、それぞれの言語の名前（「日本語」「English」）に `lang` 属性を付けて示す。3つの選択のまとまり（言語・テーマ・文字の大きさ）は `fieldset`・`legend` で名前を持ち、矢印キーで選べる。言語を切り替えた後もフォーカスを選んだラジオに残し、画面の言語が変わったら `<html lang>` も変える。失敗の知らせは `role="alert"`、入力の誤りは項目の下に文字で出して `aria-describedby`・`aria-invalid` で結び付け、最初の誤りの項目にフォーカスを移す。送信中・ログアウト中のボタンは `aria-disabled`・`aria-busy` でフォーカスを保つ | 画面部品のテスト（部品 8節の RadioGroup の使い方・入力の確かめ・送信の間・ログアウト中） | 要点 13、W5〜W8、CR6.1・CR6.3・CR6.6、U4 の NFR7.4 |
| NFR7.5 | NFR7.3 のフォームの状態は、検査のブラウザのコンテキストの中だけで、リンクの確かめの API（`POST /api/registration/verify`）の答えを Playwright の `page.route` で差し替えて出す。差し替える答えは 200 で、本文は `example.com` のメールアドレスと言語 `ja`。トークンはどの招待にも当たらない固定の見本の値（NFR1.5）。ほかの要求（画面の配信・見た目の設定・セッションの復元）は差し替えず本物の WAR に送る。「リンクが使えない」の状態は、フラグメントの無い `/register` を本物のまま開いて出す。アプリのコードと CSP は変えない（NFR9.5）。検査のファイルは E2E-1 の流れのファイルと分け、流れの E2E の本数に数えない（U4 の NFR9.11）。本物の流れ（招待から登録の完了まで）は E2E-1 が確かめる | B5 のコード生成の計画で、検査のファイルが E2E-1 と分かれていること、差し替えが確かめの API だけでその検査のコンテキストの中に限られることを確かめる。Build and Test の結果に、状態ごとの検査の結果を記録する | Q2 B、U4 の NFR9.11 |

### 3.1 記録: アクセシビリティの検査でフォームの状態を出す手段（Q2 B）

- 状況: フォームの状態を出すには有効な招待のトークンが要ります。本物のトークンを得るには、管理者でログインして招待し、手元の受け手から招待メールのリンクを取り出す必要があります。その手段は infrastructure-design の持ち主で、この段では決まっていません。検査の目的は描画・色・はみ出しの確かめで、サーバーの動きの確かめではありません。
- 決定: 検査のブラウザのコンテキストの中だけで、リンクの確かめの API の答えを差し替えてフォームを出します（NFR7.5）。「リンクが使えない」の状態は本物のまま開きます。
- 良い点: 受け手の手段に頼らず、検査が E2E-1 と独立して動きます。組の数（状態ごとに 20 組）だけ招待を作らずに済み、内部DB と監査の行も増えません。
- 悪い点: フォームの状態の検査は、本物の確かめの API の応答の形を通りません。応答の形の食い違いは、契約 C6 の結合テスト（U3）と E2E-1 で見つける前提です。
- 退けた案:
  - A. 本物の流れで出す（E2E-1 と同じ手段で受け手からリンクを取り出す）: 本物に近い代わりに、infrastructure-design の受け手の手段に頼る点が E2E-1 のほかにもう1つ増え、検査が流れの前提に縛られます。
  - C. 「リンクが使えない」の状態だけを実際のブラウザで検査する: 手間は最も少ない代わりに、ラジオ3組と入力の欄が並び最も崩れやすいフォームの状態のコントラストと崩れを、実際のブラウザで確かめられません。
- セキュリティ: 差し替えはテストのブラウザの中だけで、アプリのコード・設定・CSP に手を入れません。見本のトークンは本物の招待に当たらない値で、秘密情報ではありません。

## 4. 多言語

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR8.1 | U6 が足す文言（`registration.` で始まる鍵）は、ja・en の両方を用意し、ja と en の鍵をそろえる。アプリ名は骨組みの `app.name`、テーマ・文字の大きさの選択肢は U4 の `display.theme.*`・`display.fontSize.*`、言語の選択肢は U4 の `LANGUAGE_NAMES`（訳さない、U4 の NFR8.2）を使う | 画面部品のテストで、足した鍵が ja・en の両方にあり、空でないことを見る。画面の言語が en でも言語の選択肢が「日本語」と出ることを見る | 要点 14、要件 NFR8、7節 |
| NFR8.2 | 招待の言語を確かめの後に当て、利用者が選んだ言語を画面と要求に当てる。言語 en の招待を、ブラウザの言語が ja の状態で開くと、確かめの後の画面の文言・`<html lang>`・画面の確かめの誤り（11 文字のパスワード）が en で出て、完了の要求の `Accept-Language` が en になる。サーバーの説明文の言語は、画面に `detail` を出さないため U3・U4 のテストで確かめる | 画面部品のテスト（部品 8節の開く・言語 en を選ぶ）。9節の CR1 の確かめ方の読み替えのとおり | 要点 14、D6・W5・W6、CR1.2・CR1.3、9節・10節 |

## 5. テスト

テストの置き場と名前は既存の決まりどおりです。対象と同じ場所に `*.test.ts(x)` を置き、説明文は英語で書きます。E2E は `frontend/e2e/*.e2e.ts` に置きます。個々のテストの一覧は部品 8節を正とします。

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.8 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を守る。U6 のために計測の除外を増やさない | `./gradlew verify` の中のフロントエンドのカバレッジの検証で、値を記録する | 要点 15、要件 NFR9、`team.md` の Testing Posture |
| NFR9.9 | 性質ベースのテスト（fast-check）を `shared/validation` の関数に当てる。`countCodePoints`（`Array.from` の長さと同じ）、`utf8ByteLength`（TextEncoder の長さと同じ）、`trimDisplayName`（2回かけても同じ、前後に White_Space が残らない、内側を変えない）、`validateDisplayName`・`validateNewPassword` の境界（253・254・255 コードポイント、11・12 コードポイント、72・73 バイト、絵文字 11・12 文字）。失敗時の乱数の種を記録する | 単体テスト（fast-check） | 要点 15、9節、`team.md` の Testing Posture |
| NFR9.10 | この Intent の代表の流れの E2E（E2E-1、招待から登録の完了まで）を U6 が持ち、`frontend/e2e/` に1本足して `./gradlew e2eTest` で動かす（`./gradlew verify` と CI の外）。流れは W13 のとおりで、前のテストが作った状態に頼らず、管理者のログインと招待を自分で行う。招待するメールアドレスは実行ごとに重ならない値で、予約済みのドメイン `example.com` を使う。メールは E2E が起動したアプリの手元の受け手だけに送り、実在の宛先・外部の SMTP へ送らない。流れ全体の時間の目標は置かず（Playwright の既定の時間切れのまま）、流れの成否を見る。リンクを開いてからフォームが出るまでの時間（NFR6.1）とアドレス欄のフラグメント（NFR1.3）は、この流れの中で確かめる。招待メールのリンクの取り出し方と、E2E の WAR への SMTP の設定の渡し方は infrastructure-design が決める | Build and Test で `./gradlew e2eTest` を実行し、結果を記録する。画面・認証に関わる変更を統合する前と、リリースの前にも手元で実行する（`team.md` の Testing Posture） | 要点 16、W13、要件 NFR9、`team.md` の Testing Posture・Deployment、`project.md` の Forbidden |
| NFR9.11 | 既存の画面のテストと、既存の E2E（`frontend/e2e/` の 010〜040）が通り続ける。U4 の実際のブラウザのアクセシビリティの検査に足す登録の完了の画面の検査（NFR7.3）は流れではないため、E2E の本数に数えない | `./gradlew verify` と `./gradlew e2eTest` | 要点 15、U4 の NFR9.10・NFR9.11 |

## 6. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| U4 の実際のブラウザの検査に、表示の幅 375px の6組を足す（共通の決定） | `construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md` の NFR7.3 と「上流との差」 | 横のはみ出しは既定の表示の幅（`Desktop Chrome`）だけで確かめる。狭い幅を検査に足すかは B5 の計画で依頼者に確かめる | U5 の質問で出た共通の決定で、B5 で U5・U6・U7 のすべての画面に幅 375px の6組を足すことに決まった。U6 では NFR7.3 の (c) として書いた。U4 の文書は書き換えない。追加 |
| U4 の実際のブラウザの検査で、フォームの状態を要求の差し替えで出す（Q2 B） | U4 の NFR7.3・NFR7.5 | 画面ごとに組を切り替えて axe-core を流す。組の切り替え方はコード生成で決める。画面を出す手段は決めていない | 登録の完了の画面のフォームの状態は、検査のブラウザのコンテキストの中だけでリンクの確かめの API の答えを差し替えて出す（NFR7.5、3.1 の記録）。U4 の文書は書き換えない。追加 |
| 検査する状態を2つにする | U4 の NFR7.3 | 画面ごとに検査する | 登録の完了の画面は状態で見た目が変わるため、フォームと「リンクが使えない」の2つの状態を検査する（NFR7.3）。確かめ中・読み込めない・ログイン中の案内は vitest-axe（構造）で確かめる（NFR7.2）。追加 |
| E2E-1 の中でリンクを 5 回開いて時間を測る | 機能設計の W13 | 流れの中でリンクを開くのは1回 | 流れは W13 のまま、計測の手順を足す（`performance-requirements.md` の NFR6.1 と「上流との差」）。追加 |
| NFR7 の確かめ方（狭い幅を含む） | `frontend-components.md` の 8節の末尾 | 「テーマと文字の大きさのすべての組み合わせで崩れないこと（NFR7）の確かめ方は、NFR 要件の段の持ち主のまま」 | この段で決めた（NFR7.3・NFR7.5）。承認済みの文書は、持ち主を示すだけで書き換えは要らない |
