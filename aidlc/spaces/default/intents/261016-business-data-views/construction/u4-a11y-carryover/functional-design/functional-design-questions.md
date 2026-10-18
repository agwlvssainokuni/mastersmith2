# Functional Design の質問 — U4 u4-a11y-carryover

U4 は、前の Intent から持ち越したアクセシビリティの直しを受け持つ単位で、次の4つを扱います。

- make-you-chic-ui の固定先を `b0a1e79` に上げる
- 「操作: …」の名前と、既存の管理の一覧の直し
- トップバーの名前の直し
- axe-core 4.14 への引き上げ

上流は次の文書です（いずれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）。

- `inception/units-generation/unit-of-work.md`
- `inception/requirements-analysis/requirements.md`（FR14）
- `inception/user-stories/stories.md`（US4.1）
- `inception/contract-design/contract-summary.md`（C4）
- `make-you-chic-ui-request-5.md`
- `inception/delivery-planning/bolt-plan.md`（前半を B3、後半を B7 で作る）

## 確かめたこと

- **固定先 `b0a1e79`**: 親は今の固定先 `a9309c4` で、上流の `main` から辿れます。`package.json` と lockfile に差は無く、`hasInstallScript` も増えていません。変わるのは Dropdown の部品だけです。
- **トップバーを直す口**: 今の make-you-chic-ui の Topbar には、メニューの名前を差し替える口（`labels.userMenu(name)`）があります。頭文字を求める関数（`getInitials`）も公開されています。
- **前の Intent の 100 件の失敗の見立て**: axe の規則 `label-content-name-mismatch` は、見えている文字が名前の中に含まれるかだけを見ます。そのため、今の「〇〇の操作」は理屈の上では通ります。前の Intent の 100 件の失敗の主な原因は、すべての画面にあるトップバーの頭文字（2語の氏名で「検太」）と見ています。B3 で 4.14 を当てて確かめます。

## 決まっていること（質問にしない）

- **固定先の更新**: 承認を得た専用のコミットで行い、fast-forward で統合します。前後のハッシュ・辿れること・lockfile の差を記録します（team.md、FR14.1）。
- **規則・要素を外さない**: axe の規則と要素は外しません（project.md の Forbidden）。
- **合格の条件**: axe-core を上げた状態で、E2E の全件が通り、E2E と vitest-axe の違反が 0 件であることを件数で記録します。axe-core の版が lockfile で1つにそろっていることも確かめます。
- **`ignore` を外す**: axe-core を上げたら `.github/dependabot.yml` の `ignore` を外します。持ち越すときは、理由と外す時期を書き直します（FR14.6）。
- **見た目は変えない**: 名前は見えている文字で始め、行を見分ける値を添えます（AC4.1.1）。業務データの一覧の行の名前は U3 が作ります。
- **表の右端の Dropdown**: `placement="bottom-end"` のままにします。
- **成果物と記録の向け先**: ui の単位なので、functional-spec.md・frontend-components.md・traceability.json を作ります。traceability の向け先は、functional-spec の決め（D の番号）と流れの番号です。

## Q1. 利用者の管理の一覧で、「操作」のメニューの名前に添える「行を見分ける値」

今の名前は「佐藤 一郎（sato@example.com）の操作」です。画面イメージの例は「操作: 佐藤 一郎」で、氏名だけでした。グループとロールは名前が重ならないので、名前だけを添えます。

A. 氏名とメールアドレスを添える（「操作: 佐藤 一郎（sato@example.com）」、英語は `Actions: Sato Ichiro (sato@example.com)`）。同じ氏名の利用者がいても見分けられ、今の名前の情報も保てる。画面イメージとの差を記録する（推奨）
B. 画面イメージどおり氏名だけを添える（同じ氏名の行は名前で見分けられない）
X. Other (please specify)

[Answer]: A

## Q2. トップバーの利用者のメニューのトリガー（頭文字の丸）の名前の直し方

今は見えている文字が「検太」なのに、読み上げの名前は「検査 太郎のメニュー」です。名前に「検太」が含まれないため、axe-core 4.14 の規則で違反になります。

A. frontend の側で直す。名前を「検太: 検査 太郎のメニュー」の形にし（英語は `TK: Menu for Taro Kensa`）、頭文字は make-you-chic-ui の `getInitials` で求めて丸の文字とそろえる。依頼（5）からトップバーの節を外し、表の部品の2点だけを機能設計の承認の前に送る。B3 で 4.14 を当ててまだ違反が出れば、改めて依頼する。上流を待たずに済む（推奨）
B. 依頼（5）で上流に頼み、取り込んでから直す（axe-core を上げられるのが上流の対応の後になる）
X. Other (please specify)

[Answer]: A

## Q3. U4 を前半（B3）と後半（B7）にどう分けるか

A. 承認済みの Bolt の計画どおりに分ける。
- B3:
  - 固定先の更新
  - 名前の関数
  - 3つの管理の一覧とトップバーの名前の直し（テストと E2E の書き換えを含む）
  - axe-core 4.14 を手元で当てて違反の一覧を記録する（統合はしない）
- B7:
  - axe-core を上げ、`ignore` を外す
  - E2E の検査の必ず流す規則に `label-content-name-mismatch` を足す
  - 新しい画面を含めて、違反 0 件を件数で記録する

（推奨）
B. B3 で既存の画面の違反が 0 件になったら、B3 のうちに axe-core を上げて統合する。U3 の画面を初めから 4.14 の下で作れるが、承認済みの計画との差になる。0 件にできなければ A に戻る
X. Other (please specify)

[Answer]: A

## Q4. 依頼（5）（表の部品の2点）の上流の対応が B7 までに間に合わないとき

A. axe-core の引き上げは止めない。表の横のスクロールの枠と件数の選択は、U3 が frontend の側で手当てして満たすので、違反が 0 件なら B7 で上げる。上流が間に合えば、固定先をもう一度上げて、手当てを部品の口に置き換える。間に合わなければ手当てのまま残し、置き換えを次の Intent に回したことを記録する（推奨）
B. 上流の対応を待つ。間に合わなければ、axe-core の引き上げごと次の Intent へ持ち越す（FR14.6）
X. Other (please specify)

[Answer]: A

## Q5. 行の「操作」以外の名前も、「見えている文字で始まる形」にそろえるか

既存の画面には、見えている文字が名前の途中や後ろにあるものがあります。例は「営業を開く」（見えている文字は「開く」）です。axe の規則は「含まれるか」だけを見るので、これらは違反になりません。

A. そろえない。直すのは、要件 FR14.2 の行の「操作」と、axe-core 4.14 で違反と出たものだけ。変更とテストの書き換えを小さく保てる（推奨）
B. 名前を付けているボタン・リンクを、すべて「見えている文字: 対象」の形にそろえる（ほぼすべての画面に広がる）
X. Other (please specify)

[Answer]: A

## Requested Changes Feedback

承認の場（2026-10-18）の Request Changes で、次の直しを決めた（Major と、単位の継ぎ目に関わる Minor）。

- R-01（Major）規則の空振り: `label-content-name-mismatch` は、必ず流す規則に足すだけでなく、行の操作のトリガーとトップバーのトリガーがこの規則の `passes` に入っていることを E2E で確かめる（判定できない `incomplete` は合格に数えない）。トップバーは2語の氏名（頭文字が2文字）の利用者で確かめる。
- そのほかの Minor（R-02 は U3 の Q6: A で B5・B6 の終わりに試すことになった。R-03 B3 の統合の単位、R-04 依頼（5）の送付・英語の空白・氏名が空のとき）は、この単位の記録に残し、コード生成の計画で拾う。

## Consolidated Summary Confirmation

- **名前の関数**: `src/shared/` に、「`<見えている文字>: <行を見分ける値>`」を返す純粋な関数を置く。見えている文字には、ボタンの文言と同じ鍵を渡す。処理中は「（処理中）」を後ろに足す。
- **既存の管理の一覧**: 利用者は「操作: 氏名（メールアドレス）」（Q1: A）、グループとロールは「操作: 名前」にする。部品の形・Dropdown・`bottom-end`・`data-testid` は変えず、名前だけを差し替える。
- **トップバー**: frontend の側で「頭文字: 氏名のメニュー」の形にする。頭文字は `getInitials` で求める（Q2: A）。依頼（5）からトップバーの節を外し、表の部品の2点だけを機能設計の承認の前に送る。
- **B3（前半）**（Q3: A）:
  - 固定先 `b0a1e79` への更新（専用のコミット、fast-forward）
  - 名前の直しと、テスト・E2E の書き換え
  - axe-core 4.14 を手元で当てて、違反の一覧を記録する（統合しない）
- **B7（後半）**（Q3: A）:
  - axe-core を上げ、`ignore` を外す
  - E2E の必ず流す規則に `label-content-name-mismatch` を足す
  - 新しい画面を含めて、違反 0 件と、axe-core の版が1つにそろっていることを記録する
- **依頼（5）の遅れ**: 上流が間に合わなくても、axe-core の引き上げは止めない。表の部品は U3 の手当てで満たし、上流が間に合えば部品の口に置き換える（Q4: A）。
- **名前の直しの範囲**: FR14.2 の行の「操作」と、4.14 で違反と出たものだけ（Q5: A）。
- **画面のテストの確かめ方**: jsdom ではこの規則が判定できないため、画面のテストでは名前を明示して確かめる。名前の関数には、fast-check の性質ベースのテストを当てる。
- **受け入れ基準**: AC4.1.1（管理の一覧の部分）・AC4.1.2〜AC4.1.5 を受け持つ。AC4.1.1 の業務データの一覧の部分と AC4.1.6 は U3 が主で、U4 では受け持たないものとして記録する（Deferred）。
- **成果物**: functional-spec.md（決め D1〜D12、流れ F1〜F6）・frontend-components.md・traceability.json。

- 承認の場の直し（Requested Changes Feedback のとおり）:
  - R-01: この規則の `passes` に対象のトリガーが入ることを E2E で確かめる（incomplete は合格にしない）

Does this all look correct before I generate the functional design?

- Looks correct
- Request changes

[Answer]: Looks correct
