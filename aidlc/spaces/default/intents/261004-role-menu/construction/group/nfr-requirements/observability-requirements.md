# 観測の要件 — U3 group

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。BR8.1〜BR8.9 の監査、2.8 の BUSY の WARN）
- `requirements.md`（NFR5.1・NFR5.2、FR12.1〜FR12.3）
- `contract-summary.md`（C10 の監査の出来事）
- `technology-stack.md`（コード知識ベース。Micrometer・OpenTelemetry・構造化ログ）
- この段の答え: `nfr-requirements-questions.md` のまとめの確認（指標は既存の `http.server.requests`、業務のログは GROUP_BUSY の WARN だけ、detail の数え方）。コード: `backend/src/main/resources/application.yaml` の `management.metrics.distribution.slo`。

## ID の対応表

同じ番号は上流と同じ意味でだけ使う。全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR5.1 監査（FR12）と種類の名前の長さ | 当たる（グループの操作の監査） | NFR5.1 |
| NFR5.2 新しい管理の API の指標 | 当たる | NFR5.2 |
| （足す） | ログ・警報と SLO・detail の数え方 | NFR5.3〜NFR5.5 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR5.1 | 作成・名前の変更・削除・メンバーの追加・外しの成功と、業務の理由の拒否（`GROUP_NOT_FOUND`・`USER_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`GROUP_NO_CHANGE`）を、監査に残す。各行は操作した人・対象のグループ（メンバーの操作は対象の利用者も）・`detail`・結果と理由を持つ。入力の誤り・`GROUP_BUSY`・読み取りは残さない。足す種類（`GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`）と理由（`GROUP_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`）の名前は 32 文字以内 | 結合テスト `GroupAuditIT`（操作ごと・拒否ごとに行を読み、項目が埋まる。400・BUSY・読み取りで行が増えない）。単体テストで足した列挙の名前の長さ（AC1.1.16 の一覧のテストに含める） | Code Generation（B3） |
| NFR5.2 | グループの管理の API の指標は、今の管理の API と同じく Spring Boot の `http.server.requests` で出す（`uri` は道の型 `/api/admin/groups`・`/api/admin/groups/{groupId}`・`/api/admin/groups/{groupId}/members`・`/api/admin/groups/{groupId}/members/{userId}`、`method`・`status`・`outcome` のタグ、境界 100・250・500・1000・2000・5000 ms のバケット）。独自の指標は足さない（利用者の管理の API と同じ） | 結合テスト `GroupAdminMetricsIT`（API を呼んだ後に、道の型の `uri` の系列が出て、道の値（ID）が `uri` に入らないこと） | Code Generation（B3） |
| NFR5.3 | 業務のログは出さない（監査が記録する）。`GROUP_BUSY` のときだけ、排他の種類（グループの行・一意の鍵・主キー）を持つ WARN を1回、グループの名前・利用者の ID・例外の文を載せずに出す。想定外の失敗は既存の `@RestControllerAdvice` が ERROR で1回出す。トレースID は既存の仕組みでログと監査の行に入る | 結合テスト `GroupBusyLogIT`（既存の `UserAdminBusyLogTraceIT` と同じ形。WARN が1件で、キーは排他の種類とトレースIDだけ） | Code Generation（B3） |
| NFR5.4 | 警報は既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く。新しい警報とダッシュボードは足さない。SLO はグループの管理の API の p95 1 秒（NFR2.5）とし、手元の監視を常に動かしていない間は判定を `Unverified` とし、Performance Validation と配備の後の値を基準の値として記録する（`project.md` の学び） | 既存の警報の式を、グループの API に要求を送ったうえで流して確かめる | Observability Setup |
| NFR5.5 | 監査の `detail` の上限 16,384 文字は、列の `VARCHAR` の数え方に合わせ、Java の文字列の長さ（UTF-16 の単位）で数える。U4 が要約に切り替える基準も同じ数え方にする（機能設計の再レビューの R-03 の手当て）。上限を超える `detail` は作らず、途中で切らない | 単体テスト（サロゲートペアを含む値で、16,384 単位ちょうどは通り、超える値は作れない） | Code Generation（B3） |
