# コードの要約 — U4 u4-a11y-carryover（Bolt B3）

承認済みの計画（`code-generation-plan.md`）の Step 1〜21 を行った。Step 22（コミット）と Step 23（統合）は、指揮役が依頼者の承認を得て行う。作業ブランチは `feature/261016-business-data-views-b3`（`develop` の `1913503` から作った）。計画に無い変更と判断の詳細は `generation-notes.md`（N-1〜N-10）にあり、ここではその要点を書く。

## 1. 作ったもの・変えたもの

作った・変えたアプリのソースは `source-manifest.json` に 35 のパスで並べた（消したものは無い）。

| 場所 | 中身 |
|---|---|
| `vendor/make-you-chic-ui`（固定先） | `a9309c4ee863b162236571a7d6300c62fd4b4f70` → `b0a1e79ef4f78416d37f78e75dfc6da3fc4b19df`（中身は変えていない。上流の `main` の先頭で、辿れることを fetch の後に確かめた） |
| `frontend/src/shared/row-actions/` | L1 名前の関数 `rowActionsName`（新）と例のテスト・性質ベースのテスト |
| `frontend/src/features/{useradmin,groupadmin,roleadmin}/` | `messages.ts` の鍵（`useradmin.actions.row`・`*.actions.busySuffix` を足し、`*.actions.name`・`*.actions.busyName` を消した）、`*RowActions.tsx` のトリガーの名前を「操作: <行を見分ける値>」に。部品の形・Dropdown・`placement`・`data-testid`・見える文字・props・状態は変えていない。部品とページのテスト |
| `frontend/src/app/layout/ShellLayout.tsx`・`frontend/src/app/i18n/messages/{ja,en}.ts` | L2 トップバーの利用者のメニューの名前「<頭文字>: <氏名>のメニュー」・「<頭文字>: Menu for <氏名>」。頭文字が空なら `topbar.userMenuWithoutInitials`。`ShellLayout.test.tsx` |
| `frontend/e2e/support/` | L3 `axeElementKey.ts`（新）、L5・L6 `axeRuleChecks.ts`（新）とそれぞれの単体のテスト、L4 `axe.ts`（`target` を除き鍵に置き換え、`labelContentNameNodes` を足し、注記 `axe-nodes` を毎回残す）、`secretValues.ts`（見本の氏名とメールアドレス） |
| `frontend/e2e/110`・`135`・`140`・`160` | 名前の照合の書き換え（110・140・160）と、失敗の文を `規則 + 鍵` に（135・160） |
| `frontend/vitest.config.ts` | `e2e/support/**/*.test.ts` を対象に足し、除外を `e2e/**/*.e2e.ts` に。カバレッジの対象と下限は変えていない |

触れていないもの: `frontend/package.json`・`frontend/package-lock.json`（4.14 の試しは戻した）、`.github/dependabot.yml`、`frontend/playwright.config.ts`、`README.md`、バックエンドのすべて、`REQUIRED_RULES`・`KNOWN_VIOLATIONS`・`WCAG_TAGS`・`missingRequiredRules`、配備した環境（アプリ・見本の PG・Mailpit とボリューム）。

## 2. 主な決定

- 行の操作の名前は見える文字で始め、半角のコロンと空白の後に行を見分ける値を続ける。処理中の文字は呼ぶ側が後ろに足す（英語は先頭の空白つき）。名前は文字列として `aria-label` にだけ渡し、ログ・`console`・トレースには出さない（5つのファイルで検索して 0 件）。
- トップバーの名前は `make-you-chic-ui` の `getInitials` で求めた頭文字で始める（Avatar の丸の中の文字と同じ）。状態・効果・API の呼び出しは足していない。
- E2E の axe の結果は、選択子（`target`）を Node の側へ返さず、鍵（element・owner・marks・menuOwner・path）で返す。鍵の関数は自己完結の関数で、`runAxe` が `toString()` を `page.evaluate` で `window` に置いてから、検査と同じ1回の `page.evaluate` の中で呼ぶ（`new Function` は使わない）。
- menuOwner は `aria-haspopup` と `aria-expanded="true"` の両方を持つ Dropdown のトリガーだけから作り、2つ以上開いていれば誤りの印にする（R-01）。owner の末尾は数字だけ・`r<数字>` だけを `-*` にする（R-03）。`data-testid` の無い要素には path を付ける（R-04）。
- 区分 C1〜C7 の当てはめと passes の確かめ（`labelContentNameProblems`）は Playwright にも Vitest にも頼らない純粋な関数にし、単体で確かめた。E2E の合否にはまだ入れていない（B7 で足す）。

## 3. テストとカバレッジ

| 確かめ | 結果 |
|---|---|
| 走らせ方の確かめ（Step 1、変更の前） | 4 ファイル・58 件が通過 |
| 固定先の後の既存のテスト（Step 4、書き換えなし） | 6 ファイル・62 件が通過 |
| この単位のテスト（Step 6・8・10・12） | L1 10 件、行の操作とページ 10 ファイル・149 件、トップバー 44 件、E2E の補助 16 件が通過 |
| `vitest list` の数 | 1508 → 1552（足した 44 件。`*.e2e.ts` は 0） |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（Step 15） | BUILD SUCCESSFUL、17分37秒。Vitest 188 ファイル・1633 件が通過 |
| フロントエンドのカバレッジ（全体の合計） | 行 95.37%・分岐 90.34%（下限 80%・70%）。`rowActionsName.ts` は行・分岐とも 100%。報告に `e2e/` のファイルは 0 |
| `dist` の検索（Step 16） | axe-core の3語と `axeElementKey`・`labelContentNameProblems` が 0 件（陽性の対照の `axe.min.js` では 9・9・1 件） |
| E2E の全件（Step 17、4.13） | 232 件すべて通過、10分43秒（基準 約 10分35秒）。報告の秘密の確かめは見つかった件数 0 |
| 4.14.0 の試し（Step 19、統合しない） | 232 件すべて通過、10分51秒、**どの規則の違反も 0 件**。区分の結果は 4節 |

いずれの長いテストも `caffeinate -dims` で包み、流した後の `pmset -g log` に Sleep・DarkWake は無かった。

## 4. axe-core 4.14.0 の試しの結果（Step 19、L6 の形の要約）

| 区分 | passes | incomplete | violations |
|---|---|---|---|
| C1 利用者の行の操作のトリガー | 882 | 0 | 0 |
| C2 グループの行の操作のトリガー | 0 | 0 | 0（場面が無い） |
| C3 ロールの行の操作のトリガー | 129 | 0 | 0 |
| C4 トップバーのトリガー | 180 | 638 | 0 |
| C5 作業ロールのトリガー | 818 | 0 | 0 |
| C6 作業ロールの項目（説明つきを含む） | 176 | 0 | 0 |
| C7 トップバーの項目 | 0 | 0 | 0（場面が無い） |

- C4 の incomplete は1語の氏名の場面（機能設計の D10c の見込みどおり）で、2語の氏名の場面（130 など）では passes。
- 違反が無かったため、D6: A の「止めて諮る」には当たらず、B3 で直すものは無い。試しの差分（版と `expect.soft`）は戻し、`package.json`・`package-lock.json` の差なし、作業フォルダの状態が試しの前と同じことを確かめた。
- ファイルごとの表と、区分に当たらない incomplete（表の見出しの中のボタン・木の開閉のボタン、計 113 件。違反ではない）は `generation-notes.md` の Step 19。

## 5. 計画との差

`generation-notes.md` の 2節の要点:

- **N-1**: Step 1 の `aidlc/` の未コミットの変更は監査の記録の追記だけ（この会話の中でフックが書き続けるもの）で、コミットせずに進めた。
- **N-2**: marks は「自分のクラスのうち `mycui-` で始まるものと印の一覧に当たるもの」に「祖先のクラスのうち印の一覧に当たるもの」を足した集合にした（一覧の3つはトリガーを包む要素のクラスのため）。
- **N-3**: menuOwner の形は `<owner か -> .<mark>…` の文字列。2つ以上開いていれば `!multiple-open-triggers`、開いたトリガーが無ければ null。
- **N-4**: C1〜C3 は、要素が role の無い `button` で包む要素の印を持つことを条件にした（グループとロールの行の「開く」のリンクを除くため）。要素を引けない結果は `UNRESOLVED_KEY`。
- **N-5**: `splitKnownViolations` は鍵の owner（自分か祖先の `data-testid`）で照らすため、前の形より広く当たりうる。`KNOWN_VIOLATIONS` は空のまま。
- **N-6**: 評価し直しの確かめは `node:vm` の `runInNewContext` で行った（`eval`・`new Function` は使わない）。
- **N-7**: `splitKnownViolations` の確かめは `axe.ts` を Vitest から直接読んで行った（読み込めた）。
- **N-8**: 部品のテストの件数が目安と少し違う（`UserRowActions` 11・`GroupRowActions` 9・`RoleRowActions` 9・`ShellLayout` の足し 4）。
- **N-9**: 測りの見本の生成の値（`測定 NN`・`sokutei.NN@example.com`）は `SECRET_FIXED_VALUES` に足していない。
- **N-10**: 4.14.0 の下の `npm test` の1回目で `src/eslintImportRules.test.ts` がファイルの読み込みで落ち、単独と全件の2回目では通った（再現しない。axe-core と、この単位の変更に関わらないファイル）。見立てを記録して進めた。

## 6. Bolt の計画との差

- `bolt-plan.md` は U4 を B3（前半）と B7（後半）に分けていたが、依頼者の決定で B7（axe-core 4.14 以降の引き上げ・`.github/dependabot.yml` の `ignore` の外し・`REQUIRED_RULES` の足し・E2E の passes の確かめ・依頼（5）の取り込み）を U3 のコード生成の計画の最後の Bolt に移した（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations 2026-10-19T06:20:00Z）。そのため、`traceability.json` で B7 にあたる ID（AC4.1.3・AC4.1.5・AC4.1.6・NFR4.8・NFR5.1〜5.3）は U3 u3-businessdata-screens への Deferred にし、NFR7.2・7.3 は Build and Test への Deferred にした（計画の 7節）。B7 へ引き継ぐことは `generation-notes.md` の 4節。
- 統合の形は計画のとおり 1本のブランチで C1〜C5 を積んで fast-forward（D1: A）。

## 7. 依頼者に確かめたいこと

- **Q-1（N-10）**: 4.14.0 の試しの中の `npm test` の1回目で `src/eslintImportRules.test.ts` が1度落ち、再現しなかった。team.md の「手元で再現しないもの」として記録して進めたが、この扱いでよいか。
- **Q-2（試しの区分）**: C2（グループの行の操作のトリガー）と C7（トップバーの項目）は、axe を流す場面が今の E2E に無く、要素が見つからなかった。B7 の必須の対象をどうするか（場面を足して対象に入れるか、理由を書いて外すか）を、B7 の計画で決める扱いでよいか。
- **Q-3（Step 19 の気づき）**: 鍵の owner は `data-testid` の末尾の数字だけを `-*` にするため、`dsl-diff-toggle-<スキーマ>/<テーブル>` のように名前を持つ `data-testid` は名前のまま注記 `axe-nodes` に残る（今は見本の DSL の名前で、個人に関する値ではない）。U3 の業務データの画面で行の値を `data-testid` に入れないことを U3 の計画の前提にしてよいか。
- **Q-4（N-2〜N-5）**: 鍵の作り方の細部（marks に祖先の印を入れる、menuOwner の文字列の形、C1〜C3 の条件、既知の違反を owner で照らす）を、計画の範囲の判断として受け入れてよいか。
- **Q-5（Step 1・N-1）**: `aidlc/` の監査の記録の追記は未コミットのまま。統合の前に記録のコミットとして積む必要がある。
