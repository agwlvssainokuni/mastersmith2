# 拡張性の設計 — U2 u2-businessdata-write

## 捨ての試しの結果（要点）

全体（版・設定）は reliability-design.md の 1節にある。拡張性に関わるものは次の3つ。

- **W10（大きな本文のヒープ）**: 最悪の本文は、100 カラム × 絵文字 10,000 字（4,001,149 バイト）。

  | アプリの側の値 | PG | MySQL | MariaDB |
  |---|---|---|---|
  | JSON の読み込みの割り当て | 7.5 MB | 7.5〜7.9 MB | 7.5 MB |
  | 型つきの値への写し | 10 MB | 10 MB | 10 MB |
  | INSERT の実行の割り当て | 10 MB | **42.7 MB** | 18.3 MB |
  | 同時に持ったときに残る量 | 10 本で 81.5 MB | 5 本で 40.4〜41.4 MB | 10 本で 81.4 MB・5 本で 40.9 MB |

- **W10（対象DB の側）**
  - **MySQL（メモリの上限 768m）は、10 本の同時の INSERT で OOMKilled になった。** 起動し直して、もう一度起きることを確かめた。
  - 5 本（書き込みのプールの上限）なら、6 回続けても止まらなかった。ただし、最大 742 MiB / 768 MiB（97%）だった。3 本なら余裕がある。
  - MariaDB（512m）は 10 本でも止まらなかった（448.9 MiB）。PG（512m）も止まらなかった。
- **W12（接続を捨てる）**: 管理者が返した後に `evictConnection` を呼ぶと、次に借りたときに新しい接続になり、プールの数も戻った。

**設計への意味**
- アプリのヒープは小さい（10 本で約 80 MB）。最大ヒープは 1g（上限 2g の 50%）。
- 対象DB の側の同時の書き込みを、書き込みのプールの上限 5 で抑えることが、MySQL を守る役目も持つ（F1: A）。

## 対応表

| U2 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR2.6 1要求で使う接続の数 | 1節 | そのまま |
| NFR2.7 書き込みの接続のプール | 2節・3節 | 5 のまま。10 に上げる案は MySQL では採らない（F1: A） |
| NFR2.8 大きな本文のメモリ | 4節 | 試しで裏付けた。場面は `bizCreateLarge` |
| NFR2.9 監査の行の増え方 | 5節 | R-03 の注記を足した（Q7: A） |

## 1. 1要求で使う接続の数（NFR2.6）

| API | 内部DB（同時の最大） | 対象DB の書き込み | 対象DB の読み取り | 順 |
|---|---|---|---|---|
| 作成・更新・削除 | 1 | 1 | 0 | 写しを読み終えて返す → 書き込みの口が1本を借りる（事前の問い合わせ・書き込みの文・確かめ）→ 返す → 監査を内部DB に書く |

- 書き込みの接続を借りている間は、内部DB の接続を 0 本にする。
- セーブポイント（Q2: A）と `connectionInitSql`（Q3: A）は、同じ1本の中で行う。接続は増えない。
- 失敗した接続を捨てるのは、管理者が返した後にする（W12）。開いているトランザクションの中で捨てると、PG・MySQL では管理者の巻き戻しが失敗した。
- 確かめは `BusinessDataConnectionUsageIT`（U1 と1つのクラス）に作成・更新・削除の3行を足し、書き込みの DataSource を数える列を足す。

## 2. 書き込みのプール（NFR2.7）

- 名前は `mastersmith-target-db-write`。既定の大きさは 5 で、環境変数で変えられる。
- 対象DB の側の接続は、読み取り 5 + 書き込み 5 の最大 10 本。
- 合格の条件は、承認済みのとおり次の3つ。
  - 各場面の p95 < 1 秒
  - 書き込みのプールの待ちの時間切れの累計が 0
  - 503 の件数が 0
- 借りるまでの待ちの最大は、記録だけにする。
- **NFR2.7 の「満たせなければ上限を 10 に上げる案」は、MySQL では採らない**（F1: A）。4 MiB 近い同時の書き込みが 10 本になると、perf の MySQL は止まる（W10）。満たせないときは、上限を上げる以外の扱いを Performance Validation で依頼者に諮る。

## 3. 上限に届く形の確かめ（NFR2.7）

- `bizWritePoolLimit`（10 VU・3 分）は、違反の道（セーブポイントへの戻しと確かめの1文を含む）で、最も長く接続を持つ。10 VU で上限 5 に必ず届く。
- 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、書き込みのプールの `hikaricp` の値（待ちの時間切れの累計・待ちの最大）を場面の前後で読む。
- 相手が止まったときは、接続を最大 10〜20 秒持ち続ける（reliability-design.md の 5節）。その間、ほかの要求は残りの本数と借りる待ち 3 秒で失敗する（UNAVAILABLE）。

## 4. 大きな本文のメモリ（NFR2.8）

**アプリの側の見積もり（W10 で裏付けた）**

| 部分 | 1要求 | 同時の最大 |
|---|---|---|
| JSON の読み込み・型つきの値への写し（一時の割り当て） | 約 18 MB | 10 VU で約 180 MB（若い世代で回収される） |
| INSERT の実行（ドライバー。一時の割り当て） | PG 10 MB・MySQL 43 MB・MariaDB 18 MB | 書き込みのプールの 5 本で、最大約 215 MB（MySQL） |
| 同時に持つ量 | 約 8 MB | 10 本で約 81 MB |

- 合わせても、最大ヒープ 1g の中で小さい。前の Intent の DSL の投入（10 MiB で GC の後も 573〜746 MB）よりも小さい。

**場面**
- `bizCreateLarge`（10 VU・短い回、成功の道）を置く。`bizWritePoolLimit` の p95 に大きな本文を混ぜないため。
- 合格の線は、承認済みのとおり、アプリのコンテナの `memory.peak` < 2g の 90%。`memory.stat` の `anon` の最大も並べる。
- 場面の前にアプリのコンテナを作り直し、作り直した直後の基準の値（`memory.peak`・`anon`）と、場面の後の値を並べる（U1 の 5.3 と同じ）。
- **対象DB の側（F1: A）**
  - perf の MySQL のメモリの上限は、U1 の F1: A（表が入るバッファープール）とあわせて Infrastructure Design で決め、記録する。
  - 利用者の対象DB でも、大きな本文の同時の書き込みが DB のメモリを使い切りうる。既知の制約として README に書く。

## 5. 監査の行の増え方（NFR2.9、R-03 は Q7: A）

- 見積もりは承認済みのとおり（ふつうの使い方で約 1万行・約 5 MB/日）。
- 主キー・ビューの決まりによる 409 は、補助権限を持つ人のものだけを残す。
- **R-03 の手当て**
  - 補助権限を持つ人がスクリプトで繰り返し失敗させる（重なり・参照先に無い値など）と、監査の行は上限なく増えうる（50 回/秒で約 430 万行/日）。既知の制約として書く。
  - Observability Setup で、内部DB の大きさの基準の値を記録する。
- 負荷の試験で書き込む行は、U1 と同じ `bizPrepare` の表と種を使い、場面の setup で自分で作る。

## 6. 横に増やさない前提

- アプリは状態を持たないが、内部DB が組み込みの H2 のため、1台で動く前提（既存の決まり）。
- 道は3つに固定する。指標の `uri` の系列は、テーブルの数が増えても増えない。

## 7. 上流との差

- **BR6.1 の表に足す**（Q6: A・F3: A）: 詳しくは reliability-design.md の 2節。
- **BR6.2 の切り替え先を先に使う**（Q2: A）: セーブポイントは同じ1本の中で行うため、接続の数は変わらない。
- **BR3.5 に日時の秒の端数を足す**（Q6: A）。
- **U1 の「貸すたびに付ける」との差**（Q3: A）: 初期化は接続を作るときだけで、借りるたびの往復が増えない。
- **NFR1.6・NFR4.7 の確かめの入力**（F4: A）。
- **「およそ 18 秒」の読み替え**（Q4: A）: 相手が止まったときに接続を持ち続ける時間は、10〜20 秒になる。
- **NFR2.7 の満たせないときの案**: 「上限を 10 に上げる」を MySQL では採らない（F1: A）。
- **NFR2.8 の場面の形**: `bizCreateLarge` とした（要件は形を NFR 設計に回していた）。

## 8. 後の段に回すこと

- **Infrastructure Design**
  - F1: A の perf の MySQL のメモリの上限（U1 の F1: A とあわせる。colima の VM の 6GiB に収まるかも確かめる）
  - F2: A の `REVOKE TEMPORARY` と README
  - 機能設計のレビューの R-06 の表ごとの GRANT
  - 書き込みのプールの大きさの環境変数を、`.env.example` と README に書く
- **コード生成の計画（B4）**
  - SpotBugs の除外の承認
  - 名前つきの管理者の注入の結合テスト
  - NFR 要件のレビューの R-06: 監査の移行の後方互換の結合テスト
  - `AuditMigrationCompatibilityIT` などの洗い出し
  - `BusinessDataConnectionUsageIT` に3行を足す
- **Observability Setup**
  - R-03 の内部DB の大きさの基準の値
  - `ms-pool-pending` の実測
- **Performance Validation**
  - NFR2.7・NFR2.8 の合否の回
  - NFR2.9 の監査の行の数と内部DB の大きさの記録（片付ける前に結果を見る）

## 9. U3 とそろえる点

- 413（4 MiB を超えた本文）の文を画面が用意する。画面で先に大きさを確かめるかは U3 で決める。
- 503 の出し分け: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」。
- E2E は業務データの代表の流れ1本まで。U2 は E2E を足さない。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/scalability-requirements.md`（NFR2.6〜NFR2.9）
- この段の答え
  - `construction/u2-businessdata-write/nfr-design/nfr-design-questions.md`（Q2・Q3・Q7: A）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1: A）
- U1 の NFR 設計: `construction/u1-businessdata-read/nfr-design/scalability-design.md`（3節・5節・8節）
- NFR 要件のレビュー: `.aidlc-reviews/nfr-requirements/units/u2-businessdata-write/00d44a7a2452e6b7/1.json`（R-03）
- コード: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（プールの既定 5）、`backend/src/main/resources/application.yaml`（内部DB のプール 30）、`docker/perf/compose.yaml`（アプリ 2g、対象DB 512m・768m・512m）、`Dockerfile`（`MaxRAMPercentage=50`）
- 決まり: `aidlc/spaces/default/memory/project.md`（大きな入力のヒープは試しで裏付ける、memory.peak の前にコンテナを作り直す、接続プールの見積もりは上限に届く形で確かめる）
- 捨ての試し: リポジトリの外の試しのコードと結果（W10・W12）

## Assumptions & Open Questions

- 前提: アプリの DTO と、要求の文脈の上乗せは、試しの値（読み込み・写し・実行）に含めていない。`bizCreateLarge` で確かめる。
- 前提: MySQL の 742 MiB は、1 秒ごとの `docker stats` の最大で、瞬間の最大はもっと大きい見込み。
- 依頼者の決定 F1: A は、2節・4節に反映した。
- この文書に固有の、諮る点は無い。
