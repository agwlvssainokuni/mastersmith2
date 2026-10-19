# コードの要約 — U5 u5-carryover-tests（Bolt B1）

承認済みの計画（`code-generation-plan.md`）の Step 1〜16 を行った。Step 17（コミット）と Step 18（統合）は、指揮役が依頼者の承認を得て行う。作業ブランチは `feature/261016-business-data-views-b1`（`develop` の `05a9b37` から作った）。詳しい結果と計画に無い判断は `generation-notes.md`（1節・2節の N-1〜N-11）にあり、ここではその要点を書く。本番のコード（`backend/src/main`・`frontend/src` のテストでないファイル）と配備物は変えていない。

## 1. 作ったもの・変えたもの

作った・変えたアプリのソースは `source-manifest.json` に 13 のパスで並べた（消したものは無い）。

| 場所 | 中身 |
|---|---|
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteReorderBarrierConfig.java`（新） | 監査の書き込みの時間を測る `LongSupplier` を `@Primary` で差し替え、止める相手の書き込みの直前で止め、放す相手の書き込みの終わりで放す待ち合わせ。口は `armFor(held, releaser, waitLimit)`・`heldArrived()`・`releasedByTimeout()`・`disarm()`。スレッドだけを受け取り、`role` を知らない |
| `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java` | `Gate.arrivedThread()`・`Signal.passedThread()`（記録だけ。振る舞いは変えない） |
| `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java` | `Actor.toString` で email と token を伏せる |
| `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` | #7 を自然の順と入れ替えた順の2つのメソッドにし、監査の2行を中身で見分ける。#1・#4 は変えていない |
| 単体テスト3ファイル（新） | `AuditWriteReorderBarrierTest` 7件・`TestRoleBarrierTest` 3件・`RoleActorsTest` 2件 |
| `frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 対象の1件で、効果の回数を写す前に `waitFor` でちょうど 1 を待つ。ほかの 11 件は変えていない |
| `perf/lib/perf-db-url.sh`・`perf/lib/perf-db-url-test.sh`（新） | `PERF_DB_URL` の形の確かめの関数と、その試し（19 件） |
| `perf/dsl-timing.sh` | 引数の確かめの直後・環境を作る前に確かめの関数を呼ぶ。冒頭の注記 |
| `perf/dsl-mixed-memory.sh`（新） | `dslMixed` の1回の測り（1秒ごとの `memory.stat` の記録・peak・まとめ・WARN の件数・設定の値の読み取り）と、記録のループだけの `--loop-only`、TSV の確かめだけの `--tsv-check`。切れた行は捨て、`trap` で記録のループを必ず止める（R-03） |
| `perf/README.md` | `PERF_DB_URL` の受け付ける形と、(b) 節の `memory.peak` の測り方の手順 |

## 2. 主な決定

- #7 の止める相手と放す相手は `TestRoleBarrier` の印で特定する。後の保存の `BEFORE_LOCK` の知らせは、先の保存が `AFTER_LOCK` で止まった後に作る（先に作ると先の保存のスレッドが最初に通るため。N-2）。
- どちらの順の場合も、待ち合わせの結果と「409 でないこと」を、ほかの確かめより前に確かめる（D1: A。自然の順にも当てたのはコードのレビューの R-02 を受けた依頼者の決定）。自然の順も同じ待ち合わせで作る（D2: A）。
- `PERF_DB_URL` は前の部分 `jdbc:h2:file:/app/data/mastersmith` ちょうどと、`DEFRAG_ALWAYS=TRUE`・`RETENTION_TIME=<1〜9 桁>` だけを受け付け、`INIT` は受け付けない。止めたときは決まりの名前だけを出す。
- 新しい台本には、`.github/dependabot.yml` の一覧に無い所にイメージの版とダイジェストを書かない（`K6_IMAGE` で受け、H2 の `Shell` は測るアプリのイメージで動かす。N-3）。
- 記録に書くのは数値・時刻・状態・ロガーの名前と件数だけ。k6 の出力は件数だけを残す（N-7）。

## 3. テストとカバレッジ

直しの前の結果を表に、レビューの直し（7節）の後の結果を7節に書く。

| 確かめ | 結果 |
|---|---|
| 走らせ方の確かめ（変更の前） | `LargeRoleTransferYamlTest` 3件・`RoleConcurrencyIT` 4件・`AdminForbiddenProvider.test.tsx` 12件が通過 |
| テストの補助の単体テスト | 7件・3件・2件が通過 |
| #7 の二段の1段目（直す前の確かめ方） | 入れ替えた順で `to contain "before":{"main":null` で落ちた。`heldArrived=true`・`releasedByTimeout=false`、「後: FULL」の行の ID 2 <「後: READ」の行の ID 3 |
| #7 の二段の2段目と繰り返し | `RoleConcurrencyIT` 5件が通過。5回の繰り返しで5回とも5件通過、`ROLE_BUSY` 0 件 |
| 画面のテストの二段 | T1 は5回とも `expected 1 to be +0` で落ち、T2 は5回とも通過。試しは戻して差が無いことを確かめた。直した後のファイル 12 件を5回流し、5回とも通過 |
| `PERF_DB_URL` の試し | 19 件すべて期待どおり、値の漏れ 0 件。悪い値で `dsl-timing.sh` は終了コード 1、一時ディレクトリは作られない |
| 記録のループの空回し（配備したアプリのコンテナ、5 秒、読むだけ） | 5行・`NA` 0 件・ループの残り無し。同じ写しからの比べで5つのキーがすべて一致 |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | BUILD SUCCESSFUL、17分38秒。単体 2,370 件・結合 1,254 件（`assumeTrue` による既存の飛ばし 4 件）・画面 1,633 件、失敗 0 |
| カバレッジ | バックエンド 行 97.8%・分岐 91.8%、フロントエンド 行 95.37%・分岐 90.39%（本体を変えていないため、本体の値は変わらない見込みどおり） |

## 4. traceability の読み方（計画の 7節）

`traceability.json` の `OK` の target は道1つのため、計画の「部分」の書き分けをここに書く。

| ID | 状態 | この段で閉じたこと | Build and Test に残ること |
|---|---|---|---|
| BR1.1〜BR1.6 | OK | 二段の記録（直す前の失敗・2つの場合の通過・5回の繰り返し）が `generation-notes.md` にある | CI の試行ごとの結果 |
| BR2.1〜BR2.5 | OK | T1・T2 と5回の繰り返し、戻したことの差 | 同上 |
| BR3.1〜BR3.3 | OK（台本と手順） | 空回しで列が正しいこと | 4回の測り |
| BR3.4・BR3.5 | Deferred | 手順を README に書いた | 測りと判定 |
| BR4.1 | OK（部分） | 渡し方（URL）の確かめ、確かめの関数と試しの表、一時のボリュームでの設定の保存と読み取りの確かめ（N-4） | 使い捨てのアプリで効いたことの確かめ |
| BR4.2・BR4.3 | Deferred | — | 結果の時点で依頼者に諮る |
| NFR4.5 | OK（部分） | 一時ファイルの一覧と手順、秘密の検索の関数 | 件数の検索と片付けの確かめ |
| NFR4.6 | OK（部分） | 記録の列（数値・時刻・状態だけ）、k6 の出力を残さない | `env.txt` と TSV の件数の確かめ |
| NFR4.7 | OK（部分） | 確かめの関数（`INIT` を一覧から外した） | 設定の値の確かめ |
| NFR4.8・NFR4.9 | OK | 指標の公開と待ち受けは `dsl-timing.sh` の `app.env` のまま。ループに追加の権限を付けない | — |
| NFR7.1〜NFR7.3 | OK | 失敗の文に token・email を出さない、待ち合わせの後始末、`sleep` と上限の延長なし | — |

FR16.1〜FR16.4 は BR3・BR4 を通して2段の連鎖でたどる（行は置いていない。N-11）。

## 5. Bolt の計画との差

- Bolt の計画（`inception/delivery-planning/bolt-plan.md`）では B1 は1番目だったが、コード生成の順を U1 → U4 → U5 → U2 → U3 にした決定（`construction/code-generation/memory.md` の Deviations）により、B2（U1）・B3（U4）の後の3番目に移った。B2・B3 の統合の後の CI の1回目に別のテスト2件（6節の4）が落ちたが、対象の2件は落ちていない。
- 計画との差は `generation-notes.md` の N-2〜N-11。どれも本番のコードと配備物に及ばない。

## 6. 依頼者に確かめたいこと

1. **#7 の知らせを作る時点（N-2）**: 後の保存の `BEFORE_LOCK` の知らせを、先の保存が止まった後に作る形に替えた。今までの #7 は後の保存が排他の直前の点（`BEFORE_LOCK`）を通ったことも確かめていなかった。今の形が確かめるのはその点を通ったことまでで、排他を待っていることまでは保証しない（コードのレビューの R-01）。この形でよいか（依頼者は受け入れ済み）。
2. **イメージの固定先（N-3）**: 新しい台本は k6 のイメージを `K6_IMAGE` で受け、H2 の `Shell` を測るアプリのイメージで動かす（固定先の一覧を増やさないため）。計画の 4.4 の R-02 の形（eclipse-temurin のイメージ）との差としてよいか。
3. **計画に無い先の確かめ（N-4）**: 一時の docker のボリュームだけで、URL の `RETENTION_TIME=0` が保存されることと読み取りの形を確かめた（配備したアプリには触れていない）。記録してよいか。
4. **対象の外の CI の失敗**: B2 の `ea23bf3` の CI の1回目で `PatternCheckerTest`、`0427d45` の1回目で `PasswordChangePage.test.tsx` が落ち、どちらも2回目で通った。team.md の「不安定なテストと CI の失敗」の決まり（手元で再現しないものは見立てを記録して再実行で進めてよい・同じテストが二度落ちたら直す）で、記録と扱いをどうするか。
5. **手順のコマンド（N-9）**: `unit-test-instructions.md` 3.2 の「親が止まることの確かめ」は zsh では一致しない glob が誤りになるため、bash で流した。承認済みの文書は変えていない。

## 7. コードのレビュー（READY・Minor 4）の直し

レビューの記録は `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/code-generation/units/u5-carryover-tests/bb969d769be0cb2e/1.json`。依頼者の決定で R-01〜R-03 を統合の前に直し、R-04 は持ち越す。

| 指摘 | 直したこと | 確かめ |
|---|---|---|
| R-01（N-2 の書き方） | この文書の 6節の 1・`generation-notes.md` の N-2・`RoleConcurrencyIT` の Javadoc を、「後の保存が `BEFORE_LOCK` を通ったことまでを確かめ、排他を待っていることまでは保証しない」に直した | 読み合わせ |
| R-02（自然の順の 409） | `RoleConcurrencyIT` の #7 の自然の順の場合も、待ち合わせの結果（`heldArrived` 真・`releasedByTimeout` 偽）と「後の保存が 409 でないこと」を、入れ替えた順と同じ位置（結果を待った直後、ほかの確かめより前）に置いた。409 `ROLE_BUSY` の分岐（code だけを確かめて通す形）は消した。BR1.3（409 の分岐を変えない）との差は、依頼者の決定として受け入れた | `--rerun` で5回流し、5回とも5件通過（1回 8.89〜9.08 秒）。`ROLE_BUSY` は 0 件 |
| R-03（`perf/dsl-mixed-memory.sh`） | (1) 記録の TSV の完全な行だけ（改行で終わらない最後の行と、9 列・時刻と数か `NA` の形でない行を捨てる）で、まとめと後置きの欠けを計算する。捨てた数を `summary.txt` の `dropped_rows` に出し、時刻が読めなければ `post_missing_seconds=NA` にして続ける。`memory.peak`・`memory.stat` はループを止めた直後に先に読む。(2) `trap` を EXIT・INT・TERM に置き、手元の `docker exec` と、コンテナの中の目印（台本の1回ごとに一意の引数）を持つ記録のループの処理だけを止める（手元の `docker exec` を止めてもコンテナの中のループは残ると確かめたため）。(3) 確かめのための `--tsv-check <TSV>` を足した。一時ファイルの一覧と `KEEP` の決まりは変えていない。`perf/README.md` の (b) 節に3つの点を書き足した | `bash -n` が通過。切れた行を含む TSV 5つの形（最後の行が列の足りないまま切れた・9 列だが改行で終わらない・途中の行が切れた・見出しだけ・完全）で `--tsv-check` が止まらず、切れた行を捨てた。配備したアプリのコンテナに読むだけで上限 30 秒のループを起こし、3 秒後に TERM・INT を送ると、台本は 143・130 で終わり、コンテナの中と手元にループは残らなかった。普通に終わる5秒の空回しも5行・`NA` 0 件・残り無し |
| R-04（固定の値の写しと H2 の版） | 持ち越し（手を付けていない） | — |

- 直しの後の `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: BUILD SUCCESSFUL、17分44秒（13:42:20Z〜14:00:04Z）。単体 2,370 件・結合 1,254 件（既存の `assumeTrue` の飛ばし 4 件）・画面 188 ファイル・1,633 件、失敗 0。カバレッジはバックエンド 行 97.8%・分岐 91.8%、フロントエンド 行 95.37%・分岐 90.32%（前の回の分岐 90.39% からの差は、画面のソースを変えていない回どうしの揺れ）。流した後の `pmset -g log` に、検査の窓の Sleep・DarkWake は無かった（最後は 2026-10-18）。

