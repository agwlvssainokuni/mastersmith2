# 観測の設計 — U4 role

## 出典

- この単位の承認済みの NFR 要件 `construction/role/nfr-requirements/observability-requirements.md`（NFR5.1〜NFR5.6）と、その読み直しの記録の R-09
- この単位の承認済みの機能設計 `construction/role/functional-design/`（`functional-spec.md` の 2.16、`rules.md` の BR7.6・BR11、`entities.md` の `RoleAuditEvent`・`RoleAuditDetail`）
- `contract-summary.md`（C10）
- この段の答え: `nfr-design-questions.md` とまとめの確認
- group の NFR 設計の `observability-design.md`

## 1. 監査（NFR5.1・NFR5.5・NFR5.6）

- 出来事 `RoleAuditEvent` は、`role.domain` に置く。`audit.service.AuditEventListener` が確定の後に写して記録し、U3 が足した `target_role_id`・`target_group_id`・`detail` の列とロール向けのファクトリーを使う。
- 種類 8 つと、足す失敗の理由は機能設計の BR11 のとおり。全件の長さは単体テストで 32 文字以内を確かめる。
- `detail` は `RoleAuditDetail` の型から JSON の文字列にし、16,384（UTF-16 の単位）を超えるときは要約の型に切り替える。切り替えの判定は、全件の JSON を作ってから長さを見る（途中で切らない）。
  - 権限の保存（`PermissionChanges`）: 100 カラムの表で名前が長いと超えうる。そのときは `PermissionChangesSummary`（変わった数と先頭の 20 件）にする。
  - import の適用（`Transfer`）: ロールごとの要約。ロールが多く超えるときは `TransferSummary`（ロールの数と先頭の 50 件）にする。
- 監査を残さないもの（NFR5.6）: 入力の誤り、`ROLE_BUSY`・`GROUP_BUSY`、読み取り、import の確かめ、書き出し、保存がすでに同じロールを指す切り替え、有効な作業ロールの読み替え、確かめと適用を1つずつにする入口の `ROLE_BUSY`（`reliability-design.md` の 2.6）。
- 読み替え中に、有効な作業ロールと同じロールを明示で選んだときは保存を書き、`WORK_ROLE_SWITCHED`（`storedBeforeRoleId` に書く前の保存）を残す。これはストーリーの AC4.1.13 との差として、承認済みの要件に記録済み。`WorkRoleSwitchAuditIT` は、保存が同じときと、読み替え中のときの2つの場合に分けて書く。

## 2. 指標（NFR5.2）

- 既存の `http.server.requests`（`uri` の型・`status`・`method`、境界 100・250・500・1000・2000・5000 ms）で出る。独自の指標は足さない。
- import の確かめと適用は、試しの見通しで数秒（`reliability-design.md` の 1.2 U3・U4）で、境界の 5000 ms に収まる見込み。上限ちょうどで超えたときは `+Inf` に入る。時間の判定は k6 で行う（`performance-design.md`）。

## 3. ログ（NFR5.3）

- 業務のログは出さない（監査が記録する）。
- `ROLE_BUSY` のときだけ、`RowLockFailures.warn` で WARN を1回出す。キーは排他の種類（`lockKind`: `ROLE_ROW`・`ROLE_NAME_KEY`・`ROLE_ASSIGNMENT_KEY`・`WORK_ROLE_SELECTION_KEY`・`GROUP_ROW`・`ROLE_TRANSFER_SLOT`）。名前・ID 以外の中身と例外の文は載せない。`RoleBusyLogTraceIT` で、WARN が1件で、キーがあり、例外の文が無いことを確かめる。
- 想定外の失敗は、既存の `@RestControllerAdvice` が ERROR で1回出す。包み直した例外のクラスの名前・SQLState・制約の名前をキーで出す（`security-design.md` の 4.1）。
- トレース ID は既存の仕組みで、ログと監査の行に入る。

## 4. 警報と SLO（NFR5.4）

- 新しい警報は足さない。既存の「5xx の割合の増加」と「コネクションプールの待ち」が、role の API と作業ロールの切り替えにも `uri` によらず効く。Observability Setup で、role の `uri` の要求を指標の送信の周期をまたいでくり返し送り、2つの式の値に含まれることを確かめる（`project.md` の学び）。
- role の API の p95 1 秒を拾う警報は無い。確かめは Performance Validation の k6 だけになる。これは受け入れた制約として扱い、警報を足すかは配備先が決まったときの監視の設計（後の Intent）で決める（読み直しの R-09）。

## 読み直し1回目の直し

読み直し（NOT-READY）を受け、依頼者の決定（Q5: A と、指摘をすべて直す）で、この成果物の次を直した。

- 読み直しの指摘で、この成果物の中身に当たるものは無かった。入口の節の参照先（`reliability-design.md` の 2.6）だけを直した。WARN の `lockKind` の一覧（`ROLE_TRANSFER_SLOT` を含む）は変えていない。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。この成果物では次を直した。

- この成果物に当たる直しは無かった（鍵の待ちの上限は `reliability-design.md` の 2.1、合否の形は `scalability-design.md` の 2.4）。
