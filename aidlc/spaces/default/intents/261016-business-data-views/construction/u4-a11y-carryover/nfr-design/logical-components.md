# 論理の部品 — U4 u4-a11y-carryover

この文書は、U4 の NFR の設計がどの部品に当たるかをまとめる。部品は L1〜L6 の6つで、passes の確かめの対象の決め方（Q2: A）もここに書く。文章で ID を指すときは、単位の接頭辞を付ける（例 `U4-NFR5.3`）。

## 冒頭の対応表

| 部品 | 置き場 | 受け持つ NFR 要件 | 成果物（`dist`）に入るか |
|---|---|---|---|
| L1 名前の関数 `rowActionsName` | `frontend/src/shared/row-actions/` | U4-NFR4.2・4.6・7.1・7.4 | 入る（アプリのコード） |
| L2 トップバーの名前 | `frontend/src/app/layout/ShellLayout.tsx` の `topbarLabels.userMenu` | U4-NFR4.2・6.1・6.2・7.1 | 入る（アプリのコード） |
| L3 要素の鍵の関数 | `frontend/e2e/support/axe.ts` の中（`page.evaluate` の中で動く） | U4-NFR4.5・5.4 | 入らない |
| L4 区分の要約 | `axe.ts` の `AxeSummary` に足す（例 `labelContentNameNodes`） | U4-NFR4.5・5.3 | 入らない |
| L5 passes の確かめ | `axe.ts` の `expectLabelContentNamePasses(summary, matcher)`（名前はコード生成で確定） | U4-NFR5.2・5.3 | 入らない |
| L6 試しの記録の形 | コード生成の記録（B3 の試し） | U4-NFR5.4・4.5 | 入らない |

## 1. 部品ごとの役割と境界

- **L1**: 純粋な関数で、`"<見える文字>: <行を見分ける値>"` を返す。ログを書かない。U4 の3つの行の操作の部品と、U3 の業務データの行の操作が使う。
- **L2**: `getInitials` で頭文字を求め、鍵 `topbar.userMenu` に渡す。氏名が空なら、頭文字と区切りを省く。
- **L3**: DOM の要素から鍵（element・owner・marks・menuOwner）を作る。鍵の作り方は `security-design.md` の 2節。選択子と値を戻り値に入れない。
- **L4**: 規則 `label-content-name-mismatch` の結果の要素を、区分（passes・incomplete・violations）と鍵の組で返す。既存の `violations` の要素も、`target` の代わりに鍵を持つ。
- **L5**: 区分ごとに、passes に1件以上あり、incomplete と violations に0件であることを確かめる。対象の区分の要素が見つからなければ、確かめの作りの誤りとして失敗にする。
- **L6**: 「ファイル × 表示の組 × 規則 × 鍵 × 結果の区分」の表。U3 の B5・B6 の試しも同じ形で記録する。

失敗の及ぶ範囲:

- L3〜L6 は E2E の補助と記録の中だけにある。誤りがあっても、E2E の確かめが落ちるか、取りこぼすかにとどまる。
- 取りこぼしは、L5 の「0 件は失敗」で防ぐ。
- L1・L2 の誤りは読み上げの名前に出る。画面の部品のテスト（`getByRole(..., { name })`）と、E2E の passes の確かめで拾う。

共有するもの:

- L1 と L5 は U3 と共有する。
- 表示の組（`frontend/e2e/support/displayCombos.ts` の 20 組）は既存のものを使う。

## 2. 既存の部品への変更

- `AxeViolationNode` から `target` を除き、鍵に置き換える（`security-design.md` の 1節）。
- `splitKnownViolations` の失敗の文は、選択子ではなく `規則 + 鍵` にする。`KNOWN_VIOLATIONS` は空のまま、仕組みも変えない。
- `REQUIRED_RULES` には B7 で `label-content-name-mismatch` を足す。`missingRequiredRules` は変えない。規則の空振りは、L5 が受け持つ。

## 3. passes の確かめの対象の決め方（Q2: A、レビューの R-01）

### 候補の7区分

| # | 区分 | 鍵 | 確かめる E2E の候補 |
|---|---|---|---|
| C1 | 利用者の行の操作のトリガー | owner `useradmin-row-actions-*` | 120 |
| C2 | グループの行の操作のトリガー | owner `groupadmin-row-actions-*` | 140 |
| C3 | ロールの行の操作のトリガー | owner `roleadmin-row-actions-*` | 140 |
| C4 | トップバーのトリガー | marks `mycui-topbar-user-trigger` | 130 か新しい E2E（2語の氏名、D10c） |
| C5 | 作業ロールのトリガー | owner `work-role-switcher` | 160 |
| C6 | 作業ロールの項目 | menuOwner が C5 | 160（メニューを開いた場面） |
| C7 | トップバーの項目 | menuOwner が C4 | 開いた場面がある E2E |

- U3 の行の操作（`tables-row-actions-*`）と行のリンク（「詳細: …」）は、U3 の B5・B6 の試しの結果で候補に足す。

### 手順

1. **B3 の試し**: axe-core 4.14 を作業フォルダでだけ当て、E2E の全件を流す。区分ごとに passes・incomplete・violations の件数を L6 の形で記録する。どの区分の要素も見つからなかった場面も記録する。
2. **B7 の対象の確定**: 試しで要素が見つかった区分を、すべて必須の対象にする。
   - passes に入った区分も violations に入った区分も含める。violations の区分は、直した後に passes に入ることを求める。
   - 見つからなかった区分は、理由（その場面でメニューを開いていないなど）を記録に書いて対象から外す。
   - 対象の一覧は、コード生成の計画（B7）で確定し、承認を得る。
3. **件数の下限**: 確かめる場面ごとに、対象の区分が passes に1件以上、incomplete と violations に0件。incomplete は合格に数えない（D10b）。

### 1語の氏名の扱い

- トップバーのトリガー（C4）の確かめは、2語の氏名（頭文字が2文字）の利用者で行う。
- 1語の氏名では規則が incomplete になる。その結果は記録するが、合格の根拠に使わない（D10c）。

## 上流との差

- **違反の要素の見立て**: 機能設計（`functional-spec.md` の 2節）はトップバーの頭文字と見た。前の Intent の記録（`generation-notes.md` の Step 10）は、Dropdown のトリガー（`dropdown-trigger`）と作業ロールの項目と書いている。
  - Dropdown はトリガーの `data-testid` を `dropdown-trigger` に上書きする（`Dropdown.tsx` 135〜143行）。そのため、行の操作が無い画面の `dropdown-trigger` は、トップバーか作業ロールのトリガーであった可能性が高い。両方の見立てが正しかった可能性がある。
  - そのため、候補を機能設計の2つ（C1〜C4）から、7区分に広げた（Q2: A）。
- **まとめの確認の文言**: まとめの確認には「どれも E2E の補助の中だけで使い、成果物には入れない」とある。実際には、L1・L2 は承認済みの機能設計（D1・D6）のとおりアプリのコードで、成果物に入る。成果物に入らないのは L3〜L6 だけで、この文書は機能設計を正として書いた（project.md の学び）。
- **D10a の見分け方**: portal の中の項目のため、menuOwner を足した（`security-design.md` の上流との差）。

## 後の段に回すこと

- コード生成の計画（B3）:
  - ログ・`console`・トレースの呼び出しの検索（L1・L2 と3つの行の操作の部品）。
  - 作業ロールのメニューを開いた場面（C6）があるか。160 の 157〜175行のあたりを確かめる。トップバーのメニューを開いた場面（C7）も同じ。
  - B3 の統合の単位（機能設計のレビューの R-03）。
  - R-04 の残り（依頼（5）の送付の有無、英語の `busySuffix` の先頭の空白、空の氏名）。
- コード生成の計画（B7）: 対象の区分の確定と、C4 の確かめの置き場。新しいファイルにするときは、U3 の 180・185 と番号が重ならないようにする。

## U3 とそろえる点

- L1（`rowActionsName`）と L5（passes の確かめ）を共有する。
- U3 の行の操作の鍵の owner は `tables-row-actions-*`（U3 の NFR4.21）。行のリンクの区分の鍵は、U3 のコード生成で決めてもらう。
- U3 の B5・B6 の試しは L6 の形で記録し、区分の表を B7 の対象の確定に入れる。

## Sources

- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/security-requirements.md`（U4-NFR5.3・5.4）、`performance-requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/nfr-design-questions.md`（Q1・Q2: A、まとめの確認: Looks correct）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-requirements/memory.md`（Open questions）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u4-a11y-carryover/00d44a7a2452e6b7/1.json`（R-01・R-02）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（D1・D6・D10a〜D10c）、`frontend-components.md`（7節）
- U3: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/security-requirements.md`（NFR4.21・NFR5.8）
- 前の Intent の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md`（Step 10）
- コード: `frontend/e2e/support/axe.ts`、`frontend/e2e/support/displayCombos.ts`、`frontend/e2e/160-app-frame-accessibility.e2e.ts`（157〜175行）、`frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`（53行）、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Topbar.tsx`、`frontend/src/app/work-role/WorkRoleSwitcher.tsx`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（Forbidden・学び）

## Assumptions & Open Questions

- C6・C7 は、メニューを開いた状態で axe を流す場面があるときだけ候補になる。場面の有無はコード生成で確かめる。
- U3 の候補（行の操作・行のリンク）は、U3 の B5・B6 の試しの結果を待って足す。
