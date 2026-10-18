# 性能の設計 — U4 u4-a11y-carryover

U4 は種類 `ui` の単位で、API も新しい画面も持たない。性能の設計の対象は2つ。

- 名前を作ることで描画と API の呼び出しを増やさないこと
- 検査（E2E・`./gradlew verify`・CI）の時間を記録し、伸びに気づけること

文章で ID を指すときは、単位の接頭辞を付ける（例 `U4-NFR7.2`）。ほかの単位の同じ番号と重なるため（NFR 要件の段の承認の場の決定）。

## 冒頭の対応表

| NFR 要件の ID | 設計の節 | 要点 |
|---|---|---|
| U4-NFR7.1 | 1節 | 純粋な関数でメモ化しない。props・状態・API の呼び出しを変えない |
| U4-NFR7.2 | 3節 | E2E の時間の記録。基準の 1.5 倍を合否に使わない目安にする（Q3: A） |
| U4-NFR7.3 | 4節 | verify と CI の時間を、実測の値だけで記録する |
| （U4-NFR5.3 の作り） | 2節 | 区分ごとの要約は、axe の1回の検査の中で作る |

## 1. 名前の関数と部品（U4-NFR7.1）

- `rowActionsName(visibleLabel, rowLabel)` は純粋な関数で、文字列をつなぐだけにする（`logical-components.md` の L1）。計算が軽いため、メモ化（`useMemo`）しない。依存の配列を誤って古い名前が残る危険の方が大きいため。
- `UserRowActions`・`GroupRowActions`・`RoleRowActions` と ShellLayout の props と状態は変えない。名前のために状態・効果（`useEffect`）・API の呼び出しを足さない。
- トップバーの頭文字は `getInitials(user.name)` を描画の中で求める。Avatar と同じ関数で、丸の中の文字とずれない。
- 確かめ方: 画面の部品のテストで、一覧の API の呼び出しの回数が名前の変更の前と同じであることを確かめる（Code Generation の B3）。

## 2. axe の検査の中の要約（U4-NFR5.3 を支える作り）

- 規則 `label-content-name-mismatch` の結果を区分（passes・incomplete・violations）ごとに要約する処理と、要素の鍵を作る処理（`security-design.md` 2節）は、`runAxe` の1回の `page.evaluate` の中で行う。
- 画面を開き直さない。axe を流し直さない。ブラウザと Node の行き来も増やさない。
- 既存の axe の E2E（120・130・140・160 など）の中で、返った要約をもう一度読むだけにする。そのため、E2E の本数も検査の回数も増えない。

## 3. E2E の時間（U4-NFR7.2、Q3: A）

- 基準: 前の Intent の `./gradlew e2eTest` は 232 件 passed・21:28:43Z〜21:39:18Z（約 10分35秒）。出典は `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md` の 22行で、この段で確かめた（レビューの R-05）。
- 測る時点: B3・B7・Build and Test。`caffeinate -dims` を付けて流す。流した後の `pmset -g log` に Sleep・DarkWake が入っていたら、結果を使わずに流し直す。
- 記録すること: 件数・始めと終わりの時刻・眠りの有無。件数が増えた分は、1件あたりの時間（基準は約 2.74 秒）も並べる。
- 目安: 基準の 1.5 倍（約 15分53秒）を超えたら、ファイルごとの時間を記録して原因を見立て、依頼者に諮る。
  - この目安は合否には使わない。E2E は CI の外にあり、team.md の CI の 42 分の線は当たらない。
  - 伸びの原因には、axe-core の版を上げたことと、U3 の画面を足したこと（B5・B6）がありうる。
- 各 E2E の時間の上限（`frontend/playwright.config.ts` の 120 秒）は、原因を確かめずに延ばさない（team.md）。

## 4. verify と CI の時間（U4-NFR7.3）

- B3・B7 の後に、`./gradlew verify` の時間と CI の時間を記録する。CI の時間は、試行ごとの結果も確かめる（project.md）。
- 件数とカバレッジは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直した実測だけを書く（project.md）。
- CI の時間が 42 分を超えたら、テストの分け方を依頼者に諮る（team.md）。

## 上流との差

- **違反の要素の見立て**: 機能設計（`functional-spec.md` の 2節）は、前の Intent の 100 件の主な原因をトップバーの頭文字と見た。前の Intent の記録（`generation-notes.md` の Step 10）は、違反の要素を Dropdown のトリガー（`dropdown-trigger`）と作業ロールの項目と書いている。
  - この段で、Dropdown がトリガーの `data-testid` を必ず `dropdown-trigger` に上書きすることを確かめた（固定先 `a9309c4` の `Dropdown.tsx` 135〜143行）。
  - そのため、行の操作が無い 080・100・130・160 の `dropdown-trigger` は、トップバーか作業ロールのトリガーであった可能性が高い。機能設計の見立てと前の記録は、どちらも正しかった可能性がある。
  - 性能の設計には響かない。確かめは B3 の試しで行う（`security-design.md`・`logical-components.md`）。
- **E2E の時間の目安**: NFR 要件の U4-NFR7.2 は「上限は置かない」とした。この段では、合否に使わない目安（1.5 倍）を足した（Q3: A）。上限は置いていないため、NFR 要件の文書は書き換えない。

## 後の段に回すこと

- コード生成の計画（B3）:
  - 3つの行の操作の部品・ShellLayout・`rowActionsName` に、ログ・`console`・トレースの呼び出しが無いことを、コードの検索で洗い出す（`security-design.md` 3節）。
  - 作業ロールのメニューを開いた状態で axe を流す場面（160 の 157〜175行のあたり）があるかを確かめる。無ければ、場面の置き方を計画で決める。
  - B3 の統合の単位（固定先の更新だけを先に統合するか、まとめて fast-forward にするか。機能設計のレビューの R-03）。
  - 機能設計のレビューの R-04 の残り: 依頼（5）の送付の有無、英語の `busySuffix` の先頭の空白の単体テスト、氏名が空のときの扱い。
- U4-NFR7.1〜7.3 の測りと記録は、Code Generation（B3・B7）と Build and Test で行う。

## U3 とそろえる点

- U3 の画面を足した後の E2E の時間は、B7 と Build and Test の U4-NFR7.2 で一緒に測る。U3 の行の操作も、同じ `rowActionsName`（L1）を使う。
- U3 の B5・B6 の終わりの 4.14 の試しの時間も記録し、B7 の値と並べる。

## Sources

- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/performance-requirements.md`（U4-NFR7.1〜7.3）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/nfr-design-questions.md`（Q3: A、まとめの確認: Looks correct）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u4-a11y-carryover/00d44a7a2452e6b7/1.json`（R-05）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（2節・D1・D5）
- 基準: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md`（22行）
- 前の Intent の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md`（Step 10）
- コード: `frontend/e2e/support/axe.ts`（71〜101行）、`frontend/playwright.config.ts`、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（135〜143行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（Testing Posture・Deployment の学び）

## Assumptions & Open Questions

- 1.5 倍の目安は、前の Intent の件数と環境（colima の VM、同じ PC）を前提にする。環境が変わったときは、基準を測り直して記録する。
