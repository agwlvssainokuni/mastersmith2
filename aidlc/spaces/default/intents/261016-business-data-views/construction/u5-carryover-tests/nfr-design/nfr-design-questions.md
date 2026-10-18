# NFR Design の質問 — U5 u5-carryover-tests

U5 はテストと測り方の持ち越しの単位です（library）。成果物は security-design・logical-components・traceability の3つです。上流は承認済みの NFR 要件と機能設計です。

## 前の段から持ち越した論点

- NFR 要件の承認の場の決定: 台本で `PERF_DB_URL` の形を確かめる（今は確かめずに書いているため、`INIT=RUNSCRIPT` などを渡せてしまう）。
- NFR 要件のレビュー:
  - R-02: `RoleActors.Actor` の既定の `toString` が、メールアドレスとトークンを出す。→ Q2
  - R-03: `releasedByTimeout()`・`disarm()` は新しく足す口。
  - R-04: 資格情報を持つ一時ファイルの一覧が、実際のファイルと合わない。
  - R-05: 「コンテナに書かない」の範囲が記録のループに限ることを明記する。
  - R-06: cgroup v2 の前提と、読めないときの扱い。
- 待ち合わせが止めたスレッドを放す条件（機能設計のレビュー）。→ Q1
- 読んで分かった事実: `dslMixed` は README の手順で流しており、`PERF_DB_URL` は `dsl-timing.sh` にしか無い。使い捨ての環境には渡らない見込みが高い（確定はコード生成で行う）。

## 決まっていること（質問にしない）

- 本番のコードと配備物は変えない。新しい依存は足さない。H2 の設定は保つ。
- `PERF_DB_URL` は、口を残したまま台本の1つの関数で形を確かめる。
  - 許すのは `jdbc:h2:file:/app/data/mastersmith` で始まる URL と、許す一覧の設定（`DEFRAG_ALWAYS=TRUE`・`RETENTION_TIME=<整数>`・`INIT=SET RETENTION_TIME <整数>`）だけ。
  - それ以外は起動の前に止め、値は表示しない。
  - 悪い入力を与えて止まることを、コード生成で確かめる。
- 資格情報を持つ一時ファイルの一覧は、2つの流れのものを合わせてそろえる。同じ一覧を、記録に含めない対象・検索の対象・片付けの対象に使う。
- 記録のループ:
  - 書かない範囲は記録のループに限る。ファイルの大きさは `mastersmith.mv.db` だけを読む。
  - 始める前に cgroup v2 のファイルが読めるかを確かめる。読めなければ、その回は合否に使わない。
- 待ち合わせの部品の置き場と使い方:
  - 部品は `audit/testsupport`、スレッドの記録は `role/testsupport` に置く。向きは role → audit だけにする。
  - 後始末で必ず放す。
  - 入れ替えた順の場合は、行の ID の順で入れ替えが実際に起きたことも確かめる。
- 画面のテストの写す位置・409 の扱い・自然の順の書き方は、コード生成の計画で決める。

## Q1. 待ち合わせが止めたスレッドを放す条件を、どこまで狭めるか

A. 後の保存のスレッドも記録し、そのスレッドの書き込みの終わりでだけ放す。口は「止める相手」と「放す相手」の2つを受け取る形にする。ほかのスレッドの監査で早く放されることが無くなる（推奨）
B. 機能設計のまま（止める相手でないスレッドなら放す）にし、狭め方はコード生成の計画で決める
X. Other (please specify)

[Answer]: A

## Q2. テストの利用者（`RoleActors.Actor`）がメールアドレスとトークンを出さない手当て

A. `Actor` の `toString` を上書きして利用者の ID だけを出し、メールアドレスとトークンは伏せる（team.md の Code Style の「秘密を持つ record は toString を上書きする」にそろえる）。値を読む口は変えない（推奨）
B. `toString` は変えず、失敗の文に `Actor` を渡さないことを読み合わせで確かめる
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**security-design**
- S1 `PERF_DB_URL` は、許す一覧に当たる形だけを通す。確かめは台本の関数1つで行い、それ以外の形では起動の前に止める。値は表示しない。
- S2 資格情報を持つ一時ファイルの一覧を、2つの流れで1つにそろえる。置き場はホームの下の権限 700 の場所とし、片付けのときにまとめて消す。
- S3 記録のループは読むだけにする。始める前に cgroup v2 で読めるかを確かめ、読めなければその回を合否に使わない。
- S4 テストの補助と失敗の文に出すのは、`detail`・件数・真偽・スレッドの名前だけにする。`Actor` は `toString` を上書きして、利用者の ID だけを出す（Q2: A）。

**logical-components**
- 待ち合わせの部品（`audit/testsupport`）は、止める相手と放す相手を受け取る（Q1: A）。
  - `releasedByTimeout`・`heldArrived`・`disarm` を持つ。
  - 後始末で必ず放す。
- `TestRoleBarrier` にスレッドの記録を足す。
- 台本の関数と記録のループ。

**traceability**
- NFR4.5〜4.9・NFR7.1〜7.3 を、上の設計に当てる。

Does this all look correct before I generate the NFR design?

- Looks correct
- Request changes

[Answer]: Looks correct
