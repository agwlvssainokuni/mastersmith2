# 観測の要件 — U4 role

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。2.16 の監査の出来事、BR7.6・BR11.1〜BR11.9）
- `requirements.md`（NFR5.1・NFR5.2、FR12.1〜FR12.4）
- `contract-summary.md`（C10）
- `technology-stack.md`（コード知識ベース。`http.server.requests` の境界 100・250・500・1000・2000・5000 ms）
- この段の答え: `nfr-requirements-questions.md` の Q1〜Q4 と、まとめの確認（「決まっていること」の観測、R-14 の差）。
- 手元の監視の警報の決まり `docker/monitoring/provisioning/alerting/mastersmith.yaml`（5xx の割合の増加・コネクションプールの待ち・監査の書き込みの失敗。p95 の警報はログイン・トークンの更新・確認用 API の3つだけ）。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR5.1 監査 | 当たる | NFR5.1 |
| NFR5.2 指標 | 当たる | NFR5.2 |
| （足す） | 業務のログ、警報、detail の数え方、import と作業ロールと書き出しの監査の線引き | NFR5.3〜NFR5.6 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR5.1 | 役割・権限の監査の種類（`ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`・`ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`ROLE_TRANSFER_APPLIED`・`WORK_ROLE_SWITCHED`）と、足す失敗の理由の名前は、どれも 32 文字以内で、一度決めたら変えない。操作ごとに、操作した人・対象の列（`target_role_id`・`target_user_id`・`target_group_id`）・`detail`・結果が埋まる（BR11.1・BR11.4・BR11.9）。業務の拒否は FAILURE と理由で残す（BR11.2） | 単体テスト `AuditEventTypeNameLengthTest`（全件の長さ。group と共有の既存のテストに足すなら、そのテスト）。結合テスト `RoleAuditIT`: 操作ごとに成功と主な拒否の行の列が埋まること（AC1.1.16） | Code Generation（B4・B5・B6） |
| NFR5.2 | role の API の指標は、今の管理の API と同じく Spring Boot の `http.server.requests` で出る。タグは `uri` の型（`/api/admin/roles/{roleId}`・`/api/me/work-role` など）・`status`・`method`、境界は 100・250・500・1000・2000・5000 ms のバケット。独自の指標は足さない（NFR5.2）。import の確かめと適用（最大 15〜30 秒）は `+Inf` に入るため、時間は k6 で測る（`performance-requirements.md` の NFR2.4） | 結合テスト `RoleMetricsIT`: 管理の API 1つと作業ロールの切り替えを呼び、`http.server.requests` に、その `uri` の型と `status` のタグの系列ができること（AC1.1.17・AC4.1.20） | Code Generation（B4・B5） |
| NFR5.3 | 業務のログは出さない（監査が記録する）。`ROLE_BUSY` のときだけ、排他の種類（ロールの行・一意の鍵・主キー・グループの行）の WARN を1回出す。WARN には名前・ID 以外の中身と例外の文を載せない（group と同じ形）。想定外の失敗は、既存の `@RestControllerAdvice` が ERROR で1回出す。トレース ID は既存の仕組みでログと監査の行に入る | 結合テスト `RoleBusyLogTraceIT`（既存の `UserAdminBusyLogTraceIT` と同じ形。WARN が1件で、種類のキーがあり、例外の文が無いこと） | Code Generation（B4） |
| NFR5.4 | 新しい警報は足さない（配備先が決まるまでは手元の監視、`project.md`）。既存の「5xx の割合の増加」と「コネクションプールの待ち」は、role の API と作業ロールの切り替えにも `uri` によらず効く。p95 の警報はログイン・トークンの更新・確認用 API の3つだけで、role の API の p95 1 秒の目標を拾う警報は無い。目標は Performance Validation の k6 で確かめる | Observability Setup で、2つの警報の式に role の `uri` の要求が含まれることを、実際に要求を送って式を流して確かめる（`project.md` の学び） | Observability Setup |
| NFR5.5 | 監査の `detail` の上限 16,384 文字は、列の `VARCHAR` の数え方に合わせ、Java の文字列の長さ（UTF-16 の単位）で数える。全件の JSON が上限を超えるときは、要約の型（変わった対象の数と先頭の何件か、ロールの数と先頭の何件か）に切り替え、途中で切らない（BR11.5、group と同じ数え方） | 単体テスト `RoleAuditDetailTest`: 権限の保存の前後の値が 16,384 単位ちょうどは全件、超えると要約になり、どちらも JSON として読めること。サロゲートペアを含む名前で数えること | Code Generation（B4・B6） |
| NFR5.6 | 監査の線引きは次のとおり（BR11.3・BR11.7・BR7.6、Q6・Q7 の答え）。<br>・**import の適用**: 1回を1行の `ROLE_TRANSFER_APPLIED` にする。`detail` はファイルの SHA-256・指紋・ロールごとの要約（名前・ID・作る／置き換え・足す／変わる／消える／今の DSL に無いの数）で、前後の値の全件は残さない。確かめ（check）と書き出し（export）は残さない。<br>・**作業ロールの切り替え**: 保存がすでに選んだロールを指すときは残さない。読み替え中（保存が割り当ての外のロールを指す）に、有効な作業ロールと同じロールを選んだときは保存が書かれ、`WORK_ROLE_SWITCHED` が残る。ストーリーの AC4.1.13 の「作業ロールが営業」は、保存が営業を指す状態と読む（機能設計の再レビューの R-14、ストーリーとの差として記録）。<br>・読み替えそのもの（BR7.3）は残さない | 結合テスト `RoleTransferAuditIT`（適用の1行と `detail` の項目、確かめ・書き出しで行が増えないこと）。`WorkRoleSwitchAuditIT` は2つの場合を分ける。(1) 保存が同じロールを指すときは行が増えない。(2) 読み替え中に同じロールを選ぶと行が1件増え、`storedBeforeRoleId` が書く前の保存を指す | Code Generation（B5・B6） |
