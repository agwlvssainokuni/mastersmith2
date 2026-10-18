# Functional Design の質問 — U5 u5-carryover-tests

U5 は、前の Intent からの持ち越しを受け持つ単位で、次の2つを扱います。上流は `aidlc/spaces/default/intents/261016-business-data-views/inception/` の下の `units-generation/unit-of-work.md`、`requirements-analysis/requirements.md`（FR15・FR16）、`delivery-planning/bolt-plan.md`（B1）です。

- 不安定なテスト2件（`RoleConcurrencyIT` の #7、`AdminForbiddenProvider.test.tsx`）の直し
- `memory.peak` の測り方の台本

## 原因の見立て（コードで確かめた作り。未検証の仮説）

**RoleConcurrencyIT #7（K-57）**
- 2件の保存は、行の排他で直列になります。
- 監査の記録は、各保存の確定の後に、新しいトランザクションで書かれます（`AuditEventListener`・`AuditEventRecorder`）。そのため、後の保存の監査が先に書かれることがあります。
- テストは監査の行を ID の順に読み、1行目と2行目の中身を決め打ちしているため、書かれる順が入れ替わると落ちます。

**AdminForbiddenProvider.test.tsx（K-58）**
- 部品が DOM に現れた時点（`findByTestId` が解けた時点）では、効果（`useEffect`）の回数を数えるより前のことがあります。
- そのため、前の回数が 0 と写り、後の確かめが落ちます。

## 決まっていること（質問にしない）

- **直す範囲**: テストの側だけを直し、部品の責務は変えない。テストだけの変更なので、`packagesJudgedByTotal` と境界テストの作業は付かない。
- **統合と E2E**: squash で `./gradlew verify` だけで統合し、統合の前の E2E は流さない（team.md）。
- **画面のテストの直し方**: `waitFor` で待つ形にし、時間の上限は延ばさない（team.md）。
- **物差しと合格の線**: 物差しは `memory.peak/memory.max`、線は 90% 未満、コンテナの上限は 2g のまま。物差しを `anon` に替えない（要件 Q11: A、team.md）。
- **測定の進め方**: 使い捨ての環境で測る。前の最大を消してから測り、`caffeinate -dims` で包む。`pmset` で眠りを確かめ、片付ける前に結果を確かめる（project.md の学び）。測定そのものは Build and Test で行う。
- **ほかの同時性のテスト**: 監査の行を順不同か1行だけで確かめており、順に頼る形は #7 だけだった（検索で確かめた）。
- **内部DB の表**: アプリが独自に持つデータは無いので、entities.md にエンティティは置かない。

## Q1. RoleConcurrencyIT #7 の直し方

A. テストで、監査の2行を中身で見分ける（「前が空・後が READ」の行と「前が READ・後が FULL」の行が、ちょうど1つずつある）。本番の監査の書き方は変えない。後の保存が前の保存の値を見ていること（直列になっていること）は確かめ続けられる（推奨）
B. 1件目の監査が書かれるのを、テストで待ってから2件目を確定させる（自然に起きる順の入れ替えを、テストで見なくなる）
C. 本番で、監査の書き込みを保存の順に直列にする（配備物が変わり、待ちが増える）
X. Other (please specify)

[Answer]: A

## Q2. 二段の確かめで起こした「順の入れ替え」「効果の前の写し」を、直した後もテストに残すか

A. バックエンドは残す。#7 を「自然の順」と「入れ替えた順」の2つの場合にして毎回流す。入れ替えた順は、1件目の監査の書き込みを、2件目の監査が書かれるまで止める待ち合わせで作る。画面のテストは、効果の前に写す形が直す前に必ず落ちることを試しで確かめて記録し、試しの変更は戻す（推奨）
B. どちらも試しで確かめて記録するだけにし、テストには残さない
C. どちらも残す
X. Other (please specify)

[Answer]: A

## Q3. 二段の確かめで、確実に起こせなかったときの再現の試みの時間の上限

A. 1件ごとに 30 分。起こせなければ、試みた範囲と見立てを記録して進める（推奨）
B. 1件ごとに 60 分
X. Other (please specify)

[Answer]: A

## Q4. ページキャッシュを減らす手（FR16.3）の試し方と、効いたときの取り込み

前の Intent の内訳では、`anon` は 68% 前後で横ばいでした。一方、ページキャッシュ（`file`）は、DSL の投入のたびに 485 MiB（23.7%）まで増えました。90% 未満にするには、ページキャッシュを約 425 MiB 未満に抑える必要があります。

A. U5 のコード生成では、測りの台本・手順と、試す手の候補（H2 の設定 `RETENTION_TIME` を短くする など）だけを書く。試しは Build and Test の使い捨ての環境で、接続先の上書き（`PERF_DB_URL`）で行い、配備物は変えない。効いた手を配備物に取り込むかは、その時点で依頼者に諮る（推奨）
B. U5 のコード生成で、H2 の設定を変えて配備物に入れ、Build and Test で測る（効くか分からない変更を先に入れる）
C. 設計の段（NFR 設計）で、リポジトリの外の試しで先に測り、結果で U5 の変更を決める（設計の段で配備したアプリを止める）
X. Other (please specify)

[Answer]: A

## Q5. `memory.peak` の測り方の細部（読む間隔と回数）

A. 1秒ごとに読み、2回流して、2回とも 90% 未満なら Met とする（推奨。前回は5秒ごとで、94.4% と 93.1% だった）。読む値は次のとおり。
- `memory.current`・`memory.peak`
- `memory.stat` の `anon`・`file`・`inactive_file`・`kernel`
- H2 のファイルの大きさ
- ヒープの使用
B. 前回と同じ5秒ごと、2回
C. 1秒ごと、3回（配備したアプリを止める時間が延びる）
X. Other (please specify)

[Answer]: A

## Requested Changes Feedback

承認の場（2026-10-18）の Request Changes で、次の直しを決めた（Major と、単位の継ぎ目に関わる Minor）。

- R-01（Major）止める相手: 入れ替えた順の待ち合わせは、「最初に来たスレッド」ではなく、先の保存（テストが先に送る要求）の監査の書き込みを特定して止める。特定は、テストがその要求のスレッドに付ける印（または出来事の中身の後の値）で行い、後の保存の監査が書かれたら放す。上限の時間で放されたら、入れ替えが起きなかったとしてテストを失敗にする。
- R-02（Major）試し方の前提: `PERF_DB_URL` が dslMixed の使い捨ての環境で効くかを、コード生成で台本を読んで確かめる。効かなければ、使い捨ての環境の接続先を上書きする口を台本に足す。`RETENTION_TIME` を接続先の URL で渡せないときは、接続の初期化（`INIT=SET RETENTION_TIME ...`）で渡す。どちらもだめなら記録して諮る。
- そのほかの Minor（R-03〜R-05）は、この単位の記録に残し、コード生成の計画で拾う。

## Consolidated Summary Confirmation

- **RoleConcurrencyIT #7**: 監査の2行を中身で見分ける形に直す。本番の監査は変えない（Q1: A）。直列になっていることと `ROLE_BUSY` の分岐の確かめは保つ。
- **#7 の入れ替えた順の場合**: テストに残す（Q2: A）。`audit/testsupport` の待ち合わせ（書き込みの時間を測る `LongSupplier` の差し替え）で作り、直す前のテストが落ち、直した後のテストが自然の順と入れ替えた順の両方で通ることを記録する。
- **AdminForbiddenProvider.test.tsx**: 写す前に `waitFor` で初めの効果を待つ。時間の上限は延ばさない。効果の前に写す形が、直す前に必ず落ちることを試しで確かめて記録し、試しの変更は戻す（Q2: A）。
- **再現の試み**: 起こせないときは、1件ごとに 30 分を上限に試み、試みた範囲と見立てを記録する（Q3: A）。
- **ページキャッシュを減らす手**: コード生成では、台本と試す手の候補（H2 の `RETENTION_TIME` など）だけを書く。試しは Build and Test の使い捨ての環境で `PERF_DB_URL` で行い、配備物は変えない。取り込むかは、その時点で諮る（Q4: A）。
- **memory.peak の測り方**: 1秒ごとに読む。2回流し、2回とも 90% 未満なら Met（Q5: A）。物差しは `memory.peak/max` のまま、上限は 2g のまま。`anon` の最大と内訳は並べて記録する。
- **決まり**:
  - BR1 #7
  - BR2 画面のテスト
  - BR3 測り方
  - BR4 ページキャッシュを減らす手
- **エンティティ**: アプリが独自に持つデータは無いため、entities.md にエンティティは置かない。
- **受け持つ ID**: FR15・FR15.1・FR15.2・FR16・FR16.1〜FR16.4。

- 承認の場の直し（Requested Changes Feedback のとおり）:
  - R-01: 待ち合わせは先の保存の監査の書き込みを特定して止め、入れ替えが起きなければ失敗にする
  - R-02: `PERF_DB_URL` が dslMixed で効くかを確かめ、だめなら口を足す。`RETENTION_TIME` は URL か接続の初期化で渡す

Does this all look correct before I generate the functional design?

- Looks correct
- Request changes

[Answer]: Looks correct
