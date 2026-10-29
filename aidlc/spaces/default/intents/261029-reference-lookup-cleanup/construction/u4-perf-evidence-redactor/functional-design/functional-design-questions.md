# Functional Design の質問 — U4 u4-perf-evidence-redactor（Intent 261029-reference-lookup-cleanup）

U4（k6 の結果の JWT の自動の伏せ字）の機能設計で、判断が分かれる点だけを尋ねる。最初の選択肢を推奨とし、理由を添える。

## 決まっていること（質問しません）

- JWT の形は `eyJ` で始まる base64url の 3 区間（`.` 区切り）。2 区間は置換しない。`eyJ` の前が英数字の文字列は置換しない。値・配列の要素・オブジェクトのキー名・長い文字列のどこにあっても置換する。キー名の置換で 2 つのキーが同じになるときは失敗にする（AC4.1.1）。
- 置換の後の残存の検査は、置換とは独立の関数にし、見つかれば非 0 で終わり、出力も一時ファイルも残さない。入力が JSON でない・空のときも失敗にする（AC4.1.2〜AC4.1.4）。
- 道具は `perf/` に置く Node の 1 ファイル（依存なし）で、呼び方は契約 C7 のとおり（入力の summary json と出力のパス）。性質ベースのテストは `frontend/` の Vitest と fast-check から呼ぶ（ADR-004）。ファイルの名前と、`frontend/` の検査の対象への入れ方はコード生成で決める。
- 伏せる対象は summary json。k6 の標準出力とログは対象外で、伏せる手順を `perf/README.md` に書く（AC4.1.8）。リフレッシュトークンと Authorization ヘッダーの値は形による置換の対象にせず、summary json に入らないことを台本の書き出す項目で確かめて記録する（AC4.1.9、M2: B）。

## Q1. [品質] すでに記録（`evidence/`）にあるファイルに JWT の形が無いことを確かめる検査を、どこで、どの範囲に置くか（AC4.1.7）

前の Intent では、記録に入った JWT をコミット前の `verify` が拾えず、プッシュの後の CI で落ちた。この検査は、手で置いたファイルや、道具を通さずに置いたファイルも拾う最後の守りになる。

A. `./gradlew verify` の中の Gradle のタスクにし（CI も同じ `verify` を呼ぶ）、`aidlc/` の下のすべての `evidence/` の、すべてのテキストのファイル（`.json`・`.md`・`.txt`・`.log` など）を、伏せ字の道具の残存の検査の関数で調べる（推奨）。コミットの前に手元で拾え、json 以外に貼った値も拾える。代わりに、`verify` の時間がファイルの数だけ少し伸びる
B. CI の段にだけ置き、`evidence/` の `.json` だけを調べる。`verify` は変わらないが、プッシュの後まで気づかず、前の Intent と同じく履歴に残った値を直すことになる
C. `verify` に置くが、`.json` だけを調べる。速いが、手順書や `.md` に貼った値を拾えない
X. Other (please specify)

[Answer]: A

## F1. [品質] 招待のトークンが k6 の summary json の `setup_data` に入る場面の扱い（追加の質問）

設計の途中で、利用者の登録の 2 場面（`registrationVerify`・`registrationComplete`）の `setup()` が、招待のトークンを `setup_data.registrationTokens` に入れると分かった。招待のトークンは JWT の形ではないため、JWT の形だけを伏せる今の決め方では伏せられず、記録に置くとそのまま残る（`project.md` の Forbidden: 招待のトークンを記録に含めない）。リフレッシュトークンと Authorization ヘッダーは summary json に入らないことを台本で確かめた（AC4.1.9）。

A. 伏せ字の道具で、`setup_data` の下の文字列の値をすべて伏せる（キーと型は保つ）。JWT の形の置換は全体にそのまま当てる。合否の数値は `setup_data` に入らないため失われず、今後の場面で `setup_data` に入りうる値も漏れない。ストーリーの M2: B（キーによる伏せ字は今回行わない）との差として記録する（推奨）
B. 登録の 2 場面の summary json は記録に置かず、数値は手順で表に写す。仕組みは小さいが、写し忘れや今後の場面で同じ形が起きうる
X. Other (please specify)

[Answer]: A
