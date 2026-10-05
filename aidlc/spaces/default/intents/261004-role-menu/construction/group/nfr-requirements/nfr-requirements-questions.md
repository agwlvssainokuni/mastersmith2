# NFR 要件の質問 — U3 group

対象の単位: U3 group（kind: service）。作る成果物は `performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の7つです（段の定義の produces_kinds）。

読んだもの:

- この単位の承認済みの機能設計 `construction/group/functional-design/`（`functional-spec.md`・`rules.md` の BR1.1〜BR10.2・`entities.md`）と、そのレビュー（再レビューの R-03・R-05〜R-11）
- 要件 `inception/requirements-analysis/requirements.md`（NFR1〜NFR6、前提 A6）、契約 `inception/contract-design/contract-summary.md`（C4・C6・C10）、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 先に確定した単位の NFR 要件（`construction/cross-cutting/nfr-requirements/`・`construction/dsl-v2/nfr-requirements/`）と、dsl-v2 の NFR 要件のレビュー（上流の枝番の再利用・N/A の抜けの指摘）
- 前の Intent `260930-user-admin` の U3（利用者の管理の API）の NFR 要件と、負荷の試験の台本 `perf/k6/scenarios.js`（`userAdmin*` の場面）・`perf/README.md`
- コード: `backend/src/main/resources/application.yaml`（指標の `http.server.requests` のバケット）、`useradmin/service/UserAdminService.java`（TransactionTemplate の使い方・負の ID の扱い）、`user/repository/UserRowLockRepository.java`（排他の待ちの上限 3 秒）

質問は 2 問です。ほかの点は上流・`team.md`・`project.md`・コードで決まっているため、下の「決まっていること」と「この段で決める要点」に書き、まとめの確認で確かめます。

---

## 決まっていること

### ID の振り方（dsl-v2 のレビューの R-01・R-02 を避ける）

- この単位の要件の ID は、上流の NFR の枝番（NFR1.1〜NFR1.6・NFR2.1〜NFR2.4・NFR3.1〜NFR3.2・NFR4.1〜NFR4.3・NFR5.1〜NFR5.2・NFR6.1〜NFR6.4）と **同じ番号は同じ意味** でだけ使う。上流の要件をこの単位に当てはめた要件は上流と同じ ID（例: NFR1.2 はグループの API の 401・403・200）、この単位で新しく足す要件は上流の最後の枝番の次から振る（例: NFR1.7〜、NFR2.5〜、NFR3.3〜、NFR5.3〜、NFR6.5〜）。各成果物の冒頭に、上流の ID とこの単位の ID の対応表を1つ置く。
- `traceability.json` は上流の枝番の単位（NFR1.1〜NFR6.4）で行を立てる。当たらないもの（NFR1.5 の権限の YAML、NFR2.2〜NFR2.4 の権限の木・メニュー・作業ロール・import の時間、NFR3.2 の import の一括確定、NFR4.1〜NFR4.3 の画面）は理由と持ち主の単位を書いて N/A にする。当たるものと当たらないものが混ざる要件（NFR1.4 の作業ロールの部分、NFR3.1 の割り当ての部分、NFR6.1〜NFR6.3 の役割・権限とメニューの部分）は「部分」と書き、残りの持ち主の単位を書く。

### セキュリティ（devsecops・コンプライアンスの視点）

- 守る相手と守り方は承認済みの機能設計のとおり（STRIDE で見て、権限の昇格は管理者の印だけで判定する BR2.1、改ざん・一括代入は BR2.2、IDOR は BR2.3、情報の漏えいは BR8.7・BR9.1〜BR9.3、否認の防止は監査 BR8.1〜BR8.4）。すべての口は `ApiAccess(ADMIN)` で、`SecurityRuleContributor` は足さない。
- 停止中の管理者は既存のアクセストークンの認証で拒否される（入口の判定）。グループの操作は管理者の印を変えないため、書き込みのトランザクションの中では確かめ直さない（BR5.6）。
- 個人に関する値（メンバーの氏名・メールアドレス）は詳細の応答のためだけに読み、伏せ字の型のまま渡す。`GroupSecretLeakIT`（TRACE を有効）・既存の `AuditSecretLeakIT`（列の一覧に3列を足す）・一意の違反の例外の文の漏えいのテスト（既存の `*UniqueViolationSecretLeakIT` と同じ形）で確かめる。
- 個人データの新しい保存は無い（メンバーの行は利用者の ID だけを持つ）。監査の `detail` にもメールアドレス・氏名を入れない。グループの名前は管理者が入れる業務の名前で、個人に関する値として扱わない（機能設計の Open questions に挙げた扱いで、機能設計はこの扱いのまま承認された。改めて受け入れてよいかは、まとめの確認で確かめる）。
- 静的解析の関門は今のまま（SpotBugs ＋ FindSecBugs の priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）。問い合わせは名前の付いた引数だけで組み、文字列の連結で SQL を作らない。

### 依存とライセンス

- 新しい依存は足さない。Spring Boot 4.1.1・Spring Data JPA・H2（Spring Boot の管理の版。`-Dh2.compactThreads=1` のまま）・Flyway・Jackson（監査の `detail` の JSON。Spring Boot の管理の系列 3.1、上書き 3.1.7 のまま）・ArchUnit 1.5.1・jqwik 1.10.1。ライセンスの確かめと OSV-Scanner の対象の変化は起きない。

### 信頼性・同時性

- 同時の重なりの守りは承認済みの機能設計のとおり（グループの行の排他、一意の鍵・主キーの待ちの上限切れも `GROUP_BUSY`、外部キーは最後の守り、違反の後は巻き戻して書き込みの無い新しいトランザクションで失敗の出来事だけを出す。BR5.1〜BR5.7）。待ちの上限は既存の行の排他と同じ 3 秒。
- 重なりの確かめは待ち合わせの口（`GroupBarrier`、本番は何もしない部品、テストは `@Primary`）で行い、スレッドの数に頼らない（`team.md`）。合否は両方の要求が終わった後の内部DB の状態と、負けた側が業務の code の 4xx（500 でない）であること。
- 同じ名前の同時の作成・同じ名前への同時の変更・同じメンバーの同時の追加で、重なった側が違反になるか待ちの上限切れになるかは、NFR 設計でリポジトリの外の捨ての試しのコードで先に確かめ、結合テストの期待をその結果に合わせる（`project.md` の学び、機能設計の R-02）。
- 監査の書き込みの失敗は既存の決まりどおり（操作は成功のまま。既存の `AuditWriteFailureIT` の形）。
- 移行は表と列を足すだけで、1つ前の版のアプリが動く（`ddl-auto: validate` は余分な表と列を許し、Flyway は前の版が知らない新しい移行を既定で無視する）。戻しの確かめは配備の段。

### 観測

- 指標は、今の管理の API と同じく Spring Boot の `http.server.requests`（`uri` は道の型 `/api/admin/groups/{groupId}` など、`status`・`method` のタグ、境界 100〜5000 ms のバケット）で出る（NFR5.2）。利用者の管理の API も独自の指標を持たないため、グループにも独自の指標は足さない。
- 業務のログは出さない（監査が記録）。`GROUP_BUSY` のときだけ排他の種類（グループの行・一意の鍵・主キー）の WARN を1回、値（名前・ID 以外の中身）を載せずに出す。想定外の失敗は既存の `@RestControllerAdvice` が ERROR で1回出す。トレースID は既存の仕組みでログと監査の行に入る。
- 警報は既存の決まり（5xx の率・p95）がグループの API にも `uri` のラベルで効く。新しい警報は足さない（配備先が決まるまでは手元の監視、`project.md`）。

### テスト・品質

- 必須のテストは機能設計の `functional-spec.md` の 7節のとおり（`team.md` の「認証・認可・監査」「役割・権限の機能」）。名前の正規化と鍵の関数に jqwik の性質ベースのテスト。
- カバレッジ: B3 で本体に手が入る `group.*`（新しい）・`audit.domain`・`audit.service`・`user.domain`・`user.service`（メンバーの要約をまとめて読む口）・`useradmin.domain`（USER_NOT_FOUND の移し替え）は、どれも `packagesJudgedByTotal`（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）に入っていない。`audit.repository` には手を入れない見込みで、一覧の作業（下限を満たして外す）は付かない見込み。手を入れることになったら `team.md` のとおり下限を満たして一覧から外す。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`。
- E2E は U3 では足さない（画面は U6。この Intent の E2E は最大2本で、F の流れは U6・U7 が持つ）。

---

## この段で決める要点（質問にしない案。まとめの確認で確かめる）

- **違反の後に新しいトランザクションを開く担当**（機能設計の再レビュー R-11）: グループの業務処理は、利用者の管理と同じく `@Transactional` を付けず `TransactionTemplate` でトランザクションを作る。違反を受けた1つ目のトランザクションは、その中で巻き戻しの印を付けて「違反」の結果の型を返して終える（確定させない）。同じ業務処理の方法の中で、続けて2つ目の `TransactionTemplate`（書き込みの無いトランザクション）を実行し、その中で失敗の出来事だけを出す。入れ子の REQUIRES_NEW は使わない（1つ目の接続を持ったまま2本目を借りないため、接続プールの使い方が今の形から増えない）。U4 role も同じ形にそろえる。
- **detail の 16,384 文字の数え方**（再レビューの R-03）: 列の `VARCHAR` の数え方に合わせ、Java の文字列の長さ（UTF-16 の単位）で数える。U4 が要約に切り替える基準も同じ数え方にする。
- **0 以下の ID**（機能設計のレビューの R-06）: 利用者の管理と同じく、道の `groupId`・本文の `userId` が 0 以下でも 400 にせず、存在しない ID として `GROUP_NOT_FOUND`・`USER_NOT_FOUND`（変える操作は監査に FAILURE）にする。排他の前に有無を読むため、行の排他やダミーの行には触れない。IDOR のテストに 0 と負の値を入れる。
- **操作した人の確かめ直しの窓**（機能設計のレビューの R-05）: 要求の入口で管理者の印を確かめた後、書き込みまでの間に別の管理者に印を外されても、その1件の要求は通る。グループの操作は管理者の印・停止を変えず、次の要求からは入口で拒否されるため、この窓を受け入れる。その旨を `security-requirements.md` に書く。
- **詳細の全件返しの規模**（機能設計のレビューの R-09）: Q2 の答えで決める。
- **コード生成の計画への引き継ぎ**（機能設計のレビューの R-07・R-08）: 移行の番号は V10 と固定せず、コード生成の時点の次の空き番号を使う（先行の B1・B2 は移行を足さない見込み）。USER_NOT_FOUND の移し替えで手を入れる既存のクラス（`UserAdminProblemTypes`・`UserAdminProblemTypeCatalog`・`UserProblemTypes`・`UserProblemTypeCatalog`・`UserAdminController` の参照）とテスト（`UserAdminProblemTypesTest`・`UserAdminProblemTypeCatalogTest` と、code の一覧を確かめる既存のテスト）を洗い出し、1つのコミットの中で「useradmin の一覧から外す」と「user の一覧に足す」を同時に行う（code の二重の定義で起動が止まらないように）。`tech-stack-decisions.md` に引き継ぎとして書く。
- **B5 での認可のテストの足し直し**（機能設計のレビューの R-10）: B5 で作業ロールを持つ利用者が作れるようになったら、グループの管理の 7 つの口の 403 の表に「全スキーマを FULL にしたロールを作業ロールにする、管理者の印だけを欠く利用者」を足す。U4 への引き継ぎに入れる。

---

## Q1 グループの管理の API の応答時間の目標と、負荷の試験に含めるか

背景: 要件 NFR2.2 は「権限の設定の木・メニュー・作業ロールの切り替え」に 95 パーセンタイル 1 秒を置いており、グループの管理の API は挙がっていません。前の Intent の利用者の管理の API では、すべての操作に p95 1 秒を置き、k6 の場面（`userAdminList`・`userAdminOps` など）を Performance Validation で流し、接続プールの上限に届かせる場面（`userAdminPoolLimit`）も足しました。グループの書き込みは、利用者の管理と同じく確定の後の監査で接続を2本使います（`project.md` の学び「接続プールの見積もりを確かめる負荷の試験には、上限に届く形を含める」）。この Intent の流れには Performance Validation の段があります。

A. 7つの口のすべてに p95 1 秒（`checks` の率 1、目標は緩めない）を置き、k6 にグループの場面（一覧・詳細・作成と名前の変更と削除・メンバーの足し外しを組で状態を戻しながらくり返す）と、接続プールの上限を下げて上限に届かせる場面を足して、Performance Validation で流す。台本の書き換えはコード生成（B3）の持ち物にする。測れない段では `Unverified` とし、持ち主の段を明記して引き継ぐ（推奨: 利用者の管理と同じ形で、2本使いの見積もりも上限に届く形で確かめられる。利用者の管理の場面と道具を使い回せる）
B. A と同じ目標と場面を置くが、接続プールの上限に届かせる場面は足さない（利用者の管理で同じ2本使いの形を確かめ済みとみなす。試験の時間は短いが、グループの経路での見積もりは確かめない）
C. グループの API には時間の目標を置かず、結合テストで問い合わせの数が規模に比例して増えないこと（一覧・詳細が N+1 にならない）だけを確かめる（要件に無い目標を足さない代わりに、応答時間は測らない）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 規模の前提と、詳細のメンバーの全件返しの上限

背景: 要件の前提 A6 は、グループの数（100 程度）を「仮に置いた。NFR 要件の段で確かめる」としています。利用者は 50 名程度の前提です（NFR2.1）。承認済みの機能設計は、詳細でメンバーをページ送りせずに全件返します（BR7.4。画面の候補で「メンバー済み」を示すのに全件が要る）。機能設計のレビューの R-09 は、利用者が増えると詳細の応答と読み取りが際限なく大きくなると指摘しました。前の Intent の利用者の管理の負荷の試験は、試験用の利用者 1,000 名で流しました。

A. 規模の前提はグループ 100・利用者 50 を目安として確定し、件数の上限（グループの数・1つのグループのメンバーの数）はアプリで強制しない。詳細の全件返しはそのままとし、試験の悪い側はグループ 1,000・利用者 1,000 名・メンバー 1,000 人のグループの詳細で、Q1 の目標を確かめる（利用者の管理の試験と同じ規模で、目安の 10〜20 倍の余裕を確かめる）（推奨: 契約 C6 と承認済みの機能設計を変えずに、大きくなったときの重さを数字で押さえられる）
B. A と同じ前提だが、1つのグループのメンバーの数に上限（例: 1,000 人）を置き、超える追加を業務の誤りで拒否する（新しい code と監査の理由が要り、承認済みの機能設計と契約 C6 に足す変更になる）
C. 詳細のメンバーをページ送りに変える（契約 C6 と承認済みの機能設計の変更。画面の「メンバー済み」の印は別の読み方が要る）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（group の NFR 要件）:

- Q1 A: グループの管理の API の7つの口すべてに p95 1 秒（checks の率 1）を置く。k6 にグループの場面と、接続プールの上限を下げて上限に届かせる場面を足し、Performance Validation で流す。台本はコード生成（B3）の持ち物。
- Q2 A: 規模の前提はグループ 100・利用者 50 を目安として確定し、件数の上限はアプリで強制しない。試験の悪い側はグループ 1,000・利用者 1,000 名・メンバー 1,000 人のグループの詳細で確かめる。
- ID の振り方（上流の枝番と同じ番号は同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振り、各成果物の冒頭に対応表。traceability.json は上流の枝番ごとに行を立て、当たらないものは理由つきの N/A、混ざるものは「部分」）。
- 決まっていること（承認済みの機能設計の守り、新しい依存は足さない、漏えいは GroupSecretLeakIT・AuditSecretLeakIT と一意の違反の例外のテスト、指標は既存の http.server.requests、業務のログは GROUP_BUSY の WARN だけ、手を入れるパッケージは packagesJudgedByTotal に入らない）のとおり。
- 機能設計のレビューで残った点の手当て: 違反の後の失敗の監査は TransactionTemplate を2回順に実行（REQUIRES_NEW の入れ子は使わず、role もそろえる）、detail の 16,384 文字は Java の文字列の長さで数える、0 以下の ID は 404、入口の判定の後に管理者の印を外されてもその1件の要求は通る窓を受け入れる、移行の番号はコード生成の時点の次の空き番号、USER_NOT_FOUND の移し替えは1つのコミットで一覧の付け替えを同時に行う、B5 で管理者の印だけを欠く利用者を 403 の表に足すことを role へ引き継ぐ。グループの名前は個人に関する値として扱わない（監査の detail に名前を残す）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
