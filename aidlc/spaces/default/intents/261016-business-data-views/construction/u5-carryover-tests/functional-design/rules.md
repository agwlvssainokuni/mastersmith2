# 決まり — U5 u5-carryover-tests

U5 の決まりは、テストの直し（BR1・BR2）と、測り方（BR3）・ページキャッシュを減らす手の試し方（BR4）の4群。どれもテストと台本の振る舞いを決めるもので、本番の振る舞いは変えない。承認の場（2026-10-18 Request Changes）の R-01 で BR1.5 を、R-02 で BR4.1 を直した（`functional-spec.md` の8節）。

```yaml
rules:
  - id: BR1.1
    statement: RoleConcurrencyIT の #7 は、監査の2行を行の順に頼らず中身で見分ける
    category: validation
    applies_to: RoleConcurrencyIT #7（同じロールの2件の同時の保存）
    trigger: 2件目の保存が 204 で終わった後の監査の確かめ
    logic: IF 対象のロールの ROLE_PERMISSION_CHANGED の監査の行がちょうど2行あり、そのうち「前が空・後が READ」の行と「前が READ・後が FULL」の行がちょうど1つずつある THEN 合格。行の ID の順（どちらが先に書かれたか）は判定に使わない
    violation: テストを失敗させ、見つかった行の中身を失敗の文に出す
    source: FR15.1
  - id: BR1.2
    statement: 2件の保存が直列になったことの確かめを保つ
    category: validation
    applies_to: RoleConcurrencyIT #7
    trigger: 2件目の保存が 204 で終わった後
    logic: IF 最後の主権限が FULL であり、かつ後の保存の監査の行の前の値が READ（先の保存の結果を見ている）である THEN 直列になったとみなす
    violation: テストを失敗させる
    source: FR15.1
  - id: BR1.3
    statement: 2件目が 409 ROLE_BUSY で返る分岐の扱いを変えない
    category: policy
    applies_to: RoleConcurrencyIT #7
    trigger: 2件目の保存の応答が 409 のとき
    logic: IF 2件目の応答が 409 THEN code が ROLE_BUSY であることだけを確かめ、監査の2行の確かめは行わない（今の扱いのまま）。入れ替えた順の場合の 409 の扱い（R-04）はコード生成の計画で決める
    violation: code が違えばテストを失敗させる
    source: FR15.1
  - id: BR1.4
    statement: 本番の監査の書き方（確定の後に、要求ごとのスレッドで、新しいトランザクションで書く）は変えない
    category: constraint
    applies_to: audit の AuditEventListener・AuditEventRecorder
    trigger: U5 のコード生成
    logic: IF 直しが本番の監査の順を保証する変更を要する THEN その直しは採らない（テストの側だけで直す。Q1 A）
    violation: コード生成の計画で外し、要るなら依頼者に諮る
    source: FR15.1
  - id: BR1.5
    statement: 監査の書き込みの順を確実に入れ替える待ち合わせを、テストの補助として置き、先の保存の監査の書き込みを特定して止める。#7 を自然の順と入れ替えた順の2つの場合で毎回流す
    category: policy
    applies_to: backend の audit/testsupport の待ち合わせ、role/testsupport の TestRoleBarrier、RoleConcurrencyIT #7
    trigger: #7 の入れ替えた順の場合の実行
    logic: IF 待ち合わせを準備した THEN 止める相手を「最初に来たスレッド」ではなく、先の保存（テストが先に送る要求）に決める。特定は、先の保存の要求を処理するスレッドに付ける印で行う（排他の後の止める点 AFTER_LOCK に来たスレッドを TestRoleBarrier が記録し、テストがそのスレッドを待ち合わせに止める相手として渡す。監査は確定の後に同じスレッドで書かれるため）。印で特定できないときは、出来事の中身の後の値（主権限 READ）で特定する形に替え、替えたことをコード生成の記録に書く。止める相手のスレッドは、監査の書き込みの直前の呼び出しで止め、ほかのスレッドは止めない。後の保存の監査の書き込みが終わったこと（止める相手でないスレッドの、書き込みの終わりを測る呼び出し）で放す。止めたスレッドと放す側のスレッドは分けて持つ。待ちの上限は TestRoleBarrier.WAIT_LIMIT。準備していない間は止めずに通す
    violation: 上限の時間で放されたときは、入れ替えが起きなかったとして待ち合わせにその旨を残し、テストを失敗させる（合格に数えない）
    source: FR15.2
  - id: BR1.6
    statement: #7 の直しを二段で確かめ、結果を記録する
    category: policy
    applies_to: U5 のコード生成の記録
    trigger: #7 の直しの後
    logic: IF 入れ替えた順の場合を起こせた THEN 直す前の確かめ方が入れ替えた順で落ち、直した後の確かめ方が自然の順と入れ替えた順の両方で通ることを記録する。IF 起こせなかった THEN BR2.5 と同じく 30 分を上限に試み、範囲と見立てを記録する
    violation: 記録が無ければ確かめは済んでいないとみなす
    source: FR15.2
  - id: BR2.1
    statement: AdminForbiddenProvider.test.tsx の1件は、効果の回数を写す前に、初めの効果が動いたことを待つ
    category: validation
    applies_to: 「keeps the same function before and after the URL becomes forbidden」
    trigger: 画面の部品が現れた後、効果の回数を写す前
    logic: IF 効果の回数がちょうど 1 に達したことを待ちの確かめ（waitFor）で確かめた THEN その値を写して以降の確かめに使う
    violation: 待ちが上限までに満たされなければテストを失敗させる
    source: FR15.1
  - id: BR2.2
    statement: 403 を知らせた後の確かめ（同じ関数のまま、効果の回数が変わらない）を保つ
    category: validation
    applies_to: 同じテスト
    trigger: 403 を知らせた後
    logic: IF 表示が「権限が無い」に変わり、口の関数が同じで、効果の回数が写した値から変わらない THEN 合格
    violation: テストを失敗させる
    source: FR15.1
  - id: BR2.3
    statement: テストの時間の上限は延ばさない
    category: constraint
    applies_to: 同じテストと、その待ちの確かめ
    trigger: U5 のコード生成
    logic: IF 直しが時間の上限（Vitest の既定 5 秒、waitFor の既定）を延ばすことを要する THEN 原因を確かめるまで延ばさない（team.md）
    violation: 延ばす変更は採らない
    source: FR15.1
  - id: BR2.4
    statement: 効果の前に写す形が直す前の確かめ方で必ず落ちることを試しで確かめ、記録してから試しの変更を戻す
    category: policy
    applies_to: U5 のコード生成の記録
    trigger: BR2.1 の直しの前後
    logic: IF 試し（効果の前の時点で写す形にする）で直す前の確かめ方が落ち、直した確かめ方が通った THEN 結果を記録し、試しの変更は戻してテストには残さない（Q2 A）。試しの形の具体（R-03）はコード生成の計画で決める
    violation: 記録が無ければ確かめは済んでいないとみなす
    source: FR15.2
  - id: BR2.5
    statement: 確実に起こせないときの再現の試みは、1件ごとに 30 分を上限とする
    category: policy
    applies_to: K-57・K-58 の二段の確かめ
    trigger: 疑った競り合い・待ちの抜けを確実に起こせなかったとき
    logic: IF 30 分の試みで起こせなかった THEN 試みた範囲（回数・負荷の掛け方）と見立てを記録して進める（不安定と確かめられていない扱い）
    violation: 上限を超えて試みを続けない
    source: FR15.2
  - id: BR3.1
    statement: 合否の物差しは dslMixed の memory.peak / memory.max、線は 90% 未満、コンテナの上限は 2g のまま
    category: constraint
    applies_to: dslMixed の memory.peak の測り
    trigger: Build and Test での測り
    logic: IF 物差し・線・上限を変える案が出た THEN 採らない（変えるのは要件定義で依頼者が明示に決めたときだけ）
    violation: 変えた測りの結果は合否に使わない
    source: FR16.1
  - id: BR3.2
    statement: 測りの間の anon の最大と、peak の時の内訳を並べて記録する
    category: calculation
    applies_to: dslMixed の測りの記録
    trigger: 各回の測りの後
    logic: IF 1回の測りが終わった THEN anon の最大 / memory.max と、peak の時の anon・file・inactive_file・kernel を記録する（合否には使わない）
    violation: 記録が欠けた回は数え直す
    source: FR16.2
  - id: BR3.3
    statement: 記録は1秒ごとに、コンテナの中で回る1つの読み取りのループで取る
    category: policy
    applies_to: perf/ の記録の台本
    trigger: dslMixed を流す前から終わりの後まで
    logic: IF 測りを始めた THEN 1秒ごとに1行（functional-spec.md 3節の列）を書き、終わりの後 60 秒まで続ける
    violation: 間隔が空いた回は記録に残し、anon の最大の取りこぼしの見込みとして書く
    source: FR16.2
  - id: BR3.4
    statement: 測りの条件をそろえ、確かめてから片付ける
    category: policy
    applies_to: 使い捨ての環境での測り
    trigger: 各回の前後
    logic: IF 回を始める THEN 使い捨てのアプリを作り直して前の最大を消し、台本の全体を caffeinate -dims で包み、pmset で眠らない状態を確かめる。IF 回が終わった THEN pmset -g log で眠りが無かったか、WARN の件数と内訳を確かめてから環境を消す
    violation: 眠りが入った回は数えずに流し直す
    source: FR16.1
  - id: BR3.5
    statement: 2回流して、2回とも 90% 未満のときだけ Met とする
    category: calculation
    applies_to: FR16.4 の判定
    trigger: 2回の測りの後
    logic: IF 2回とも memory.peak / memory.max < 0.9 THEN Met。IF どちらかが 0.9 以上 THEN Not Met
    violation: Not Met のときは BR4.3 に従う
    source: FR16.4
  - id: BR4.1
    statement: ページキャッシュを減らす手は、使い捨ての環境で内部DB の接続先を上書きして試し、配備物は変えない。上書きが dslMixed の使い捨ての環境で効くことと、H2 の設定の渡し方を、コード生成で確かめる
    category: constraint
    applies_to: FR16.3 の試し、perf/ の台本
    trigger: U5 のコード生成（確かめと台本の口）と Build and Test（試し）
    logic: IF コード生成 THEN dslMixed を流す台本と使い捨ての環境の起動を読み、PERF_DB_URL が使い捨てのアプリの MASTERSMITH_DB_URL に渡るかを確かめる。IF 渡らない THEN 使い捨ての環境の接続先を上書きする口を台本に足す（既存の dsl-timing.sh の PERF_DB_URL と同じ形）。IF H2 の設定 RETENTION_TIME を接続先の URL で渡せない THEN 接続の初期化（INIT=SET RETENTION_TIME ...）で渡す。IF どちらでも渡せない THEN 試しを行わず、確かめた範囲を記録して依頼者に諮る。IF 試す（Build and Test）THEN DEFRAG_ALWAYS=TRUE を保ったまま、決めた渡し方で H2 の設定を足して測る。application.yaml・Dockerfile・compose.yaml は変えない（Q4 A）
    violation: 配備物を変えた測りと、上書きが効いたことを確かめていない測りは、試しの結果に使わない
    source: FR16.3
  - id: BR4.2
    statement: 効いた手を配備物に取り込むかは、その時点で依頼者に諮る
    category: policy
    applies_to: FR16.3 の結果
    trigger: 手を足した測りが 2回とも 90% 未満だったとき
    logic: IF 手が効いた THEN 取り込みの案（変える設定と前後の測りの値）を示して依頼者に諮る。取り込むと決まるまで配備物は変えない
    violation: 諮らずに取り込まない
    source: FR16.3
  - id: BR4.3
    statement: 手を試しても線を超えたら、内訳とともに記録して依頼者に諮る
    category: policy
    applies_to: FR16.3・FR16.4 の結果
    trigger: 手を足した測りでも Not Met のとき
    logic: IF 手を足しても 90% 以上 THEN 内訳（anon・file・H2 のファイルの伸び）と試した手を記録し、扱いを依頼者に諮る。2g を上げる手は使わない
    violation: 目標を緩めて Met にしない
    source: FR16.3
```

## 要約

| ID | 群 | 決まり |
|---|---|---|
| BR1.1 | #7 | 監査の2行を順に頼らず中身で見分ける |
| BR1.2 | #7 | 直列になったことの確かめを保つ |
| BR1.3 | #7 | 409 `ROLE_BUSY` の分岐を変えない |
| BR1.4 | #7 | 本番の監査の書き方は変えない |
| BR1.5 | #7 | 先の保存の監査の書き込みを印で特定して止め、後の保存の監査が書かれたら放す。上限で放されたら失敗。2つの場合で毎回流す |
| BR1.6 | #7 | 二段で確かめて記録する |
| BR2.1 | 画面のテスト | 写す前に初めの効果を待つ |
| BR2.2 | 画面のテスト | 403 の後の確かめを保つ |
| BR2.3 | 画面のテスト | 時間の上限を延ばさない |
| BR2.4 | 画面のテスト | 試しで落ちることを確かめ、記録して戻す |
| BR2.5 | 二段の確かめ | 起こせないときは 30 分を上限に試み、記録する |
| BR3.1 | 測り方 | 物差しは memory.peak/max・90% 未満・2g |
| BR3.2 | 測り方 | anon の最大と内訳を並べて記録する |
| BR3.3 | 測り方 | 1秒ごとに1つのループで記録する |
| BR3.4 | 測り方 | 条件をそろえ、確かめてから片付ける |
| BR3.5 | 測り方 | 2回とも 90% 未満で Met |
| BR4.1 | ページキャッシュ | 接続先の上書きで試し配備物は変えない。`PERF_DB_URL` が dslMixed で効くか・`RETENTION_TIME` の渡し方（URL か INIT）をコード生成で確かめ、だめなら口を足すか記録して諮る |
| BR4.2 | ページキャッシュ | 効いたら取り込むかを諮る |
| BR4.3 | ページキャッシュ | 効かなければ内訳とともに記録して諮る |

## Sources

- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR15・FR16）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-design-questions.md`（Q1〜Q5: すべて A、Requested Changes Feedback、まとめの確認: Looks correct）
- レビューの記録: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u5-carryover-tests/43883aac7901769a/`（R-01〜R-05）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`（K-57・K-58・K-59）
- コード: `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java`、`backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`、`perf/dsl-timing.sh`（`PERF_DB_URL`）、`perf/README.md` 152 行
- 決まり: `aidlc/spaces/default/memory/team.md` Testing Posture（不安定なテストの二段、`waitFor`、時間の上限、物差しの変え方）、`aidlc/spaces/default/memory/project.md` Testing Posture（caffeinate・pmset、作り直し、確かめてから片付ける）
