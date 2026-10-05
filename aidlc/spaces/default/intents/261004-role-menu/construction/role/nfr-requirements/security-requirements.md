# セキュリティの要件 — U4 role

## 出典

- `functional-spec.md`（この単位の承認済みの機能設計。流れ・7節のテストの観点・8節の契約との差・11節の承認の場の直し）
- `rules.md`（同じく BR1.1〜BR13.2 の 91 件）
- `requirements.md`（NFR1〜NFR6、FR1〜FR8・FR12、前提 A6）
- `contract-summary.md`（C1・C3・C4・C5・C7・C8・C10）
- `technology-stack.md`（コード知識ベース）
- この段の答え: `nfr-requirements-questions.md` の Q1〜Q4 はすべて A。まとめの確認は Looks correct で、「この段で決める要点」と、0 以下の ID を 404 にして機能設計の BR2.4 との差として記録することを含む。
- 決まりの層: `team.md`（役割・権限の必須のテスト、認証・認可・監査、DSL の信頼できない入力、Code Style）、`project.md`（Forbidden・Mandated・学び）。
- 先に確定した単位: `construction/group/nfr-requirements/`（U4 role への引き継ぎと、レビューの R-01〜R-07）。

## ID の振り方（7つの成果物に共通）

- 上流の枝番（`requirements.md` の NFR1.1〜NFR6.4）と **同じ番号は同じ意味** でだけ使う。上流の要件をこの単位に当てはめたものは上流と同じ ID にする。
- この単位で新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR3.3〜・NFR5.3〜・NFR6.5〜）。
- 1つの要件は1つの成果物にだけ書く。下の表は7つの成果物の全体を示す。
- テストも持ち主の段も無いものは要件の行にせず、「受け入れた制約」の節に書く。

| 上流の枝番 | この単位での扱い | この単位の ID と置き場 |
|---|---|---|
| NFR1.1 API ごとのサーバー側の判定 | 当たる | NFR1.1（security） |
| NFR1.2 401・403・200 のテスト | 当たる | NFR1.2（security） |
| NFR1.3 API の分類の網羅 | 部分（role の API の分類の印。網羅の検査の仕組みは U1 cross-cutting） | NFR1.3（security） |
| NFR1.4 昇格・一括代入・IDOR | 当たる | NFR1.4（security） |
| NFR1.5 権限の YAML の守り | 当たる | NFR1.5（security） |
| NFR1.6 個人に関する値の TRACE | 当たる | NFR1.6（security） |
| NFR2.1 規模の前提 | 当たる | NFR2.1（scalability） |
| NFR2.2 木・保存・作業ロールの切り替えの時間 | 部分（メニューの時間は U5 navigation） | NFR2.2（performance） |
| NFR2.3 要求ごとの権限の読み出しの重さ | 当たる | NFR2.3（performance） |
| NFR2.4 import の時間と YAML の大きさの上限 | 当たる | NFR2.4（performance）・NFR2.9（scalability） |
| NFR3.1 同時の重なり | 当たる | NFR3.1（reliability） |
| NFR3.2 import の一括確定 | 当たる | NFR3.2（reliability） |
| NFR4.1〜NFR4.3 画面 | 当たらない（U6 role-admin-ui・U7 app-frame-ui） | — |
| NFR5.1 監査 | 当たる | NFR5.1（observability） |
| NFR5.2 指標 | 当たる | NFR5.2（observability） |
| NFR6.1 必須のテスト | 部分（役割・権限の部分。N 階層のメニューの画面は U5・U7） | NFR6.1（tech-stack） |
| NFR6.2 性質ベースのテスト | 部分（継承の解決と作業ロールの決め方。メニューを絞る関数は U5・U7） | NFR6.2（tech-stack） |
| NFR6.3 E2E | 当たらない（流れは U6・U7） | — |
| NFR6.4 カバレッジの下限 | 当たる（`access.service` には手を入れない） | NFR6.4（tech-stack） |
| （足す） | 応答・監査の秘密、例外の文、YAML の中身の TRACE、0 以下の ID、静的解析 | NFR1.7〜NFR1.11（security） |
| （足す） | role の他の API の時間、問い合わせの数、k6 の判定の決まり | NFR2.5・NFR2.6・NFR2.10（performance） |
| （足す） | 接続プール、1要求の接続の本数、件数の上限を置かないこと | NFR2.7・NFR2.8・NFR2.11（scalability） |
| （足す） | ROLE_BUSY、違反の後の監査、監査の失敗、移行、捨ての試し、import の判定の順 | NFR3.3〜NFR3.8（reliability） |
| （足す） | 業務のログ、警報、detail の数え方、import と作業ロールの監査の線引き | NFR5.3〜NFR5.6（observability） |
| （足す） | 境界テスト、依存を足さないこと | NFR6.5・NFR6.6（tech-stack） |

## 脅威の見方（STRIDE）

| 脅威 | この単位での形 | 守り（機能設計） | 要件 |
|---|---|---|---|
| なりすまし | 他人の作業ロールを変える・他人の権限を読む | 主体は要求の文脈から読み、利用者を指す値を受け取らない（BR2.2・BR7.5） | NFR1.4 |
| 改ざん | 本文に ID・組み込みの印などを足す、YAML に知らない項目を入れる | 決めた項目だけを受ける（BR2.3）、YAML の知らない項目は誤り（BR9.5） | NFR1.4・NFR1.5 |
| 否認 | 誰が権限を変えたか分からない | 操作ごとの監査、業務の拒否も FAILURE で残す（BR11） | NFR5.1・NFR5.6 |
| 情報の漏えい | 割り当ての一覧の氏名・メールアドレス、YAML の中身、DB の例外の文 | 伏せ字の型、例外の文を外へ出さない（BR12） | NFR1.6〜NFR1.9 |
| サービスの妨害 | 大きい・深い・別名の爆発の YAML、長い import | 上限（BR9.3・BR9.4）、排他の待ちの上限（BR8.3） | NFR1.5・NFR2.4・NFR3.3 |
| 権限の昇格 | 管理者の印の無い利用者がロールで管理の権限を得る | 管理の可否は管理者の印だけ（BR2.1・BR6.10） | NFR1.1・NFR1.2 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.1 | role の API の可否は、API ごとにサーバー側で判定する。管理の API は管理者の印と停止で判定し、自分の API はログインと停止で判定する。ロール・作業ロール・権限の設定・画面の出し分けで管理の可否は変わらない（BR2.1・BR2.2・BR6.10） | 結合テスト `RoleAdminAuthorizationApiIT`・`RoleMeAuthorizationApiIT`（NFR1.2 の表）。全スキーマを FULL にしたロールを作業ロールにする、管理者の印の無い利用者で、既存の管理の API（利用者・招待・DSL・グループ）も 403 になること（AC2.2.14） | Code Generation（B4・B5） |
| NFR1.2 | 足す API のすべてについて、次をパラメーターを使う表のテストで確かめる。管理の API: 未認証 401、管理者の印だけを欠く利用者 403、管理者は成功（200・201・204）、停止中の管理者は通らない。自分の API: 未認証 401、停止中の利用者は通らない、ログイン済みは成功。**B4 の時点の差**: B4 では作業ロールを持てないため、403 は管理者の印を持たない利用者で確かめる。`team.md` の「要る権限だけを欠く利用者（ほかの権限は持つ）」は、B5 で全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールにする利用者を表に足して満たす。同じ利用者を group の7つの口の 403 の表にも足す（group の引き継ぎ） | 上の2つの結合テスト。B4 は ロール・設定・木・1件の読み取り。B5 は割り当て・作業ロール・自分の権限と、403 の利用者の差し替え。B6 は書き出し・確かめ・適用 | Code Generation（B4・B5・B6。403 の差は B5 で解消） |
| NFR1.3 | role が足す管理の API には `ApiAccess(ADMIN)`、自分の API には `ApiAccess(AUTHENTICATED)` を付け、U1 の網羅の検査（印の無い API・印と道の食い違いで落ちる）を通す。`SecurityRuleContributor` は足さない | U1 の構造の検査が B4〜B6 の `./gradlew verify` で通ること（AC1.1.15 の role の分） | Code Generation（B4〜B6）。検査の仕組みは U1 |
| NFR1.4 | 昇格・一括代入・IDOR を防ぐ。<br>・本文は決めた項目だけを受ける（BR2.3）。<br>・存在しないロール・利用者・グループの ID は理由ごとの 404 で拒否し、状態を変えない。<br>・外しで存在しない相手は `ROLE_NO_CHANGE`（BR6.4）。<br>・作業ロールは自分のロールの中からだけ選べ、自分に無いロールと存在しないロールは同じ `ROLE_NOT_ASSIGNED` にする（BR7.5）。<br>・切り替えと自分の権限の API は、他人を指す値を使わない（BR2.2）。<br>・管理の API の外（`PUT /api/me/preferences` など）の本文にロール・作業ロールの項目を足しても変わらない | 結合テスト `RoleMassAssignmentApiIT`・`RoleIdorApiIT`・`WorkRoleSwitchApiIT`。拒否の後に対象の行を読み直して、要求の前と同じであることを確かめる（状態コードだけで合格にしない）。ID は存在しない値・0・負の値を入れる | Code Generation（B4・B5） |
| NFR1.5 | 権限の YAML は信頼できない入力として、`SafeYamlReader` だけで読む（BR9.3）。<br>・上限: 大きさ 10 MiB（10,485,760 バイト）・深さ 10・コレクションを指す別名 0・展開後の節 1,000,000。<br>・タグと任意の型の生成を拒否し、重複キーを誤りにし、外部の参照を取りに行かない。<br>・道ごとの本文の上限（`RequestBodyLimitRoute`）で、上限を超える本文を読まずに 413 にする（BR9.4）。<br>・別名はコレクションを指すものだけを数える。スカラーを指す別名は節を増やさないため受け付ける（機能設計の再レビューの R-05 の手当て）。<br>・拒否は Problem Details の `ROLE_TRANSFER_INVALID`（400）か `PAYLOAD_TOO_LARGE`（413）で返し、部品の例外の文を含めない | 結合テスト `RoleTransferLimitsApiIT`。<br>・境界: 10,485,760 バイトちょうどは受け付け、1 バイト超えは 413。深さ 10 は受け付け、11 は拒否。コレクションを指す別名 0 は受け付け、1 は拒否。展開後の節 1,000,000 は受け付け、1,000,001 は拒否。<br>・別名の展開の爆発が時間の上限内に拒否される。<br>・`!!` のタグ、重複キー（ロールと対象の両方）、知らない項目、版の誤り。<br>・応答の本文に部品の例外の文が無いこと（AC3.1.7・AC3.1.12・AC3.1.13） | Code Generation（B6） |
| NFR1.6 | 割り当ての一覧と、利用者のロールの読み取りに出る氏名・メールアドレスは、web・service・domain・repository のすべての経路で伏せ字の型のまま渡す。文字列にするのは応答の DTO を組み立てるときだけ（BR12.1） | 結合テスト `RoleSecretLeakIT`（TRACE を有効にし、割り当ての一覧と利用者のロールを読んで、試験の利用者のメールアドレス・氏名がアプリのログに 0 件。既存の `*SecretLeakIT` と同じ形） | Code Generation（B5） |
| NFR1.7 | 応答と監査の行に、パスワード（平文・ハッシュ値）・トークン・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない。監査の `detail` にメールアドレス・氏名を入れない（BR11.8・BR12.2） | `RoleSecretLeakIT` で、応答の JSON と監査の行の全部の列にそれらの値が 0 件。`detail` の型（`RoleAuditDetail`）に利用者の値の項目が無いことを単体テストで確かめる | Code Generation（B4・B5・B6） |
| NFR1.8 | 一意の制約・主キー・外部キーの違反と、排他の待ちの上限切れの例外の文（行の値が入りうる）、YAML の部品の例外の文は、`TraceAspect` の対象の層の外へ出さない。受けた所でクラスの名前だけをログに出す（BR12.3） | 結合テスト `RoleUniqueViolationSecretLeakIT`（同じ名前の同時の作成と、同じ組の同時の割り当てで、例外の文に入る名前・ID の値がログと応答に 0 件。既存の `UserUniqueViolationSecretLeakIT` と同じ形） | Code Generation（B4・B5） |
| NFR1.9 | 権限の YAML の本文のバイト列・読んだ木・変わる点の一覧・`errors` は、`toString` で中身を伏せる型で `role.service` に受け渡す。TRACE を有効にしても、YAML の中のロールの名前・対象の名前・値がログに出ない（機能設計の再レビューの R-11 の手当て） | `RoleSecretLeakIT` に、目印の名前（例 `LEAKCHECK_ROLE_7f3a`）を入れた YAML で確かめと適用を流し、ログに目印が 0 件であることを足す | Code Generation（B6） |
| NFR1.10 | 道と本文の `roleId`・`userId`・`groupId` が 0 以下でも 400 にせず、存在しない ID として 404 にする（変える操作は監査に FAILURE）。排他の前に有無を読むため、行の排他やダミーの行には触れない。group と同じ扱いで、機能設計の BR2.4（0 以下は 400）との差として記録する。整数でない値は今までどおり 400 | `RoleIdorApiIT` に 0 と -1 を入れる | Code Generation（B4・B5） |
| NFR1.11 | 静的解析の関門は今のまま（SpotBugs ＋ FindSecBugs の priority 1 と、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず止める）。名前の範囲の読み取り（スキーマ・テーブルの階層）も、名前の付いた引数だけで組み、文字列の連結で SQL を作らない | `./gradlew verify` の静的解析が通ること。除外の設定を足さない | Code Generation（B4〜B6） |

## 依存とコンプライアンス

- 新しい依存は足さない（`tech-stack-decisions.md` の NFR6.6）。ライセンスの確かめと、OSV-Scanner の対象の変化は起きない。
- 個人データの新しい保存は無い。割り当てと作業ロールの保存は利用者の ID だけを持つ。ロール・グループ・対象の名前は業務の名前で、個人に関する値として扱わない（group と同じ扱い）。
- 監査は、操作した人・対象・変えた中身・結果を残す（`project.md` の Mandated。拒否を含めるかは要件で決める、の答えは BR11.2・BR11.3）。

## 受け入れた制約

| 制約 | 内容と理由 | 次の確かめ |
|---|---|---|
| 入口の判定の後の窓 | 要求の入口で管理者の印を確かめた後、書き込みまでの間に別の管理者に印を外されても、その1件の要求は通る。ロールの操作は管理者の印・停止を変えず、次の要求からは入口で拒否されるため受け入れる（group の受け入れた制約と同じ） | 外した後の次の要求が 403 になることは NFR1.2 の表で確かめる |
| 適用の間の ROLE_BUSY | import の適用は、置き換えるロールの行を排他したまま1つのトランザクションで書く（最大 30 秒、NFR2.4）。その間、同じロールへのほかの管理の操作は待ちの上限 3 秒で `ROLE_BUSY` になる（Q4: A） | `reliability-requirements.md` の NFR3.3 の結合テストで、適用の間の操作が `ROLE_BUSY` になり状態が変わらないことを確かめる |
| 書き出しが上限を超えうる | 書き出しは上限を超えても止めない。上限を超えたファイルはそのままでは読み込めず（413）、ロールの一部ずつに分けて読み込む（RF3）。応答のヘッダーで知らせる（`scalability-requirements.md` の NFR2.9） | NFR2.9 の結合テスト |
