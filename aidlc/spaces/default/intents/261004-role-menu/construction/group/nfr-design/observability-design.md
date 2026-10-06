# 観測の設計 — U3 group

## 出典

- この単位の承認済みの NFR 要件 `construction/group/nfr-requirements/observability-requirements.md`（NFR5.1〜NFR5.5）と、NFR 要件の読み直し（R-06）
- この単位の承認済みの機能設計（`rules.md` の BR8・`entities.md` の監査の項目）
- `contract-summary.md`（C10）、`components.md`（AuditLog）
- この段の答え: `nfr-design-questions.md` のまとめの確認
- 捨ての試しの結果（`reliability-design.md` の T6・T7）
- コード: `backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo`・`org.hibernate.orm.jdbc.error: OFF`）、`common/persistence/RowLockFailures.java`

## 1. 監査（NFR5.1・NFR5.5）

- `group.domain.GroupAuditEvent` を業務処理が出し、`audit.service.AuditEventListener` に足す受け取り（`onGroupAuditEvent`）が、確定の後に別のトランザクションで `AuditEvent` に写して記録する（既存の `onUserAdminAuditEvent` と同じ形）。
- 写し方: 種類は操作と結果から `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`、失敗の理由は `GROUP_NOT_FOUND`・`USER_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`NO_CHANGE` に写す。対象は `target_group_id`・`target_user_id`、`detail` は `GroupAuditDetail` を決めたキーの JSON にしたもの。
- `AuditEvent` に `targetRoleId`・`targetGroupId`・`detail` と、ロール・グループ向けのファクトリー（`withRoleGroupTarget` のような1つ）を足す。U4 も同じファクトリーを使う。
- `detail` の長さは Java の文字列の長さ（UTF-16 の単位）で 16,384 まで。ファクトリーで超える値を受けたら想定外の誤りとして例外にする（切り詰めない）。サロゲートペアを含む値で、ちょうどは通り超えは作れないことを単体テストで確かめる。
- 拒否の監査は、書き込みの前の拒否も違反の読み替えも、1つ目を巻き戻した後の2つ目の `TransactionTemplate` で出来事を出す（`reliability-design.md` の 2.3。承認の場の直しでそろえた形）。`GROUP_BUSY`・入力の誤り・読み取りは出さない。
- 確かめは `GroupAuditIT`（操作ごと・拒否ごとの行の項目）と、足した列挙の名前の長さの単体テスト（AC1.1.16 の一覧のテストに含める）。

## 2. 指標（NFR5.2）

- 独自の指標は足さない。Spring Boot の `http.server.requests` が、道の型（`/api/admin/groups`・`/api/admin/groups/{groupId}`・`/api/admin/groups/{groupId}/members`・`/api/admin/groups/{groupId}/members/{userId}`）を `uri` のタグにして出す。バケットの境界は既存の 100・250・500・1000・2000・5000 ms。
- `GroupAdminMetricsIT` で、要求の後に道の型の `uri` の系列が出て、道の値（ID）が `uri` に入らないことを確かめる。

## 3. ログ（NFR5.3）

| 場面 | 出すもの | 出さないもの |
|---|---|---|
| 成功・業務の拒否 | 何も出さない（監査が記録する） | — |
| 待ちの上限切れ（`GROUP_BUSY`） | `RowLockFailures.warn` の WARN を1回。キーは `lockKind`（`GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`）と `exceptionClass` とトレースID | 名前・ID・例外の文・スタックトレース |
| 違反の読み替え | 何も出さない（監査の FAILURE が記録する） | 例外の文 |
| 想定外の失敗 | 既存の `@RestControllerAdvice` が ERROR で1回（`group.store` が包み直した例外。元の文を持たない） | 元の例外の連なりの文 |

- Hibernate のロガー `org.hibernate.orm.jdbc.error` は、違反の文（値つき）を WARN で出すことを捨ての試しで確かめた（T6）。本番の設定（OFF）を変えない。`GroupUniqueViolationSecretLeakIT` がこれを守る。
- `GroupBusyLogIT` で、WARN が1件だけで、キーが上の3つだけであることを確かめる（既存の `UserAdminBusyLogTraceIT` と同じ形）。

## 4. 警報と SLO（NFR5.4）

- SLO はグループの管理の API の p95 1 秒。手元の監視を常に動かしていない間は判定を `Unverified` とし、Performance Validation と配備の後の値を基準の値として記録する（`project.md` の学び）。
- 新しい警報とダッシュボードは足さない。既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く。
- **Observability Setup への引き継ぎ**（NFR 要件の読み直しの R-06）: グループの API の p95 1 秒を拾う既存の警報の式を名指しし、しきい値（1000 ms のバケットの境界）との関係を確かめる。グループの API に要求を送ってから式を流す（`project.md` の学び: 系列の最初の1件は `increase` に数えられない、NaN を要求が無いためと決めつけない）。

## 5. 承認の場の決定と直し

依頼者は NFR 設計の承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」。この成果物では、読み直しの R-05 の手当て（巻き戻しの印を `GroupStoreTransactions` に集め、書き込みの前の拒否の失敗の出来事も2つ目のトランザクションで出す）に合わせて、1節の拒否の監査を出すトランザクションの書き方を直した。
