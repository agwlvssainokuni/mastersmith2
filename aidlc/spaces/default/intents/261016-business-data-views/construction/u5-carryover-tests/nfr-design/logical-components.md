# 論理の部品 — U5 u5-carryover-tests

U5 は、本番の部品を足さない。足すのは、テストの補助と `perf/` の台本だけ。この文書では次のことを決める。

- 部品の一覧と置き場
- 失敗の範囲
- 共有の資源
- 入れ替えの待ち合わせの口の形（Q1: A）

セキュリティの決まりの中身は `security-design.md` を正とする。

## 上流の ID との対応表

| この文書の節 | 当てる NFR 要件 | 持ち越した論点 |
|---|---|---|
| 1 部品の一覧 | NFR4.7・NFR4.8・NFR4.9・NFR7.1 | — |
| 2 失敗の範囲と分け方 | NFR4.8・NFR7.2 | — |
| 3 共有の資源と後始末 | NFR7.2 | — |
| 4 入れ替えの待ち合わせの口 | NFR7.2・NFR7.3 | NFR 要件のレビューの R-03、機能設計のレビュー（放す条件）、この段の Q1: A |

## 1. 部品の一覧

| 部品 | 置き場 | 役割 | 新しく足すか |
|---|---|---|---|
| 入れ替えの待ち合わせ `AuditWriteReorderBarrier` と、その設定（`@TestConfiguration`。監査の書き込みの時間を測る `LongSupplier` を `@Primary` で差し替える） | `backend/src/test/java/cherry/mastersmith/audit/testsupport/` | 先の保存の監査の書き込みを、後の保存の監査が書かれるまで止める | 足す |
| `TestRoleBarrier` のスレッドの記録（`Gate.arrivedThread()`・`Signal.passedThread()`） | `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java` | 止める相手（先の保存）と放す相手（後の保存）のスレッドを特定する | 既存に足す |
| `RoleActors.Actor` の `toString` | `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java` | メールアドレスとトークンを伏せる（Q2: A） | 既存を直す |
| `RoleConcurrencyIT` の #7 | `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` | 自然の順と入れ替えた順の2つの場合を流す（BR1.5） | 既存を直す |
| `PERF_DB_URL` の形を確かめる関数 | `perf/`（台本の中。2つの流れから呼ぶ） | 許す一覧の形だけを通す（`security-design.md` の S1） | 足す |
| 記録のループの台本 | `perf/`、`perf/README.md` の (b) 節 | 1秒ごとの TSV（機能設計の3節の列） | 足す |

## 2. 失敗の範囲と分け方

| 部品 | 失敗したときに響く範囲 | 分け方 |
|---|---|---|
| 入れ替えの待ち合わせ | `RoleConcurrencyIT` の Spring の文脈だけ | `@Import` で `RoleConcurrencyIT` にだけ入れる。既存の `AuditWriteBarrierConfig` も同じ `LongSupplier` を差し替えるため、2つを同じクラスに入れない。準備していない間は、すべての呼び出しを止めずに通す |
| `TestRoleBarrier` のスレッドの記録 | `TestRoleBarrier.Config` を入れる結合テスト | 記録を足すだけにし、止める・通すの振る舞いは変えない |
| `Actor` の `toString` | `RoleActors` を使うテスト | 値を読む口（`email()`・`token()`）は変えない |
| 形を確かめる関数 | 使い捨ての環境の起動の前 | 通らなければ起動しない。配備したアプリには触れない |
| 記録のループ | 使い捨てのアプリ（`127.0.0.1:18080`）だけ | 読むだけにする。cgroup v2 のファイルが読めない回は無効にする |

## 3. 共有の資源と後始末

- 共有の資源は次の2つ。
  - Spring の文脈の使い回し（同じ設定のテストのクラスで文脈が共有される）
  - 差し替えた `LongSupplier` の Bean（監査の書き込みのたびに呼ばれる）
- `RoleConcurrencyIT` の `@AfterEach`（今は 117〜119 行で `barrier.reset()`）で、`reorderBarrier.disarm()` も呼ぶ。
  - テストが失敗しても、止めているスレッドを必ず放す。
  - 準備の状態を、次のテストに残さない（NFR7.2）。
- `disarm()` は何度呼んでもよい形にする。

## 4. 入れ替えの待ち合わせの口（Q1: A）

### 4.1 口の形

形の例（説明のための断片。名前はコード生成で確定する）:

```java
/** 先の保存の監査の書き込みを、後の保存の監査が書かれるまで止める待ち合わせ。 */
public interface AuditWriteReorderBarrier {
    /** 止める相手と放す相手のスレッドを決めて準備する。 */
    void armFor(Thread held, Thread releaser);
    /** 上限の時間で放されたか（入れ替えが起きなかった）。 */
    boolean releasedByTimeout();
    /** 止める相手が、監査の書き込みの直前に来たか。 */
    boolean heldArrived();
    /** 準備をやめ、止めているスレッドがあれば放す（何度呼んでもよい）。 */
    void disarm();
}
// TestRoleBarrier に足す口: Gate.arrivedThread()、Signal.passedThread()
```

### 4.2 振る舞い

- 書き込みの時間を測る呼び出しは、1回の書き込みで同じスレッドから2回来る。1回目が直前、2回目が終わり（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` 412〜414 行）。
- 止めるのは、`held` の1回目の呼び出しだけ。同じスレッドの2回目（書き込みの終わり）は止めない。
- 放すのは、`releaser` の2回目の呼び出し（後の保存の監査の書き込みの終わり）だけ。ほかのスレッドの監査では放さない（Q1: A）。
- 放す条件が止める相手の来る前に満たされていたら、止めずに通す。順は自然に入れ替わったことになるため、`releasedByTimeout()` は偽とする。
- 待ちの上限は `TestRoleBarrier.WAIT_LIMIT`（20 秒）。上限で放したら、`releasedByTimeout()` を真にする。
- 準備していない間は、すべての呼び出しを止めずに通す。
- 止めたスレッドと放す相手は、別々の値で持つ。同じスレッドの2回目と取り違えない。

### 4.3 テストでの使い方（入れ替えた順の場合）

1. 先の保存を `first.awaitArrival()` まで進める。`first.arrivedThread()` が止める相手になる。
2. 後の保存を `second.awaitPassed()` まで進める。`second.passedThread()` が放す相手になる。
3. `armFor(held, releaser)` を呼んでから、`first.release()` を呼ぶ。
4. 結果は、後の保存（`result(b)`）を先に待ち、次に先の保存（`result(a)`）を待つ。止まった先の保存の上限と、結果を待つ上限が重なって取り違えるのを避けるため。
5. 次の3つを確かめる。
   - `heldArrived()` が真で、`releasedByTimeout()` が偽であること（真ならテストを失敗にする。BR1.5）
   - 「後: FULL」の行の ID が「後: READ」の行の ID より小さいこと（入れ替えが実際に起きたこと）。これが二段の確かめの1段目の前提になる（BR1.6）。
   - 中身で2行を見分ける確かめ（BR1.1・BR1.2）

- 自然の順の場合は準備しない。行の順は問わない。書き方の具体はコード生成の計画で決める（R-05）。
- `sleep` は使わず、上限も延ばさない（NFR7.3）。

## 上流との差

一点ある。機能設計の 2.1 の手順3 は、放す条件を「止める相手でないスレッドの、書き込みの終わりの呼び出し」としていた。この段の Q1: A で、これを「後の保存のスレッド（`releaser`）の、書き込みの終わりの呼び出し」に狭めた。

- 狭めたことで、ほかのスレッドの監査で早く放されることが無くなる。
- 口は、機能設計の断片の `armFor(Thread heldThread)` から、`armFor(Thread held, Thread releaser)` に変わる。あわせて `heldArrived()` を足した。
- 承認済みの機能設計の文書は書き換えない。

## 後の段に回すこと

コード生成の計画で行うこと:

- R-03: 画面のテストの試しで写す位置と、「ちょうど 1」と読む前提
- R-04: 入れ替えた順の場合に返る 409 `ROLE_BUSY` の扱い（確かめを 409 の分岐より前に置くか、失敗として扱うか）
- R-05: 自然の順の場合の書き方
- `PERF_DB_URL` の確かめ（機能設計の 4.1）と、悪い入力の試し（`security-design.md` の S1.3）
- `dslMixed` の手順への、接続先の上書きの口の追加（`security-design.md` の S1.1）
- 二段の確かめの記録（BR1.6・BR2.4）

## Sources

- 質問と確認: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-design/nfr-design-questions.md`（Q1: A、Q2: A、Looks correct）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/security-requirements.md`（NFR7.2 ほか）、`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/tech-stack-decisions.md`（TS2・TS4）
- NFR 要件のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u5-carryover-tests/00d44a7a2452e6b7/1.json`（R-03）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`（2.1・8.2）、`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/rules.md`（BR1.1・BR1.2・BR1.5・BR1.6）
- コード:
  - `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java`（45 行 `WAIT_LIMIT`、60〜128 行 `Gate`・`Signal`、189〜201 行）
  - `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`（57〜62 行、126〜138 行）
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（412〜414 行）
  - `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java`（69 行、117〜119 行、#7）
  - `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java`（53 行）
  - `docker/perf/compose.yaml`（28 行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture の待ち合わせ、Code Style の `testsupport`）

## Assumptions & Open Questions

- [assumption] 後の保存のスレッドは、`BEFORE_LOCK` の知らせを通ったスレッドと同じスレッドで監査を書く。監査は確定の後に、要求ごとのスレッドで書かれるため（`AuditEventListener` の AFTER_COMMIT）。違う形だと分かったら、機能設計の BR1.5 の代わりの手（出来事の中身の後の値で特定する）に替え、コード生成の記録に書く。
- [assumption] `RoleConcurrencyIT` のほかのテストは、準備していない待ち合わせを通っても振る舞いが変わらない。
