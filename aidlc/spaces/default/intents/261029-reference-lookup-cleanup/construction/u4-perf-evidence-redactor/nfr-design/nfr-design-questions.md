# NFR Design の質問 — U4 u4-perf-evidence-redactor（Intent 261029-reference-lookup-cleanup）

U4（JWT の伏せ字）の NFR 設計で、判断が分かれる点だけを尋ねる。最初の選択肢を推奨とし、理由を添える。U4 は種類が `library` のため、この段の成果物は security-design.md・logical-components.md・traceability.json。

## 決まっていること（質問しません）

- 置換・残存の検査・`setup_data` の伏せ字・テキストの置換の口・入力の上限 64 MiB・値を表示しないこと・終わりのコードは、機能設計と NFR 要件のとおり。
- テストの置き場と `frontend/vitest.config.ts` の include・カバレッジの扱いは、コード生成の計画で明記する（NFR 要件）。
- verify の `evidence/` の検査は、NUL を含むファイルを飛ばすときにパスだけを表示する（NFR 要件）。

## Q1. [品質] verify の `evidence/` の検査で、NUL を含むファイルを飛ばしたときに失敗にする範囲（NFR 要件の承認で直すよう記録した点）

今の NFR 要件は、拡張子が `.json`・`.md`・`.txt`・`.log`・`.tsv` のファイルを飛ばしたときだけ失敗にする。`.csv`・`.yaml`・`.html`・拡張子なしなど、列挙に無い名前の UTF-16 や NUL 混じりのテキストは、飛ばしたと表示されるだけで通ってしまう。

A. 決めたバイナリの拡張子（画像 `.png`・`.jpg`・`.jpeg`・`.gif`・`.webp`・`.svgz`、圧縮 `.gz`・`.zip`、`.pdf` など、設計で一覧にする）以外は、NUL を含んで飛ばしたら失敗にする（推奨）。知らない形のファイルは安全側に倒れ、今の `evidence/` の形（json・md・txt・log と画像）では誤って落ちる見込みが低い
B. 今の 5 つの拡張子のままにし、列挙外の飛ばしは表示だけで通すことを、残る穴として記録する。誤って落ちることは無いが、列挙外の名前で置いた値は拾えない
X. Other (please specify)

[Answer]: A
