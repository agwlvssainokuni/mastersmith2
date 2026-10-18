# 機能の仕様 — U5 u5-carryover-tests

U5 は、前の Intent から持ち越した3件を受け持つ（種類 `library`、部品を増やさない、ストーリーは無い）。

- 不安定なテストの1件目: `RoleConcurrencyIT` の #7（K-57）
- 不安定なテストの2件目: `AdminForbiddenProvider.test.tsx` の1件（K-58）
- `dslMixed` の `memory.peak` の測り直し（K-59）

この文書は、直し方と確かめの手順、測りの台本の形の正とする。決まりの中身の正は `rules.md`。エンティティは無い（`entities.md`）ため、エンティティの関係の図は置かない。承認の場（2026-10-18 Request Changes）の決定は8節に書く。

## 1. 原因の見立て（どちらも未検証の仮説）

### 1.1 K-57 `RoleConcurrencyIT` の #7

- 2件の保存は行の排他で直列になる（`backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` 145〜166 行。1件目を排他の後で止め、2件目を排他の前まで進めてから1件目を放す）。
- 監査は保存の確定の後に、要求ごとのスレッドで、新しいトランザクションで書かれる（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` 260〜263 行の AFTER_COMMIT、`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` 51 行の REQUIRES_NEW）。
- 1件目が確定してから自分の監査を書くまでの間に、2件目は排他を得て確定し、先に監査を書きうる。監査の行の ID は書いた順に振られる。
- テストは行を ID の順に読み（173 行）、1行目と2行目の中身を決め打ちしている（177・178 行）。
- 前の Intent の CI の失敗（1行目に後の保存の監査が来た）は、この競り合いで説明できる。ただし手元の5回では再現しておらず、確かめた原因ではない。
- この競り合いがあるため、入れ替えの待ち合わせで「最初に書き込みの直前に来たスレッド」を止めると、後の保存が先に来たときに後の保存を止めてしまい、入れ替えが起きない（承認の場の R-01）。そのため、止める相手は先の保存に決めて特定する（2.1）。

### 1.2 K-58 `AdminForbiddenProvider.test.tsx`

- テストの部品は、ログインの状態を Promise の後で決める部品の中で、遅れて描かれる（`frontend/src/app/login-state/LoginStateGate.tsx` 77〜93 行、`frontend/src/app/testing/renderWithProviders.tsx` 118〜145 行）。
- `findByTestId` は要素が現れた時点で解ける。その時点では、描画の後の効果（`frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` 66〜69 行の `effectRuns += 1`）がまだ動いていないことがある。
- 写した値は 0（235 行）になり、後で効果が動くと 1 になって、確かめ（241 行）が `expected 1 to be +0` で落ちうる。値の形は CI の記録と一致する。
- 口の関数は Provider が生きている間は同じ関数を保つ（`frontend/src/app/admin-forbidden/AdminForbiddenProvider.tsx` 82・120〜128 行）。そのため、2回目の効果で回数が増えた形ではないと読む。

## 2. 直し方と二段の確かめの手順

### 2.1 #7（BR1.1〜BR1.6）

1. テストの補助 `backend/src/test/java/cherry/mastersmith/audit/testsupport/` に、監査の書き込みの順を入れ替える待ち合わせを置く。形は既存の `AuditWriteBarrierConfig` と同じで、書き込みの時間を測る呼び出し（`LongSupplier`。1回の書き込みで、直前と終わりに同じスレッドで2回呼ばれる）を差し替える。既存の `AuditWriteBarrier` の「全スレッドが加わる」作りは使い回さず、止める相手を1つのスレッドに決める形にする。
2. 止める相手の特定（BR1.5、R-01）: 先の保存（テストが先に送る要求）を処理するサーバーのスレッドに印を付ける。`TestRoleBarrier` の排他の後の止める点（`AFTER_LOCK` の `Gate`）に来たスレッドを記録し、テストは `first.awaitArrival()` の後にそのスレッドを待ち合わせの止める相手として渡す。監査は確定の後に同じスレッドで書かれる（`AuditEventListener` の AFTER_COMMIT）ため、この印で先の保存の監査の書き込みを特定できる。印で特定できない形が分かったときは、出来事の中身の後の値（主権限 READ）で特定する形に替え、替えたことをコード生成の記録に書く。
3. 止め方と放し方: 止める相手のスレッドだけを、監査の書き込みの直前の呼び出しで止める。ほかのスレッドは止めない。止める相手でないスレッドの、書き込みの終わりを測る呼び出し（後の保存の監査が書かれたこと）で放す。止めたスレッドと放す側のスレッドは分けて持ち、同じスレッドの2回目の呼び出しと取り違えない。上限は `TestRoleBarrier.WAIT_LIMIT`。準備していない間は止めずに通す。
4. 上限の時間で放されたときは、待ち合わせが「入れ替えが起きなかった」ことを持ち、テストはそれを確かめて失敗にする（合格に数えない）。
5. 直す前の確かめ方（行の順の決め打ち）を、入れ替えた順で流す。落ちることを確かめて記録する（二段の1段目）。
6. 確かめ方を、中身で2行を見分ける形に直す（BR1.1）。最後の値が FULL であることと、後の行の前の値が READ であることの確かめは保つ（BR1.2）。409 `ROLE_BUSY` の分岐は変えない（BR1.3）。
7. 直した確かめ方が、自然の順と入れ替えた順の両方で通ることを記録する（二段の2段目、BR1.6）。2つの場合はテストに残し、毎回流す（Q2: A）。
8. 手順5で入れ替えが起きなければ、30 分を上限に試み、範囲と見立てを記録する（BR2.5）。

待ち合わせの口の形（説明のための断片。名前はコード生成で確定する）:

```java
/** 先の保存の監査の書き込みを、後の保存の監査が書かれるまで止める待ち合わせ。 */
public interface AuditWriteReorderBarrier {
    /** 止める相手（先の保存の要求を処理するスレッド）を決めて準備する。 */
    void armFor(Thread heldThread);
    /** 上限の時間で放された（入れ替えが起きなかった）か。 */
    boolean releasedByTimeout();
    /** 準備をやめ、止めているスレッドがあれば放す。 */
    void disarm();
}
```

確かめ方の形（説明のための擬似コード）:

```
rows = 対象のロールの ROLE_PERMISSION_CHANGED の監査の行（順は問わない）
assert rows の件数 == 2
assert rows の中に「前: 空, 後: READ」がちょうど1つ
assert rows の中に「前: READ, 後: FULL」がちょうど1つ
入れ替えた順の場合: assert releasedByTimeout() == false
```

### 2.2 画面のテスト（BR2.1〜BR2.5）

1. 試しとして、効果の前の時点で写す形にする（部品の中の、描画の直後に動く効果で値を写す）。直す前の確かめ方が必ず落ちることを確かめる。試しの形の具体（写す位置）は、コード生成の計画で決める（8.2 の R-03）。
2. 同じ試しの形で、写す前に初めの効果を待つ形（BR2.1）が通ることを確かめる。
3. 結果（落ちた文・通った回）を記録し、試しの変更を戻す（BR2.4）。
4. 本番のテストを、写す前に効果の回数がちょうど 1 に達したことを待つ形に直す。403 の後の確かめは保つ（BR2.2）。時間の上限は延ばさない（BR2.3）。
5. 手順1で確実に落とせなければ、30 分を上限に試み（くり返し・負荷の下での実行）、範囲と見立てを記録する（BR2.5）。

### 2.3 確かめの記録と統合

- 記録の置き場は、コード生成の段の成果物（直す前後の結果・試しの範囲・見立て・止める相手の特定の仕方）。
- 統合は squash。テストと `perf/` と文書だけの変更のため、`./gradlew verify` だけで統合する（team.md の Way of Working）。
- 統合の後の CI は、試行ごとに確かめて記録する（`project.md` の学び）。

## 3. memory.peak の測り方の台本の形（BR3.1〜BR3.5）

`perf/README.md` の「(b) 大きな入力のヒープ」の節に、`dslMixed` の間の記録の台本と読み方を足す。

| 項目 | 決めたこと |
|---|---|
| 読む間隔 | 1秒ごと。コンテナの中で回る1つの読み取りのループで読む（`docker exec` を1秒ごとに起こさない） |
| 列（TSV の1行） | 時刻（UTC）・`memory.current`・`memory.peak`・`memory.stat` の `anon`・`file`・`inactive_file`・`active_file`・`kernel`・内部DB のファイル（`/app/data/mastersmith.mv.db`）の大きさ |
| 別の記録 | ヒープの使用（既存の `heapwatch`、0.5 秒ごと） |
| 範囲 | 作り直しの直後から記録を始め、20 秒後に `dslMixed`（VUS 10・3 分・10 MiB の DSL）、終わりの後 60 秒まで |
| 判定 | `memory.peak / memory.max < 0.9` を各回で見る。2回流し、2回とも満たせば Met（BR3.5） |
| 並べる値 | `anon` の最大 / `memory.max`、peak の時の内訳、`file` の伸びと内部DB のファイルの伸び（BR3.2） |
| 条件 | 各回の前に `--force-recreate`。台本の全体を `caffeinate -dims` で包む。前後の `pmset` の確かめ。WARN を数えてから片付ける。配備したアプリは測る間だけ止める（BR3.4） |
| 接続先 | 既定（`application.yaml` の `DEFRAG_ALWAYS=TRUE`）で測る。4節の試しのときだけ、確かめた口で上書きし、使った値を記録に残す |

1回の流れ:

1. 配備したアプリを止める。
2. 使い捨てのアプリを作り直す。
3. 記録のループを始める。
4. `dslMixed` を流す。
5. 60 秒後に記録を止める。
6. `memory.peak`・`memory.max`・状態（OOMKilled）を読む。
7. WARN を数える。
8. `pmset -g log` で眠りが無かったかを確かめる。

2回目は手順2から行い、終わったら配備したアプリを起動し直す。

## 4. ページキャッシュを減らす手の試し方（BR4.1〜BR4.3）

- 前の Intent の内訳では、`anon` は 68% 前後で横ばいで、`file` が投入のたびに増え続けた（485 MiB）。そのため、試す手は内部DB のファイルの伸びを抑える方向とする。
- 候補は H2 の `RETENTION_TIME`（古いデータを残す時間）の短縮。空いた場所を早く使い直し、ファイルの伸びを抑える見込み。効くかは未確認で、Build and Test の測りで確かめる。
- 試す手は、内部DB の接続先を上書きして `DEFRAG_ALWAYS=TRUE` を保ったまま足し、3節の台本で2回測る。配備物（`backend/src/main/resources/application.yaml`・`Dockerfile`・`compose.yaml`）は変えない。

### 4.1 コード生成で確かめること（R-02）

| 順 | 確かめること | 結果による扱い |
|---|---|---|
| 1 | `dslMixed` を流す台本と使い捨ての環境の起動を読み、`PERF_DB_URL` が使い捨てのアプリの `MASTERSMITH_DB_URL` に渡るかを確かめる。今の `PERF_DB_URL` は `perf/dsl-timing.sh`（141・142 行で使い捨ての環境の設定に `MASTERSMITH_DB_URL` を書く）と `perf/README.md` 152 行の `--storage` の比べ方にだけ書かれている | 渡る: そのまま使う。渡らない: 使い捨ての環境の接続先を上書きする口を、`dslMixed` を流す台本に足す（`dsl-timing.sh` の `PERF_DB_URL` と同じ形で、使った値を記録に残す）。配備物は変えない |
| 2 | H2 の `RETENTION_TIME` を接続先の URL の設定（`;RETENTION_TIME=...`）として渡せるかを、H2 の資料とソースで確かめる | 渡せる: URL で渡す。渡せない: 接続の初期化（`INIT=SET RETENTION_TIME ...`）で渡す |
| 3 | 1・2 のどちらかが成り立たない | 試しを行わず、確かめた範囲を記録して依頼者に諮る |

- どの形で渡したかと、使い捨てのアプリで実際に効いたこと（起動の後に設定の値を読み取りで確かめる）を、Build and Test の測りの記録に残す。確かめていない測りは試しの結果に使わない（BR4.1）。

### 4.2 結果の扱い

- 効いた（2回とも 90% 未満）: 取り込むかを依頼者に諮る（BR4.2）。
- 効かない: 内訳と試した手を記録して諮る（BR4.3）。
- コンテナの上限 2g を上げる手は使わない。

## 5. 決まりの要約（`rules.md` から）

| 群 | ID | 中身 |
|---|---|---|
| BR1 #7 | BR1.1〜BR1.6 | 中身で見分ける・直列の確かめを保つ・`ROLE_BUSY` の分岐を保つ・本番の監査は変えない・先の保存の監査を特定して止める順を入れ替える待ち合わせ・二段の記録 |
| BR2 画面のテスト | BR2.1〜BR2.5 | 写す前に初めの効果を待つ・403 の後の確かめを保つ・上限を延ばさない・試しで確かめて戻す・30 分を上限に試みる |
| BR3 測り方 | BR3.1〜BR3.5 | 物差しと線と 2g・`anon` の最大と内訳・1秒ごとの記録・条件をそろえる・2回とも 90% 未満で Met |
| BR4 ページキャッシュ | BR4.1〜BR4.3 | 接続先の上書きで試し配備物は変えない（`PERF_DB_URL` の効きと `RETENTION_TIME` の渡し方をコード生成で確かめる）・効いたら諮る・効かなければ記録して諮る |

## 6. 上流との差

- `inception/units-generation/unit-of-work.md` は、U5 を「配備物を変えない」としている（13 行）。一方、FR16.3 は「ページキャッシュを減らす手（H2 のファイルの読み書きの見直しなど）を試す」としている。
- Q4: A で、試しは Build and Test の使い捨ての環境で接続先の上書きを使って行い、U5 では配備物を変えないことにした。両方を満たす読み方で、承認済みの文書は書き換えない。
- `PERF_DB_URL` が `dslMixed` の使い捨ての環境で効かないときに足す接続先の上書きの口は、`perf/` の台本の変更で、配備物ではない（4.1、R-02）。
- 効いた手を配備物に取り込むと決まったときは、配備物が変わる。そのため、その時点で U5 の「配備物を変えない」との差として記録し、取り込みの置き場（どの段・どの Bolt か）を依頼者と決める。

## 7. 後の段に回すこと

- コード生成:
  - 2.1 の止める相手の特定（印で特定できるか、出来事の中身で特定する形に替えたか）を記録する。
  - 4.1 の確かめ（`PERF_DB_URL` の効き、`RETENTION_TIME` の渡し方）と、要るなら台本の口の追加。どちらもだめなら記録して諮る。
  - 8.2 の Minor（R-03〜R-05）を計画で拾う。
- Build and Test:
  - 3節の台本での `dslMixed` の2回の測り（FR16.1・FR16.2・FR16.4）
  - 4節の手の試し（FR16.3）と、渡した形・効いたことの記録
  - #7 と画面のテストの二段の確かめの結果の受け入れ（FR15.2。確かめそのものはコード生成で行い、記録を Build and Test でたどる）
- 効いた手を配備物に取り込むかを、Build and Test の結果の時点で依頼者に諮ること。

## 8. 承認の場の決定（2026-10-18 Request Changes）

機能設計の段の承認の場（2026-10-18）で、依頼者は Request Changes を選び、次のとおり決めた（`functional-design-questions.md` の「Requested Changes Feedback」。そのうえでまとめを Looks correct で確かめ直した）。指摘の元は、この単位のレビューの記録（`aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u5-carryover-tests/43883aac7901769a/`）の R-01〜R-05。

### 8.1 この書き直しで直した指摘

| 指摘 | 重さ | 直したこと | 直した場所 |
|---|---|---|---|
| R-01 止める相手 | Major | 入れ替えた順の待ち合わせは、「最初に来たスレッド」ではなく、先の保存の監査の書き込みを特定して止める。特定は、先の保存の要求のスレッドに付ける印（`TestRoleBarrier` の `AFTER_LOCK` に来たスレッド）で行い、印で特定できなければ出来事の中身の後の値で行う。後の保存の監査が書かれたら放す。止めたスレッドと放す側のスレッドは分けて持つ。上限の時間で放されたら、入れ替えが起きなかったとしてテストを失敗にする | `rules.md` の BR1.5、1.1 の最後の項目、2.1 の手順1〜4 と断片・擬似コード |
| R-02 試し方の前提 | Major | `PERF_DB_URL` が `dslMixed` の使い捨ての環境で効くかを、コード生成で台本を読んで確かめる。効かなければ、接続先を上書きする口を台本に足す。`RETENTION_TIME` は、接続先の URL で渡せなければ、接続の初期化（`INIT=SET RETENTION_TIME ...`）で渡す。どちらもだめなら記録して諮る | `rules.md` の BR4.1、3節の「接続先」、4節・4.1、6節、7節 |

### 8.2 拾わない Minor（この単位の記録に残し、コード生成の計画で拾う）

| 指摘 | 重さ | 扱い |
|---|---|---|
| R-03 画面のテストの試しの形と「ちょうど 1」の前提 | Minor | 試しの形の具体（写す位置）と、再描画で効果が再び動く場合の読み替え（1 以上を待つか）を、コード生成の計画で決める（2.2 の手順1、BR2.4） |
| R-04 入れ替えた順の場合の 409 `ROLE_BUSY` | Minor | 入れ替えた順の場合で 409 を失敗として扱うか、入れ替えの確認を 409 の分岐より前に置くかを、コード生成の計画で決める（BR1.3） |
| R-05 自然の順の場合の書き方 | Minor | 自然の順も待ち合わせで確実に起こすか、「準備しない場合は順を問わない」と BR1.1 の趣旨に合わせて書き直すかを、コード生成の計画で決める（BR1.5） |

## Sources

- 単位: `aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work.md`（U5）、`aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work-story-map.md`（U5 はストーリー無し）、`aidlc/spaces/default/intents/261016-business-data-views/inception/delivery-planning/bolt-plan.md`（B1）
- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR15・FR16）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-design-questions.md`（Q1〜Q5: A、Requested Changes Feedback、まとめの確認: Looks correct）
- レビューの記録: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u5-carryover-tests/43883aac7901769a/`（R-01〜R-05）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`（K-57・K-58・K-59）
- 前の Intent の記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/perf-run-notes.md`（memory.peak の内訳と時系列）、`aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md`（CI の失敗の文）
- コードと設定: 1節の各ファイル、`backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java`（`Gate`・`WAIT_LIMIT`）、`backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`（`LongSupplier` の差し替え）、`perf/dsl-timing.sh`（45・141・142・164 行の `PERF_DB_URL`）、`perf/README.md`（7・146・150・152・829〜836 行）、`compose.yaml` 63 行・`docker/perf/compose.yaml` 57 行（上限 2g）、`Dockerfile` 52 行、`backend/src/main/resources/application.yaml` 166 行
