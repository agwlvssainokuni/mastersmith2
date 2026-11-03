## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/tech-stack-decisions.md > 1. 技術の選択 > SVG の中身の検査（および security-requirements.md の NFR1.1.1・NFR1.1.5 の悪い例） | 検査は「開きタグを字句として切り出す」とだけ書かれ、抜け道への耐性が決まっていない。大文字小文字（`<SCRIPT>`・`ONLOAD`）、名前空間の接頭辞（`<svg:script>`・`xlink:` 以外の接頭辞）、コメントと CDATA の中の `<` や `>`、属性値の中の `>`、処理命令（`<?xml-stylesheet href=...?>` は外部の読み込みになりうる）、文字参照で崩した値（`&#x6A;avascript:`）、`data:` の URI の扱いが書かれていない。悪い例の一覧（`<script>`・`onload`・`href`・`url(http)`・`@import`・`foreignObject`・`DOCTYPE`）にもこれらが無い。許可リスト方式なので多くは落ちるが、切り出しがずれると要素が見過ごされる。ライセンスヘッダーのコメント自体を読み飛ばす扱いも未記載 | 字句の切り出しの決まりを tech-stack-decisions.md に足す。要素名・属性名は大文字小文字を区別して許可リストと照らし、接頭辞付きの名前は `xmlns` 以外を拒否する。処理命令・CDATA・`<!ENTITY` を拒否し、コメントは先頭のヘッダーだけを許す（またはコメントの中を無視する根拠を書く）。属性値は引用符の対応で読み、値の中の `>` で切れないことを決める。NFR1.1.5 の悪い例に、これらの変形（大文字・接頭辞・処理命令・CDATA・`data:`・文字参照・属性値の中の `>`）を足し、fast-check の性質に「変形しても落ちる」を入れる | New |
| R-02 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/security-requirements.md > NFR1.1.4 の確かめ方と NFR1.1.5 | `/favicon.ico` の結合テストが、どの静的なファイルを配る環境で動くか決まっていない。Spring のテストのクラスパスに `frontend/dist` が入らなければ、テストは本物の素材と SPA のフォールバックを通らず、K-102 の確かめにならない。NFR1.1.4 は「`dist` に素材が入ること（NFR1.1.5）」と参照するが、NFR1.1.5 の（a）〜（d）は `public/` のファイルを読むだけで、`dist` や WAR の中身は見ない。配備の後のスモークテストにも `/favicon.ico` の確かめが無い（キャッシュの見え方だけ） | NFR1.1.4 に、確かめる対象（`dist` を取り込んだクラスパスか、組み上がった WAR を起動したもの）を明記する。`dist` と WAR に素材が入ることを確かめる項目を別に立てる。`/favicon.ico` と SVG の favicon の確認を、配備の後のスモークテストの項目に足す。成り立たなかった場合の戻り先（上流との差の節に書いた設計に戻す手順）を、確かめる段と持ち主つきで Assumptions に移す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/traceability.json > coverage NFR2.1 | 上流の NFR2.1 は「問い合わせの数を増やさない・性能の目標を変えない」で、この単位には当たらない（security-requirements.md も「性能の目標が当たらない」と書く）。それなのに OK にして、別の意味の素材の大きさの上限（NFR2.1.1）を同じ番号の下に置いている。同文書の「同じ番号は同じ意味でだけ使う」と食い違う。NFR1.1 の下の NFR1.1.3・NFR1.1.4（バイナリの出どころ・`/favicon.ico`）も、上流の NFR1.1 の文言（SVG の安全とヘッダー）の外で、実際は FR9.5・AC4.1.3 に当たる | NFR2.1 を N/A にし、素材の大きさの上限は別の ID（U6 の追加の要件として明示）に移す。NFR1.1.3・NFR1.1.4 の上流を FR9.5・US4.1 の AC として traceability に出すか、追加の要件であることを冒頭に書く | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/security-requirements.md > NFR1.1.1 の確かめ方 | 検査の対象を「`frontend/public/` と `frontend/src/` の下」とするが、ライセンスヘッダーの検査（check-license-header.mjs）は `frontend/` 全体（`.` で始まるディレクトリと SKIP_DIRS を除く）を走る。`e2e/` などに置いた `.svg` の扱いが2つの検査でずれる | 中身の検査の対象を、ヘッダーの検査と同じ範囲（`frontend/` 全体、`vendor/`・`node_modules`・`dist` を除く）にそろえるか、違う範囲にする理由を書く | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| traceability.json の読み取り | 上流の NFR 14 件がすべて OK か N/A に振り分けられている | 本文の NFR1.1・2.1・3.1・3.2・5.3 の細目と対応する。NFR2.1 の分類だけ意味がずれている（Minor） |
| 実現できない・緩めた目標の確認 | 無し | NFR3.1.1 は axe 違反 0 件のまま、規則を外さない旨が書かれている。素材の大きさの上限は新設で、緩めたものではない |
| 現状の確認（Read のみ） | `frontend/public/` は無く、`index.html` に favicon の `<link>` は無い。`check-license-header.mjs` は HTML 形のコメントを `HTML_EXTENSIONS` で検査する | 文書の前提と一致。`.svg` を足すだけでヘッダーの検査は成り立つ。XML 宣言が先頭の不合格は先頭一致の検査で成り立つ |

### Summary

ヘッダーの検査の置き場、ライセンスヘッダーとバイナリの扱い、飾りのアイコンの決まり、素材の大きさの上限は妥当で、緩めた目標も無い。主な懸念は、字句の切り出しによる SVG の安全の検査が抜け道（大文字・接頭辞・処理命令・CDATA・属性値の中の `>`）への耐性を決めていないことと、`/favicon.ico` の確かめが本物の `dist`・WAR を通る形になっていないこと。どちらも設計の補足で直せる（Major 2 件のため判定は READY）。
