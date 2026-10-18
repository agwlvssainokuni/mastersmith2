# NFR Requirements の質問 — U4 u4-a11y-carryover

U4 はアクセシビリティの持ち越しの単位です。上流は次の2つです。

- 要件の NFR1〜NFR8（`aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`）
- 承認済みの機能設計（`construction/u4-a11y-carryover/functional-design/`）

## 前の段から持ち越した論点

- 機能設計の D10a〜D10c: 規則 `label-content-name-mismatch` の空振りを防ぐ形にした。対象のトリガーが passes に入ることを確かめ、incomplete は合格に数えない。
- 機能設計のレビューのうち後の段に回したもの: R-03 は B3 の統合の単位、R-04 は依頼（5）の送付・英語の空白・空の氏名。どちらもコード生成の計画で決める。
- **前の Intent の記録との食い違い**: 機能設計は、100 件の失敗の主な原因をトップバーの頭文字と見立てた。一方、前の Intent の記録（`aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md` の Step 10、`.github/dependabot.yml` の注記）では、違反の要素は Dropdown のトリガーと作業ロールのメニューの項目で、トップバーは出てこない。この見立ては未確認として要件に書き、B3 の試し（D9）で確かめる。

## 決まっていること（質問にしない）

**合格の条件**
- 規則・要素を外さない（project.md の Forbidden）。
- E2E の全件が通ること。
- E2E と vitest-axe の違反が 0 件であること。
- 対象のトリガーが passes に入ること。トップバーは2語の氏名で確かめる。
- 確かめる要素が 0 件なら失敗とする。
- B7 で `ignore` を外す。

**E2E と記録**
- E2E の時間は、B3・B7・Build and Test で `caffeinate -dims` を付けて測る。前の 232 件・約 10分35秒と並べて記録する（上限は置かない）。
- axe の確かめは、E2E の本数に数えない。
- 表示の組は `displayCombos.ts` の 20 組を、すべて流す。

**依存とライセンス**
- 新しい依存は足さない。
- axe-core のライセンスは MPL-2.0 で、Apache-2.0 と異なる。開発のときだけ使い、成果物に同梱しないので、採用の理由として tech-stack-decisions.md に書く（team.md）。

## Q1. axe-core の版の選び方（B3 の試しと B7）

A. B7 の時点で公開されている 4 系の最新の版（4.14.0 以上）を入れ、lockfile で1つに固定する。B3 の試しはその時点の最新の版で行い、版を記録する。上げる前に、変更の記録（CHANGELOG）を読み、新しく既定で流れる規則を確かめて記録する（推奨）
B. 4.14 系に留める（4.15 以降は、次の Dependabot の知らせとして別に扱う）
X. Other (please specify)

[Answer]: A

## Q2. axe の違反の記録と失敗の文に、axe の選択子（要素を指す文字列）を出すか

今は `data-testid` の無い要素には、選択子を使います。axe は 31 文字未満の `aria-label` も選択子に使うため、「操作: <氏名（メールアドレス）>」や、U3 の行の値が失敗の文や報告に出えます。

A. 違反の記録・失敗の文・注記には、規則・`data-testid`・要素の種類だけを出し、選択子は出さない。B3 の試しの記録も同じ形にする。直すのは E2E の補助だけ（推奨）
B. 今のまま選択子を出す（値は架空の値だけのため）
X. Other (please specify)

[Answer]: A

## Q3. 「axe-core の版が lockfile で1つ」を確かめる範囲

A. `frontend` の lockfile に限る（vitest-axe の分を含めて1つ）。`vendor/make-you-chic-ui` の lockfile にある開発用の axe-core は、版を記録するだけにする。make-you-chic-ui のテスト用で、このリポジトリの CI・E2E・成果物に入らないため（推奨）
B. vendor の lockfile も 4.14 以上にそろうまで、上流に依頼して待つ
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

ID は上流の最後の枝番の次から振り、対応表を冒頭に置く。

**performance**
- NFR1 は N/A。
- 名前の関数は描画の回数と API の呼び出しを増やさない。
- E2E の時間、`./gradlew verify` の時間、CI の時間を B3・B7・Build and Test で記録する。眠りが入った回は流し直す。

**security**
- 名前の値をログ・トレースに出さない。
- axe の記録・失敗の文・注記に選択子と値を出さない（Q2: A）。
- 名前は文字として作る。
- E2E は架空の値だけを使い、json の報告の秘密の確かめを通す。
- 依存の確かめ:
  - axe-core の新しい版に `hasInstallScript` が無いこと。
  - OSV-Scanner の結果を記録する。
  - ビルドした `dist` に axe-core が入らないこと。
- 固定先の手順は FR14.1 と team.md のとおり。

**アクセシビリティ（NFR5）**
- WCAG 2.0・2.1 の A・AA を、20 組のすべてで流す。
- B7 で違反 0 件にし、件数を記録する。
- 行の「操作」とトップバー（2語の氏名）が passes に入ること。0 件は失敗。
- B3 の試しの記録の形は「ファイル × 組 × 規則 × 要素」。違反の要素の見立ての食い違い（前の Intent の記録では Dropdown のトリガーと作業ロールの項目）を確かめる。
- 画面の部品のテストでは、名前を明示して確かめる。

**言語（NFR6）**
- 鍵を日英でそろえる。
- 英語の先頭の空白をテストで確かめる。
- 空の氏名では頭文字と区切りを省く。

**テスト（NFR7）**
- 名前の関数に性質ベースのテストを当てる。
- 書き換えの一覧を、名前の検索で洗い出し直す。
- 既存の説明の確かめが、そのまま通ること。
- frontend のカバレッジの下限を保つ。

**tech-stack**
- axe-core は 4 系の最新（4.14 以上、Q1: A）にし、lockfile で1つにそろえる。範囲は frontend だけ（Q3: A）。
- axe-core は MPL-2.0。開発のときだけ使い成果物に入らないことを、採用の理由として書く。
- vitest-axe・fast-check・Playwright は変えず、新しい依存は無い。

**traceability**
- NFR4〜NFR7 は上の要件に当てる。
- NFR1〜NFR3・NFR8 は N/A。

**U3 とそろえる点**
- 表示の組。
- 4.14 の試しの記録の形。
- passes の確かめの部品を、U3 の業務データの行の操作でも使う。そのため、U3 の行の操作に、主キーを含まない `data-testid` の接頭辞を決める。

Does this all look correct before I generate the NFR requirements?

- Looks correct
- Request changes

[Answer]: Looks correct
