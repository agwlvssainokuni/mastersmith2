# セキュリティの設計 — U4 role

## 出典

- この単位の承認済みの NFR 要件 `construction/role/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.11、受け入れた制約）と、その読み直しの記録の R-08
- この単位の承認済みの機能設計 `construction/role/functional-design/`（`functional-spec.md`・`rules.md` の BR2・BR9・BR11・BR12、`entities.md`）
- `contract-summary.md`（C1・C5・C7・C8・C10）、`components.md`（RoleManagement）
- この段の答え: `nfr-design-questions.md` の Q2・Q4（どちらも A）とまとめの確認（「決まっていること」の設計の形）
- 捨ての試しの結果: `reliability-design.md` の 1節（U1・U2）
- group の NFR 設計の `security-design.md`（例外の文の経路）と、その読み直しの記録の R-06・R-09
- コード: `backend/src/main/resources/application.yaml`（`org.hibernate.orm.jdbc.error: OFF`・`open-in-view: false`）

## 1. 守りの層（深さで守る）

| 層 | 守り | 要件 |
|---|---|---|
| 入口 | 既存のアクセストークンの認証（停止中は拒否）と、`/api/admin/**` の管理者の印の判定。`ApiAccess(ADMIN)`・`ApiAccess(AUTHENTICATED)` を付け、U1 の網羅の検査を通す。`SecurityRuleContributor` は足さない | NFR1.1〜NFR1.3 |
| 本文の大きさ | `RequestBodyLimitRoute`（確かめと適用の道、10 MiB、`PAYLOAD_TOO_LARGE`）を `role.web` の設定で足す。認証と認可の後に動く既存のフィルターで、読む前に断る | NFR1.5 |
| 入力の型 | DTO は決めた項目だけを持つ `record`。主体は要求の文脈から読み、他人を指す値を受けない | NFR1.4 |
| YAML | `SafeYamlReader`（深さ 10・コレクションの別名 0・展開後の節 1,000,000・10 MiB）と、`role.transfer` の中身の検証 | NFR1.5 |
| 書き込み | `role/store` の排他と区分。例外の文を外へ出さない | NFR1.8 |
| 出力 | 伏せ字の型、応答の DTO にハッシュ値・内部の値の項目を持たない、`detail` は決めた型だけ | NFR1.6・NFR1.7・NFR1.9 |

## 2. 認可とテスト（NFR1.1〜NFR1.3）

- `RoleAdminAuthorizationApiIT`・`RoleMeAuthorizationApiIT` は、API と期待の状態コードの組をパラメーターの表で書く。
  - 管理の API: 未認証 401、管理者の印だけを欠く利用者 403、管理者は成功、停止中の管理者は通らない。
  - 自分の API: 未認証 401、停止中は通らない、ログイン済みは成功。
- **B4 と B5 の差**（NFR1.2、読み直しの R-08）: 差が残る期間は次のとおり。
  - B4: 403 は管理者の印を持たない利用者で確かめる。
  - B5: 全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールにする、管理者の印だけを欠く利用者を、role の表と group の7つの口の表に足す。
  - 差が残るのは B4 の統合から B5 の統合までの間。B5 の終わりの条件に「403 の表がその利用者で書かれていること」を入れる。
- AC2.2.14 のため、既存の管理の API（利用者・招待・DSL・グループ）も、全 FULL のロールを持つ管理者の印の無い利用者で 403 になることを表に入れる（B5）。

## 3. 一括代入と IDOR（NFR1.4・NFR1.10）

- 作成・名前の変更は `RoleNameRequest(name)`、割り当ては `AssignmentRequest(userId, groupId)`（ちょうど一方）、切り替えは `WorkRoleRequest(roleId)` だけを受ける。足した項目は Jackson の既定で無視し、反映しない。
- 0 以下の ID と存在しない ID は、存在しない ID として 404 にする（変える操作は監査に FAILURE）。排他の前に有無を読むため、行の排他やダミーの行に触れない。
- `RoleIdorApiIT` と `RoleMassAssignmentApiIT` は、拒否の後に対象の行を読み直して、要求の前と同じであることを確かめる。

## 4. 漏えいの守り（NFR1.6〜NFR1.9）

### 4.1 例外の文の経路

- 違反・上限切れを起こしうる書き込み・排他・`@Modifying` の問い合わせ・import の JDBC のバッチは、すべて `TraceAspect` の対象の層の外の `role/store` に置く（`logical-components.md` の L2）。repository の層には読み取りだけを置く。
- `role/store` は例外を中で受けて区分し、外へは結果の型だけを返す。想定外の例外は、値を含まない SQLState と制約の名前だけを持つ例外に包み直す。元の連なり（行の値が入りうる。group の試し T6）は持ち出さない。`GlobalExceptionHandler` の ERROR は、包み直した例外のクラスの名前・SQLState・制約の名前をキーで出す（group の読み直しの R-06 の手当て）。
- 構造の検査（`RoleBoundaryArchitectureTest`）で次の2つを確かめる。
  - `role.repository` に `@Modifying` と書き込みの方法が無い。
  - `role/store` は `role.service` からだけ呼ばれる（web の層は直接呼ばない）。
- `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` を消さない。コード生成で、その行のコメントに role の理由（ロールの名前と ID・対象の名前が違反の文に入る）を足す（group の読み直しの R-09）。消されたときは `RoleUniqueViolationSecretLeakIT` が落ちる。

### 4.2 個人に関する値（NFR1.6）

- 割り当ての一覧と利用者のロールの読み取りの氏名・メールアドレスは、`user.service` の伏せ字の型（`UserSummary`・`RedactedText`）で受け、web の層で応答の DTO を組み立てるときにだけ取り出す。
- `RoleSecretLeakIT`（TRACE を有効）で、試験の利用者のメールアドレス・氏名がアプリのログに 0 件であることを確かめる。

### 4.3 YAML の中身（NFR1.9）

- 本文のバイト列は `RoleTransferPayload`、読んだ木は `RoleTransferTree`、変わる点の一覧は `TransferPlan`、誤りの一覧は `TransferErrors` の型で `role.service` に受け渡す。どの型も `toString` で中身を伏せ、大きさと件数だけを出す。
- `role.transfer` は `TraceAspect` の対象の外（`TraceAspect` の対象は web・service・domain・repository だけ）。ただし、service との受け渡しは上の伏せる型で行う。
- import の書き込みの `JdbcTemplate` は、TRACE で引数の値を出す（`StatementCreatorUtils`）。`application.yaml` に `org.springframework.jdbc.core.StatementCreatorUtils: OFF` を足す。
- `RoleSecretLeakIT` に、目印の名前を入れた YAML で確かめと適用を流し、ログに目印が 0 件であることを足す。TRACE を `cherry.mastersmith` と `org.springframework.jdbc` に広げて確かめる（JDBC のバッチの経路を含める）。

### 4.4 監査と応答（NFR1.7）

- `detail` は `RoleAuditDetail` の決めた型だけから作る。利用者の値の項目を持たない。
- 既存の `AuditSecretLeakIT` の列の一覧は U3 が更新する。role は列を足さない。

## 5. YAML の入力の決まり（NFR1.5、試し U1・U2）

- キーは、YAML の型によらず書いたとおりの文字列として読める（U1）。型の付くキーを誤りにする手当ては要らない。`1` と `'1'` のように同じ文字列になるキーは、重複の誤りになる。
- 値は、主権限は `NONE`・`READ`・`FULL` の文字列だけ、`create`・`delete` は真偽の値だけを受ける。文字列の `'true'`・null・数は `INVALID_VALUE`。`yes`・`on`・`no` は真偽として読まれるため受け付ける（読み込みの部品の規則のまま）。
- 書き出しは、次の名前を二重引用符で囲む: YAML で型や特別な意味を持ちうる名前（`true`・`false`・`yes`・`no`・`on`・`off`・`null`・`~`、数・日付に読める名前、`:`・`#`・`&`・`*`・`!`・`|`・`>`・`'`・`"`・`%`・`@`・`` ` `` を含む名前、前後に空白のある名前、空の名前）。往復の性質ベースのテスト（`RoleTransferRoundTripProperties`）に、それらの名前を入れる。
- 別名は、コレクションを指すものだけを数え、上限は 0（U2）。境界のテストは、コレクションの別名の 0 と 1 で書く。スカラーを指す別名は受け付ける。

## 6. 静的解析（NFR1.11）

- 今の関門のまま。`role/store` の JDBC のバッチと範囲の読み取りも、名前の付いた引数だけで組み、文字列の連結で SQL を作らない（`SQL_` の関門に当たらないことを `./gradlew verify` で確かめる）。

## 7. 受け入れた制約

承認済みの `security-requirements.md` の「受け入れた制約」の3つ（入口の判定の後の窓、適用の間の `ROLE_BUSY`、書き出しが上限を超えうる）は変わらない。この段で次の1つを足す。

| 制約 | 内容と理由 | 次の確かめ |
|---|---|---|
| 確かめと適用は同時に1つ | 2つ目の確かめ・適用は待たずに `ROLE_BUSY` で断る（`reliability-design.md` の 2.6、Q5: A）。ヒープ約 1 GiB に対し、大きな確かめ・適用が重なるとアプリの土台と合わせて足りなくなるおそれがあり、それを小さくする（サービスの妨害の守り） | `RoleTransferBusyIT`（1つ目を待ち合わせで止めたまま2つ目が `ROLE_BUSY`、1つ目が例外で終わった後に次が通る） |
| 入口に入らない重い処理 | 書き出し（最大約 40 MB の文字列を作りうる）と、DSL の管理の 10 MiB の投入と適用は、上の入口に入らない。これらと確かめ・適用が同時に動くと、ヒープを大きく使いうる | 管理者の操作の頻度が低いことを前提に受け入れる。Performance Validation の `roleTransferLarge` で、確かめ・適用の時のヒープの最大を記録する |

## 読み直し1回目の直し

読み直し（NOT-READY）を受け、依頼者の決定（Q5: A と、指摘をすべて直す）で、この成果物の次を直した。

- **R-03**: 7節の `RoleTransferSlot` の根拠をヒープ約 1 GiB に合わせ、入口に入らない書き出しと DSL の適用を受け入れた制約に足した。
- **R-06**: 4.3 に、`JdbcTemplate` の TRACE のロガーを OFF にすることと、漏えいの確かめに JDBC の経路を含めることを足した。
- **R-09**: 4.1 の構造の検査を「`role/store` は `role.service` からだけ呼ばれる」に直し、4.3 に `role.transfer` が `TraceAspect` の対象の外であることを書いた。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。この成果物では次を直した。

- この成果物に当たる直しは無かった（鍵の待ちの上限は `reliability-design.md` の 2.1、合否の形は `scalability-design.md` の 2.4）。
