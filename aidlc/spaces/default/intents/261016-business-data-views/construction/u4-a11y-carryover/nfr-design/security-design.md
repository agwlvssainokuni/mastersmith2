# セキュリティとアクセシビリティの設計 — U4 u4-a11y-carryover

U4 は認証・認可・API を変えない。守るものは2つ。

- 読み上げの名前に入る値（氏名・メールアドレス・グループとロールの名前・U3 の行の値）を、検査の記録に漏らさないこと
- 規則 `label-content-name-mismatch` を、対象の要素で確かに判定させること

文章で ID を指すときは、単位の接頭辞を付ける（例 `U4-NFR4.5`）。

## 冒頭の対応表

| NFR 要件の ID | 設計の節 | 要点 |
|---|---|---|
| U4-NFR4.1・4.3・4.4（上流） | — | N/A（SQL・対象DB を使わない） |
| U4-NFR4.2 | 3節 | ログ・`console`・トレースの呼び出しを、コードの検索で洗い出す |
| U4-NFR4.5 | 1節・2節 | 記録の型から `target` を除き、鍵（`key`）に置き換える |
| U4-NFR4.6 | 3節 | 名前は文字列として作る。性質のテストで確かめる |
| U4-NFR4.7 | 1節 | 架空の値だけを使う。json の報告の秘密の確かめに値を足す |
| U4-NFR4.8・4.9 | 4節 | 依存の確かめと、固定先を上げる手順 |
| U4-NFR5.1〜5.5 | 5節 | 規則の足し、違反 0 件、passes の確かめ、試しの記録、jsdom の扱い |
| U4-NFR6.1・6.2 | 6節 | 文言の鍵、空の氏名 |
| U4-NFR7.4〜7.7 | 7節 | 性質のテスト、書き換えの洗い出し、説明のテスト、カバレッジ |

## 1. 選択子を出さない記録（U4-NFR4.5・U4-NFR4.7）

- 今の `frontend/e2e/support/axe.ts` は、`AxeViolationNode.target`（axe の選択子）を Node の側へ返している（33〜40行）。`splitKnownViolations` も、`data-testid` が無いときは選択子を失敗の文に使う（141行）。axe は 31 文字未満の `aria-label` を選択子に使うため、名前の値が漏れうる。
- 直し方:
  - `AxeViolationNode` から `target` を除き、`key`（2節）に置き換える。選択子は `page.evaluate` の中（ブラウザの側）でだけ使い、戻り値に入れない。
  - 失敗の文・注記・添付・標準出力・B3 の試しの記録は、`規則 + key` だけで作る。
- 確かめ方:
  - 型の検査で、`AxeViolationNode` と `AxeRuleNode` に `target` が無いことを確かめる。
  - json の報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）で検索する値に、E2E の利用者の氏名・メールアドレスと、U3 の見本の行の値を足す。

```ts
/** 名前の規則で判定された要素（選択子と値は持たない） */
export interface AxeRuleNode {
  outcome: 'passes' | 'incomplete' | 'violations'
  key: AxeElementKey
}
export interface AxeElementKey {
  element: string          // 例 'button[menuitem]'（タグと role）
  owner: string | null     // 例 'useradmin-row-actions-*'
  marks: string[]          // 例 ['mycui-topbar-user-trigger']
  menuOwner: string | null // portal の中の項目だけ
}
```

（型の名前と項目の名前は、コード生成で確定する。）

## 2. 要素を見分ける鍵（Q1: A、レビューの R-02）

### この段で確かめた事実

- Dropdown は、トリガーの `data-testid` を必ず `dropdown-trigger` に上書きする（`Dropdown.tsx` 135〜143行）。トリガーに `aria-controls` は無い。
- メニューは `document.body` へ portal で描かれる（149行・231行）。項目の `data-testid` は `dropdown-item-<番号>`、メニューは `dropdown-menu`。
- トップバーのトリガーは `button.mycui-topbar-user-trigger` で、祖先の `header.mycui-topbar` に `data-testid` は無い（`Topbar.tsx` 80〜88行）。
- 作業ロールのトリガーは `button.work-role-switcher-trigger` で、祖先に `data-testid="work-role-switcher"` がある（`WorkRoleSwitcher.tsx` 156〜166行）。
- 行の操作の祖先には、`useradmin-row-actions-<ID>`・`groupadmin-row-actions-<ID>`・`roleadmin-row-actions-<ID>` がある。

### 鍵の組（4つ）

| 鍵の部分 | 作り方 |
|---|---|
| element | タグの名前と role（例 `button[menuitem]`） |
| owner | 自分か近い祖先の `data-testid` のうち、`dropdown-trigger`・`dropdown-item-*`・`dropdown-menu` を除いた最初のもの。末尾の ID（`-` の後の数字・英数、U3 の `-r<n>` を含む）を `-*` に置き換えて接頭辞にする |
| marks | クラスのうち `mycui-` で始まるものと、アプリの印のクラス（`work-role-switcher-trigger` など。一覧はコード生成で決める） |
| menuOwner | portal の中の項目だけに付ける。開いているトリガー（`[aria-expanded="true"]`）の owner と marks から作る。開いているトリガーが2つ以上なら、確かめの作りの誤りとして失敗にする |

- 鍵に入れないもの: `aria-label`・要素の文字・`id`（React の `useId` の値）・属性の値。
- owner の末尾の ID を外すのは、主キーなどの値を記録に載せないためと、区分を行によらずにそろえるため。

### 5つの区分の見分け

| 区分 | 鍵 |
|---|---|
| 行の操作のトリガー（3種類） | owner が `useradmin-row-actions-*`・`groupadmin-row-actions-*`・`roleadmin-row-actions-*` |
| 作業ロールのトリガー | owner が `work-role-switcher`、marks に `work-role-switcher-trigger` |
| トップバーのトリガー | marks に `mycui-topbar-user-trigger` |
| 作業ロールの項目 | element が `*[menuitem]`、menuOwner が作業ロールのトリガー |
| トップバーの項目 | element が `*[menuitem]`、menuOwner がトップバーのトリガー |

- B3 の試しの前に、5つの区分を鍵で区別できることを確かめる。鍵の関数は純粋な形（DOM の要素を受けて鍵を返す）にし、jsdom の単体の確かめで5つの区分の見本を区別できることを確かめる。

## 3. 名前の値を漏らさない（U4-NFR4.2・U4-NFR4.6）

- 名前は文字列として作り、`aria-label` にだけ渡す。HTML として描かない。
- U4-NFR4.2 の確かめ（レビューの R-04）: `UserRowActions`・`GroupRowActions`・`RoleRowActions`・`ShellLayout`・`rowActionsName` に、ログ・`console`・トレースの属性の呼び出しが無いことを、コード生成の計画でコードを検索して洗い出す。名前の関数の単体テストの「ログの呼び出しが無い」は、補助にとどめる。
- U4-NFR4.6: 性質のテスト（7節）で、`<`・`"`・`'`・改行を含む値が文字のまま返ることを確かめる。

## 4. 依存と固定先（U4-NFR4.8・U4-NFR4.9）

NFR 要件のまま。

- axe-core の新しい版には、`frontend/package-lock.json` で `hasInstallScript` が無いこと。
- OSV-Scanner の結果を記録する。devDependency のため High は警告になる。`MAL-` は統合を止める。
- `frontend/dist` に axe-core が入らないこと。
- `frontend/.npmrc` の `ignore-scripts=true` のまま入れること。
- 固定先は、team.md の手順で専用のコミットとして上げる。

## 5. アクセシビリティ（U4-NFR5.1〜5.5）

- **U4-NFR5.1**: B7 で、`REQUIRED_RULES` に `label-content-name-mismatch` を足す。タグ（`WCAG_TAGS`）と 20 の表示の組は変えない。
- **U4-NFR5.2**: 違反 0 件。規則・要素は外さない。`KNOWN_VIOLATIONS` は空のまま（project.md の Forbidden）。
- **U4-NFR5.3**: 区分ごとの passes の確かめ。対象の決め方は `logical-components.md` の 3節（Q2: A）。incomplete は合格に数えない。対象が 0 件なら、確かめの作りの誤りとして失敗にする。
- **U4-NFR5.4**: B3 の試しの記録は「ファイル × 表示の組 × 規則 × 鍵 × 結果の区分」の形にする（L6）。試しの版は統合せず、4.13 に戻したことを `package.json`・`package-lock.json` の差で確かめる。
- **U4-NFR5.5**: jsdom はこの規則を判定できないため、画面の部品のテストでは名前を `getByRole(..., { name })` で確かめる。

## 6. 言語（U4-NFR6.1・U4-NFR6.2）

- `topbar.userMenu`・`useradmin.actions.row`・`*.actions.busySuffix` を日英で用意する。英語の `busySuffix` の先頭の空白が保たれることを単体テストで確かめる。
- 氏名が空（`getInitials` が空文字を返す）ときは、頭文字と区切りを省く。

## 7. テスト（U4-NFR7.4〜7.7）

- **U4-NFR7.4**: `rowActionsName` に fast-check の性質ベースのテストを当て、乱数の種を記録する。
- **U4-NFR7.5**: 書き換えの対象を、名前（「の操作」「のメニュー」「Menu for」）の検索で洗い出す。
- **U4-NFR7.6**: `toHaveAccessibleDescription` の既存のテストが、書き換えずに通ること。
- **U4-NFR7.7**: frontend のカバレッジの下限を保つ。除外は増やさない。

## 上流との差

- **違反の要素の見立て**: 機能設計（`functional-spec.md` の 2節）はトップバーの頭文字と見た。前の Intent の記録（`generation-notes.md` の Step 10）は、Dropdown のトリガー（`dropdown-trigger`）と作業ロールの項目と書いている。
  - 2節の事実（トリガーの `data-testid` の上書き）から、行の操作が無い 080・100・130・160 の `dropdown-trigger` は、トップバーか作業ロールのトリガーであった可能性が高い。機能設計の見立てと前の記録は、どちらも正しかった可能性がある。
  - 前の記録の形（要素の `data-testid`）では区別できなかったため、鍵を変えてから B3 で確かめる。
- **機能設計の D10a の見分け方**: 機能設計は「近い祖先の `data-testid`」と書いた。portal の中の項目では、近い祖先が `dropdown-menu` になり見分けられない。そのため menuOwner を足した（Q1: A）。機能設計の文書は書き換えない。
- **作業ロールのトリガーの名前**: 見える文字は「作業ロール:」と名前の2つの span で、名前（`aria-label`）は「作業ロール: {{name}}」（`frontend/src/app/i18n/messages/ja.ts` 40〜41行）。空白の差が規則に響くかは未確認で、B3 の試しで確かめる。

## 後の段に回すこと

- コード生成の計画（B3）:
  - ログ・`console`・トレースの呼び出しの検索（3節）。
  - 作業ロールのメニューを開いた状態で axe を流す場面（160 の 157〜175行のあたり）があるか。無ければ場面の置き方を決める。
  - B3 の統合の単位（機能設計のレビューの R-03）。
  - R-04 の残り（依頼（5）の送付の有無、英語の `busySuffix` の先頭の空白、空の氏名）。
  - marks に入れるアプリの印のクラスの一覧と、鍵の型の名前。
- コード生成の計画（B7）: トップバーの passes の確かめの置き場（130 か、2語の氏名の利用者で表示する新しい E2E か）。
- 試しで make-you-chic-ui の部品の違反が残れば、新しい依頼の文面を記録に置き、依頼者に諮る（機能設計の 8節）。

## U3 とそろえる点

- 鍵の owner の正規化で、U3 の行の操作 `tables-row-actions-r<n>` を `tables-row-actions-*` にする（U3 の NFR4.21）。
- 記録の形（L6）と、選択子・値を出さないこと（U3 の NFR4.2・NFR5.8）。
- json の報告の秘密の確かめに、U3 の見本の行の値を足す。
- 表示の組 20 組。

## Sources

- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/security-requirements.md`、`tech-stack-decisions.md`
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/nfr-design-questions.md`（Q1・Q2: A、まとめの確認: Looks correct）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-requirements/memory.md`（Open questions。U4 R-01・R-02）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u4-a11y-carryover/00d44a7a2452e6b7/1.json`（R-01〜R-04）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（2節・D1〜D12・D10a〜D10c・8節）、`frontend-components.md`（5節・7節）
- U3: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/security-requirements.md`（NFR4.2・NFR4.21・NFR5.8）
- 前の Intent の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md`（Step 10）
- コード:
  - `frontend/e2e/support/axe.ts`（30行・33〜40行・81〜86行・141行）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（135〜143行・149〜231行）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Topbar.tsx`（64〜95行）
  - `frontend/src/app/work-role/WorkRoleSwitcher.tsx`（20行・46〜53行・156〜166行）
  - `frontend/src/features/useradmin/UserRowActions.tsx`（66〜85行）、`frontend/src/features/groupadmin/GroupRowActions.tsx`（62行）、`frontend/src/features/roleadmin/RoleRowActions.tsx`（59行）
  - `frontend/src/app/i18n/messages/ja.ts`（39〜47行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・学び）

## Assumptions & Open Questions

- 開いているメニューは、axe を流す時点で1つだけと置いた。2つ以上のときは失敗にする（2節）。
- 鍵で5つの区分を区別できることは、B3 の試しの前の単体の確かめで裏付ける。区別できなければ、B7 までに鍵の作り方を直す（規則と要素は外さない）。
- 固定先 `b0a1e79` で Dropdown の項目の DOM が変わっても、`role="menuitem"` と portal の形は保たれると置いた。B3 で固定先を上げた後に確かめる。
