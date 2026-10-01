# Tech Stack Decisions — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

この文書は、U4 の技術の選定と、アクセシビリティ・多言語・依存・テストの要件をまとめたものです。技術は前の Intent から変えません（React・TypeScript・Vite・react-router・i18next・make-you-chic-ui・Vitest・Testing Library・user-event・vitest-axe・fast-check・Playwright・axe-core。`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`、`frontend/package.json`）。U4 は新しい依存を足さず、バックエンドに手を入れません。答えは `nfr-requirements-questions.md` です（Q1: B、Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は要件 `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md` の ID
- D・W・2節〜10節は、この単位の `construction/u4-admin-forbidden-ui/functional-design/functional-spec.md`。「承認の場」は同じ文書の「承認の場の決定」の節
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md` の番号
- PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。依存・make-you-chic-ui・テスト・画面の側の境界の要件は、上流に当たる ID が無いため、テストと検査の関門で確かめる NFR9 の枝番に寄せました（NFR9.6〜NFR9.12、`project.md` の学び）。

## 1. 選定

| 対象 | 選定 | 理由 |
|---|---|---|
| 判定と読み直しの口 | `frontend/src/shared/api-client/` に `isAdminForbidden`（新しいファイル `adminForbidden.ts`）と `refreshSessionOnce` を足す | 機能設計の 2節（契約 C4 の未解決の点の決定）、Q3 A。骨組みは `features/auth` を読めないため、登録済みの更新を ApiClient から呼ぶ |
| 状態・表示・失敗を渡す口 | `frontend/src/app/admin-forbidden/` に `AdminForbiddenProvider`・`useAdminForbidden`・`AdminForbiddenView`・`forbiddenHeadingKey` を置く | 機能設計の 2節、部品 3.3〜3.5 |
| 振り分けと置き換え | 既存の `app/routing/`（`ADMIN_FORBIDDEN` を足す）と `app/layout/ShellLayout.tsx` | D3・D6、部品 1節・4節（同じ形の木で作り直さない、承認の場の R-05） |
| 自分の氏名と言語の口 | 既存の `app/display-settings/` に `useApplyOwnProfile` を足す | D13・D14、部品 3.6 |
| 表示の部品 | make-you-chic-ui の `Alert`（`variant` が info のとき `role="status"`）。見出し・リンクは React と react-router の既存の書き方 | D12、承認の場の R-05（`vendor/make-you-chic-ui` の `Alert.tsx` で確かめ済み） |
| 文言 | 既存の i18next・react-i18next（骨組みの文言 `frontend/src/app/i18n/messages/ja.ts`・`en.ts` に `adminForbidden.*` を足す） | 7節、NFR8.1 |
| 画面部品のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe（既存） | TM の Testing Posture、NFR7.2 |
| 性質ベースのテスト | fast-check（既存） | NFR9.9 |
| 実際のブラウザのアクセシビリティの検査 | Playwright（既存）＋ axe-core（既存の devDependencies）。補助は既存の `frontend/e2e/support/`（`adminLogin.ts`・`axe.ts`・`displayCombos.ts`・`overflow.ts`・`pageProblems.ts`）を使う | Q1 B、NFR7.3 |
| 初回の JavaScript の大きさの確かめ | 既存の `frontend/scripts/check-bundle-size.mjs` | `performance-requirements.md` の NFR9.4 |

## 2. アクセシビリティ

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR7.1 | 目標は WCAG 2.1 AA とする。S6 では次を守る。Alert（info）は `role="status"` で文言が読み上げられる。S6 を出したら見出しへフォーカスを移す。「ホームへ戻る」はキーボードで届くリンクとする。ForbiddenByApi から ForbiddenByRoute へ移っても、S6 の見出しの要素を作り直さず、フォーカスの位置とサイドバーの開閉の状態を変えない（見出しへのフォーカスの移し直しも起きない） | コード生成で、`AdminForbiddenView.test.tsx`（`role="status"`、見出しへのフォーカスは `waitFor` で待つ、「ホームへ戻る」でホームへ移る）と `AppRouter.test.tsx`（見出しの要素が同じもので、フォーカスとサイドバーの状態が変わらない）で確かめる | 要件 NFR7、D12、4節、部品 3.4・7節、承認の場の R-05、要点 12 |
| NFR7.2 | 新しい画面部品 `AdminForbiddenView` に vitest-axe の検査を1件入れ、違反 0 件とする | コード生成で `AdminForbiddenView.test.tsx`（Bolt B2 の完了の条件） | 要件 NFR7、TM の Testing Posture（画面部品ごとに1件）、要点 12 |
| NFR7.3 | 実際のブラウザの検査を新しいファイル `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` に足す。初期管理者でログインし、管理の入口の確かめの API（`GET /api/admin/check`）の答えだけを 403・code `ACCESS_DENIED` の見本に差し替えて管理の入口を開き、S6 を出す。既存の 20 組（`support/displayCombos.ts`。既定の幅のテーマ2×文字の大きさ3の6組、ブランドカラー4×テーマ2の8組、幅 375px のテーマ2×文字の大きさ3の6組）のそれぞれで、(1) axe-core の違反 0 件（WCAG 2.0・2.1 の A・AA、`color-contrast` を含む。`support/axe.ts` の `REQUIRED_RULES` が走ったことも確かめる）、(2) 文書の横の大きさが表示の幅を超えないこと、(3) Alert が `role="status"` であること、見出しにフォーカスがあること、を確かめる。「ホームへ戻る」でホームへ移れることを確かめる。既知の違反の一覧は空から始める。管理の POST は送らず（`pageProblems.ts` の要求の一覧で確かめる）、初期管理者の状態は変えない。差し替えた 403 によるブラウザのコンソールの読み込みの失敗の表示は、その1本の URL に限って問題の一覧から除き、ほかの CSP の違反・スクリプトのエラーは 0 件とする。この除外は 130 の側だけで行い、共有の `watchPage`（`support/pageProblems.ts`）の既定の除外（更新の 401 だけ）は変えない（ほかの E2E に効かないようにするため）。上の帯の Avatar も同じ axe の検査の対象になるため、現実に近いデータとして、上の帯の氏名を空白で区切った2語（頭文字が2文字になる値。テストのための架空の値で、実在しそうな氏名にしない）にした状態で、20 組のすべてを検査する（`project.md` の学び。前の Intent で頭文字が2文字の Avatar にだけコントラスト不足が出た）。初期管理者の氏名は変えない決まりのため、2語の氏名はサーバーの状態を変えずに作る。作り方の案は、既存の `support/loginPreferences.ts` と同じ当て方で、ログイン（POST `/api/auth/login`）と Cookie での復元（POST `/api/auth/session/refresh`）の本物の応答の `user.displayName` だけを2語の値に書き換えて返す形（ほかの項目・Cookie は本物のまま、書き換えはそのページの中だけ、値を注記・添付・標準出力に出さない）。共有の手伝いの既定の動作は変えず、130 の側で書き換えるか、手伝いに省略できる選択の引数を足す。具体の形はコード生成の計画で決める。頭文字が2文字で描かれたことは、Avatar の文字で確かめる（make-you-chic-ui の `getInitials` は空白で区切った最初の2語から最大2文字を作る）。130 が実際のブラウザで確かめるのは ForbiddenByApi（403 の差し替え）だけとする。E2E には初期管理者（管理者）しかおらず、管理者でない利用者を作ると流れの E2E に近い準備が要るため、ForbiddenByRoute は部品のテスト（`decideRoute.test.ts`・`AppRouter.test.tsx`）で確かめる。両者は同じ `AdminForbiddenView` を描くため、色とはみ出しの検査は ForbiddenByApi で足りるものとする | コード生成で検査を書き、手元で `./gradlew e2eTest` を流して組ごとの結果（違反の件数・はみ出しの有無・成否）と、Avatar の頭文字が2文字だったことを記録する。2語の氏名の作り方（書き換えの置き場と共有の手伝いへの影響）はコード生成の計画に書く（持ち主は code-generation）。Build and Test でも流して記録する。`./gradlew verify` と CI の外に置き、画面・認証に関わる変更を統合する前と、リリースの前に手元で流す（TM の Testing Posture） | Q1 B（答えの後に U5 にそろえた番号と組）、要件 NFR7、PM の Testing Posture（axe は誤りの状態と現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組で）、要点 13、承認の場の R-01・R-04 |

## 3. 多言語

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR8.1 | S6 の文言（`adminForbidden.message`・`adminForbidden.homeLink`・`adminForbidden.heading`）は ja・en の両方を持ち、既定は日本語とする。見出しは、URL に登録されたサイドバーの項目の文言の鍵を使い回し（表示の条件によらず登録から探す）、項目が無い URL は共通の見出し「管理」・"Administration" とする。ja と en の文言の鍵がそろっている | コード生成で、`AdminForbiddenView.test.tsx`（ja・en の両方の文言）と `forbiddenHeading.test.ts`（登録された項目の鍵、無ければ共通の鍵）で確かめる。鍵のそろいは既存の文言のテストの形で確かめる | 要件 NFR8、7節、D11、10節の G6、要点 14 |
| NFR8.2 | 自分の氏名と言語の反映では、言語は ja・en だけを当てる。それ以外の値が渡ったときは言語を変えず、氏名だけを当てる。言語を当てたら、文言・`<html lang>`・次からの要求の `Accept-Language` が同じ時点で切り替わる | コード生成で `displaySettingsStore.test.ts`・`DisplaySettingsProvider.test.tsx`（ja・en 以外では言語が変わらない、`Accept-Language` が新しい言語になる）で確かめる | 要件 NFR8・FR6.3、D13、6節、要点 10 |

## 4. 依存と make-you-chic-ui

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.6 | U4 は新しい依存（実行時・開発時とも）を足さない。`frontend/package.json` と `frontend/package-lock.json` に U4 の分の差分を持たない | コード生成で、U4 の変更による2つのファイルの差分が無いことを確かめて記録する | 要点 15、TM の Code Style（新しい依存のライセンスの確認） |
| NFR9.7 | make-you-chic-ui（`vendor/make-you-chic-ui`）の中身を変えず、固定先も U4 では動かさない。S6 は今の固定先の `Alert` だけで作る | コード生成で `vendor/make-you-chic-ui` に差分が無いことを確かめる | 要点 15、PM の Forbidden、機能設計の冒頭 |

## 5. テストと境界

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR9.8 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を保ち、U4 のために計測の除外を増やさない。バックエンドのパッケージには手を入れないため、`packagesJudgedByTotal` の一覧とパッケージごとの下限に U4 の作業は無い | `./gradlew verify`（code-generation・build-and-test）。U4 の変更の後の値を記録する | 要件 NFR9、要点 16、TM の Testing Posture |
| NFR9.9 | 性質ベースのテスト（fast-check、失敗時の乱数の種を記録して再現できる形）を、純粋な関数 `isAdminForbidden`（`security-requirements.md` の NFR1.2）と `forbiddenHeadingKey`（登録に無い URL はいつも共通の鍵）に当てる。管理の入口の今の性質ベースのテストは「`ACCESS_DENIED` 以外の 403 も一般の誤り」に直す | コード生成で `adminForbidden.test.ts`・`forbiddenHeading.test.ts`・`adminAreaStatus.test.ts` | 8節、10節の G9、部品 7節、TM の Testing Posture |
| NFR9.10 | 流れの E2E を足さない。`030-admin-access.e2e.ts` は変えない。NFR7.3 の検査（130）は操作の流れではないため、「機能の Intent ごとに代表の流れを1本まで」の本数に数えない（この Intent の代表の流れは U5 の 110）。U4 の変更の後に、既存の E2E（`frontend/e2e/` の 010〜100）が通ることを確かめる | コード生成（B2）で `./gradlew e2eTest` を流して記録する。Build and Test でも流して記録する | 要点 17、PM の学び（Playwright のアクセシビリティの検査は本数に数えない）、TM の Testing Posture |
| NFR9.11 | 画面のテストは TM の Testing Posture に従う。テストの説明文は英語、描画の後に反映される値は `waitFor` で待つ、テストの時間の上限は原因を確かめずに延ばさない。403 はテストで差し替えて作り（DSL・招待は画面に渡す偽物の API、管理の入口は偽物の `fetch`）、書き換える既存のテスト（部品 7節の書き換えの行、振り分けのテスト2ファイルを含む）は書き換えの範囲を計画に書く。`renderWithProviders` を使う今の画面のテストが、`AdminForbiddenProvider` が並びに入っても通り続ける | コード生成で `./gradlew verify` を通し、書き換えたテストの一覧を記録する | 8節、10節の G5、部品 7節、承認の場の R-03、TM の Testing Posture |
| NFR9.12 | 画面の側の境界を保つ。機能（`features/*`）は骨組みの口を `src/app/` から読み、機能どうしは直接読み合わない。骨組みは `features/auth` を直接読まない。ApiClient（`src/shared/api-client/`）は骨組み（`src/app/`）を読まない | コード生成で、U4 が足した・変えたファイルの import を確かめて記録する（既存のリンタの決まりがあればそれに任せる） | 機能設計の 2節、TM の Code Style（機能は `src/features/<featureId>/`、共有は `src/shared/`） |

## 6. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 実際のブラウザの検査を足す（Q1: B） | 要件の NFR7、機能設計の 8節 | 実際のブラウザでの検査を足すかは設計で決める。機能設計は E2E の `030` を変えないとだけ決めた | 新しいファイル（130）で足した（NFR7.3）。`030` は変えないまま。食い違いではない |
| 検査のファイルの番号と組 | 質問の Q1 の選択肢 B の文 | 選択肢の文は「表示の設定の 20 組（既定の幅）と、幅 375px の6組」と書き、別に数えて読める | 答えの後に U5 の 110・120 と重ならない 130 とし、組は U5 と同じ既存の 20 組（375px の6組を含む）にそろえた（質問の文書のまとめのとおり、依頼者が Looks correct で確かめ済み） |
| 要件 NFR7 の「利用者の管理の画面の画面部品ごと」 | 要件の NFR7 | 対象を利用者の管理の画面と書いている | U4 の新しい画面部品 S6 にも同じ決まり（vitest-axe 1件、WCAG 2.1 AA、テーマと文字の大きさの組）を当てた（NFR7.1〜NFR7.3）。利用者の管理の画面そのものは U5 |
| 130 で上の帯の氏名を2語にした状態で検査する（承認の場の R-01） | `project.md` の学び（axe は現実に近いデータで行う）、TM の Testing Posture（E2E は初期管理者の状態を変えない） | 初期管理者の氏名は変えない | ログインと更新の応答の氏名だけをページの中で書き換えて作る案を NFR7.3 に書いた。サーバーの状態は変えないため、食い違いではない。具体の形はコード生成の計画で決める |
| 依存・テスト・境界の要件を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 依存・画面の側の境界に当たる ID が無い | NFR9.6〜NFR9.12 に寄せた（冒頭のとおり）。traceability.json の NFR9 の target に書く |
