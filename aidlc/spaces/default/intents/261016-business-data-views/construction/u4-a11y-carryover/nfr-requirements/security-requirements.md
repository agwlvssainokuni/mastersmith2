# セキュリティとアクセシビリティの要件 — U4 u4-a11y-carryover

U4 は種類 `ui` の単位で、段の定義により performance・security・tech-stack・traceability だけを作る。そのため、この文書に次の4つを節に分けて書く。

- セキュリティ（NFR4）
- アクセシビリティ（NFR5）
- 言語（NFR6）
- テスト（NFR7。NFR7.1〜NFR7.3 は `performance-requirements.md`）

U4 は認証・認可・API を変えない。扱う秘密は無いが、読み上げの名前に個人に関する値（氏名・メールアドレス）と、U3 の行の値が入る。

## 上流の ID との対応

| 上流 | 上流の枝番 | この文書の ID | 扱い |
|---|---|---|---|
| NFR4 セキュリティ | 4.1 SQL の組み立て | — | 当たらない（SQL を作らない） |
| | 4.2 行の値をログ・トレース・エラー応答に出さない | NFR4.2 | 同じ意味で受け持つ（名前の値に当てる） |
| | 4.3 書き込みの接続、4.4 問い合わせの時間の上限 | — | 当たらない（対象DB を使わない） |
| | （足す） | NFR4.5〜NFR4.9 | 4.4 の次から振る |
| NFR5 アクセシビリティ（枝番なし） | — | NFR5.1〜NFR5.5 | |
| NFR6 言語（枝番なし） | — | NFR6.1・NFR6.2 | |
| NFR7 テスト（枝番なし） | — | NFR7.4〜NFR7.7 | NFR7.1〜7.3 は `performance-requirements.md` |

## 1. セキュリティ（NFR4）

| ID | 要件 | 確かめ方 |
|---|---|---|
| NFR4.2 | `rowActionsName` と ShellLayout は、名前の値（氏名・メールアドレス・グループとロールの名前・U3 の行の値）をログ・トレースに出さない。関数はログを書かず、値は画面の `aria-label` にだけ渡す | コードのレビューと、名前の関数の単体テスト（ログの呼び出しが無いこと） |
| NFR4.5 | E2E の axe の記録（違反の記録・失敗の文・注記・添付・標準出力・B3 の試しの記録）には、規則・`data-testid`・要素の種類（タグとクラスの印）だけを出す。axe の選択子（`target`）と値は出さない（Q2: A） | E2E の補助の単体の確かめと、json の報告の文字列の検索。E2E の利用者の氏名・メールアドレスと、U3 の見本の行の値が現れないこと |
| NFR4.6 | 名前は文字列として作るだけにし、HTML として描かない | 性質のテスト（NFR7.4）で、`<`・`"`・`'`・改行を含む値が文字のまま返ることを確かめる |
| NFR4.7 | E2E とテストの値は架空の値だけにする（メールアドレスは `example.com` など）。json の報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）が通ること | E2E の全件の実行 |
| NFR4.8 | 依存の確かめ（B3 の試しと B7） | 下の4項目 |
| NFR4.9 | make-you-chic-ui の固定先を上げる手順は、FR14.1 と team.md のとおりにする（上流の `main` から辿れること、vendor の lockfile の差、`hasInstallScript` の増減、前後のコミットハッシュ、承認を得た専用のコミット）。B7 で依頼（5）を取り込むときも同じ | コード生成の記録 |

NFR4.8 の確かめ:

- 新しい版の axe-core の `frontend/package-lock.json` の項に、`hasInstallScript` が無いこと。
- OSV-Scanner の結果を記録すること。axe-core は devDependency で `config/npm-build-tools.txt` に無いため、High は警告になる（team.md）。`MAL-` は統合を止める。
- ビルドした `frontend/dist` に axe-core の本体が入らないこと（`frontend/e2e/support/axe.ts` は E2E の Node の側で読むだけ）。
- `frontend/.npmrc` の `ignore-scripts=true` のまま入れること。

脅威の見立て: 名前に入る値は、もとから画面に出している値で、新しく見せる値は無い。気をつけるのは、検査の記録へ値が漏れることだけ。axe-core は、31 文字未満の属性の値を選択子に使い、`aria-label` を対象から外さない（4.13 の `frontend/node_modules/axe-core/axe.js` 10927〜10955行）。そのため NFR4.5 を置いた。

## 2. アクセシビリティ（NFR5）

| ID | 要件 |
|---|---|
| NFR5.1 | 実際のブラウザの axe は、WCAG 2.0・2.1 の A・AA のタグ（`frontend/e2e/support/axe.ts` の `WCAG_TAGS`）で行い、`frontend/e2e/support/displayCombos.ts` の 20 組のすべてで流す。B7 で `REQUIRED_RULES` に `label-content-name-mismatch` を足す。4.13 ではこの規則に `experimental` の印があり既定では流れない。4.14 以降で既定で流れることを、NFR5.3 の passes の確かめで裏付ける |
| NFR5.2 | B7 の時点で、E2E の全件が通り、E2E と画面の部品のテスト（vitest-axe）の axe の違反が 0 件であること（FR14.3a）。規則・要素を外さず、`KNOWN_VIOLATIONS` は空のまま（project.md の Forbidden） |
| NFR5.3 | 次のトリガーが、規則 `label-content-name-mismatch` の passes に入り、incomplete・violations に入らないことを E2E で確かめる（機能設計の D10b・D10c）。incomplete は合格に数えない。確かめる要素が見つからない（0 件）ときは、確かめの作りの誤りとして失敗にする |
| NFR5.4 | B3 の試し（D9）で、違反を「ファイル × 表示の組 × 規則 × 要素（`data-testid`）」の形で記録する（NFR4.5 のとおり選択子は出さない）。あわせて記録すること: 対象のトリガーが入った区分、違反の要素の見立ての食い違い（「上流との差」の1）を確かめた結果、前の Intent で incomplete だった 060 の結果。試しの版は統合しない。4.13 に戻したことを `package.json`・`package-lock.json` の差で確かめる |
| NFR5.5 | jsdom ではこの規則を判定できないため、画面の部品のテストでは、名前を `getByRole(..., { name })` で明示して確かめる（D8） |

NFR5.3 の対象のトリガー:

- 行の「操作」のトリガー（利用者・グループ・ロールの管理の一覧の、各1行以上）
- トップバーの利用者のメニューのトリガー（2語の氏名の利用者で表示した画面）

NFR5.2 で記録する件数:

- E2E の通過の件数と違反の件数
- passes に入った対象の件数
- vitest-axe のファイル数と件数

## 3. 言語（NFR6）

| ID | 要件 |
|---|---|
| NFR6.1 | 次の鍵を、日本語と英語の両方で用意する。英語の `busySuffix` の先頭の空白（` (processing)`）が文言の仕組みで保たれることを、単体テストで確かめる（機能設計の R-04） |
| NFR6.2 | 氏名が空のとき（`getInitials` が空文字を返すとき）は、トップバーの名前から頭文字と区切りを省く。「: のメニュー」のような形にしない（R-04） |

NFR6.1 の鍵: `topbar.userMenu`・`useradmin.actions.row`・`*.actions.busySuffix`

## 4. テスト（NFR7）

| ID | 要件 |
|---|---|
| NFR7.4 | `rowActionsName` に fast-check の性質ベースのテストを当て、失敗のときの乱数の種を記録する。確かめる性質: 見える文字で始まる、空でない行の値を `trim` して含む、空白だけの値では見える文字だけを返す、区切りはちょうど1つ |
| NFR7.5 | 書き換えの対象のテストと E2E（機能設計の `frontend-components.md` 5節）を、コード生成の計画で名前（「の操作」「のメニュー」「Menu for」）の検索で洗い出し直す（project.md） |
| NFR7.6 | 固定先 `b0a1e79` の後も、`toHaveAccessibleDescription` で説明を確かめる既存のテスト（`WorkRoleSwitcher.test.tsx`・`UserRowActions.test.tsx` など）が、書き換えずに通ること（D7） |
| NFR7.7 | frontend のカバレッジの下限（全体の合計で行 80%・分岐 70%）を保つ。除外を増やさない（team.md） |

B7 の passes の確かめと既存の E2E の書き換えは、E2E の本数に数えない（team.md、project.md の学び）。

## 上流との差

1. **違反の要素の見立て**: 機能設計の 2節は、前の Intent の 100 件（E2E 080・100・120・130・160 の各 20 件）の主な原因をトップバーの頭文字と見ている。前の Intent の記録では、違反の要素は次の2つで、トップバーは出てこない。この見立ては未確認として扱い、B3 の試しで確かめる（NFR5.4）。機能設計の文書は書き換えない。
   - 記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md` の Step 10、`.github/dependabot.yml` の 65〜70行の注記
   - 要素1: make-you-chic-ui の Dropdown のトリガー（表示の設定の誤りの状態、利用者の行の操作など）
   - 要素2: 作業ロールのメニューの項目
2. **機能設計の承認の場の決定**: 定義の API の `nullable` を U2 が持つこと、`formPart` の `lookup` を外すこと、U3 で欠けた値を形の崩れとして扱うこと。どれも U1〜U3 の継ぎ目の決定で、U4 には当たらない。
3. **合格の条件の足し**: 要件 FR14.3a の合格（違反 0 件と件数）に、対象のトリガーが passes に入ること（NFR5.3）を足している。機能設計の G5 を受け継いだもので、要件の文書は書き換えない。

## 後の段に回すこと

- **B3 の統合の単位（R-03）**: 依頼（5）の送付の有無を承認の場の記録で確かめることと合わせて、コード生成の計画（B3）で決める。
- **トップバーの passes の確かめの置き場**: 130 か、2語の氏名の利用者で表示する別の E2E かを、コード生成の計画（B7）で決める。新しいファイルにするときは、U3 の 180・185 と番号が重ならないようにする。
- **U3 とそろえること**:
  - 表示の組（20 組）と、4.14 の試しの記録の形（NFR5.4・NFR4.5。U3 の D31）。
  - passes の確かめの部品（機能設計の D10a）を、U3 の業務データの行の操作にも使う。そのため、U3 の行の操作に、主キーを含まない `data-testid` の接頭辞を、U3 の NFR 要件とコード生成で決めてもらう。
- **見立ての食い違い**: B3 の試しで Dropdown のトリガー・作業ロールの項目・トップバーのどれの違反かを確かめる。make-you-chic-ui の部品の違反が残れば、新しい依頼を出して依頼者に諮る（機能設計の 8節、FR14.5・FR14.6）。
- **NFR4.8 の確かめ**: B3 の試しと B7 で行う。

## Sources

- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR14、NFR4〜NFR7）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（2節・D1〜D12・D10a〜D10c・8節・9節）、`frontend-components.md`（2節・4節・5節・7節）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u4-a11y-carryover/7dacbaa3e842d0af/1.json`（R-01 Resolved、R-02〜R-04 Deferred）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/nfr-requirements-questions.md`（Q1〜Q3: A、まとめの確認: Looks correct）
- U3: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D8・D29〜D31）、`frontend-components.md`（7節）
- コード:
  - `frontend/e2e/support/axe.ts`（`WCAG_TAGS`・`REQUIRED_RULES`・`ruleIds`・141行の選択子への切り替え）
  - `frontend/e2e/support/displayCombos.ts`
  - `frontend/node_modules/axe-core/axe.js`・`axe.min.js`（規則の印と選択子の属性）
  - `frontend/package-lock.json`
  - `frontend/.npmrc`
  - `config/npm-build-tools.txt`
  - `.github/dependabot.yml`
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Avatar/Avatar.tsx`（`getInitials`）
- 前の Intent: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md`（Step 10）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style・Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden・学び）

## Assumptions & Open Questions

- 違反の要素の見立て（トップバーか、Dropdown のトリガーと作業ロールの項目か）は未確認。B3 の試しで確かめる（NFR5.4）。
- 対象のトリガーを、近い祖先の `data-testid` とトップバーのクラスで見分けられるという前提は、B3 の試しで確かめる（機能設計の Assumptions）。
