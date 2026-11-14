# 設計の判断の記録（ADR。Domain Design。Intent 261114-internal-db-postgresql）

この Intent の範囲（classic）には Feasibility の段が無いため、実現できるかの判断もこの段で扱う（`project.md` の Way of Working）。ADR-006 がそれに当たる。

## ADR-001: 内部DB の種類の判断を新しい部品 InternalDbPlatform の1か所に閉じる

- **Status**: Proposed（承認の場で確定）
- **Context**: 内部DB に PostgreSQL を足すと、移行の置き場・接続ごとの待ちの上限など、種類によって違う設定が要る。今の内部DB の接続は `spring.datasource.*`（環境変数 `MASTERSMITH_DB_*`）で差し替えられるが、方言の指定・`connection-init-sql` は無く、種類を判断する所はどこにも無い（`architecture.md` の K-146）。要件は、接続先から種類を決めて別の設定を足さずに選べること（FR1.2）、対応しない種類で起動を止めること（FR1.3）、業務処理の層が種類で分岐しないこと（NFR9）を求める。
- **Decision**: 種類の判断（H2・PostgreSQL・対応しない種類）と種類ごとの設定（移行の置き場、PostgreSQL のときの接続ごとの待ちの上限）を、新しい部品 `InternalDbPlatform` に閉じる。種類は接続先の URL の先頭（`jdbc:h2:`・`jdbc:postgresql:`）から起動時に1回だけ決める。ほかの部品は種類を知らない。Java のパッケージの置き場（`config` か `common.persistence` の下位か）とクラスの名前は機能設計で決める。
- **Consequences**:
  - 良い点: PostgreSQL のときだけ通るコードがこの部品と移行に集まり、純粋な判断の部分は単体テストで確かめられる（NFR6）。後の Intent で MySQL・MariaDB を足すときも、この部品に種類を足す形になる。
  - 悪い点: 起動時の設定の組み立てが1か所に増え、Spring Boot の自動設定（DataSource・Flyway）との順序を機能設計で詰める必要がある。
  - 続く判断: PostgreSQL の接続ごとの待ちの上限の渡し方と、起動時の移行がその上限で切れない形は NFR 設計で決める（NFR4）。PostgreSQL のドライバーのログの抑えが内部DB の失敗の原因を隠さない形（FR3.10）は機能設計で決める。
- **Alternatives Rejected**:
  - 種類ごとの設定を `application.yaml` の profile（例: `postgresql` の profile）で持ち、利用者に profile を選ばせる: 接続先と profile の2つを合わせて指定する必要があり、ずれると起動してから壊れる。FR1.2（ほかの設定を足さずに選べる）に合わない。
  - 各 repository が種類を見て分岐する: 種類での分岐が業務の部品に散り、PostgreSQL のときだけ通るコードが増える（ADR-003 とも合わない）。
- **Security**: 接続先の URL は判断に使うだけで、ログ・応答に出さない（NFR2、`project.md` の Forbidden）。止めるときのログには設定の名前と対応する種類だけを出す。

## ADR-002: PostgreSQL 用の移行を別の置き場に一式置き、H2 の移行には触れない

- **Status**: Proposed（依頼者の答え Q1: A）
- **Context**: 今の移行 V1〜V14 は1つの置き場にあり、H2 にしか通らない書き方（`BINARY LARGE OBJECT`・`BINARY(32)`・`STORED` の無い生成列の一意・`ON DELETE RESTRICT`）を含む（`code-structure.md` の K-144）。H2 の適用済みの移行は書き換えられない（Flyway の検証 `validate-on-migrate: true`、FR2.2）。
- **Decision**: H2 用の移行は今の置き場のまま残す。PostgreSQL 用に、V1〜V14 と同じ版の番号・同じ表・同じ名前の制約と索引の移行を、別の置き場に一式置く。`InternalDbPlatform` が種類で置き場を選んで Flyway に渡す。後の Intent で表を変えるときは、両方の一式に同じ版の番号で足す（FR2.3）。
- **Consequences**:
  - 良い点: H2 の移行のファイルに一切触れず、今の H2 の内部DB の検証と既存の結合テストに影響しない。PostgreSQL の移行は PostgreSQL の型で素直に書ける。
  - 悪い点: 表の形の定義が2つになり、ずれるおそれがある。ずれは PostgreSQL の結合テスト（移行と `ddl-auto: validate` の照合）で拾うが、そのテストは普段は流れない（明示の指定のとき）。両方の一式の版の番号の一致を普段の verify で確かめる手立て（単体テストなど）は機能設計で決める。
- **Alternatives Rejected**:
  - 両方に通る部分を共通の置き場に移し、違う部分だけを種類ごとに分ける（Q1: B）: H2 の移行のファイルを動かす・分けることになり、適用済みの検証（版・説明・チェックサム）との整合を確かめる手間と危険が増える。
  - PostgreSQL では Hibernate に表を作らせる（`ddl-auto: update` など）: 制約の名前と前進のみの移行の決まり（`team.md` の Deployment）が守れない。

## ADR-003: H2 にしか通らない SQL を、両方に通る書き方に書き直す（DB の種類で分岐しない）

- **Status**: Proposed（依頼者の答え Q2: A）
- **Context**: 内部DB のネイティブの SQL のうち、`UserRepository.savePreferences` の `FINAL TABLE (UPDATE ...)`、`RoleStore` と `DslPreviewRepository` の `MERGE INTO ... KEY`、`DslAppliedRevisionRepository` の `CAST(? AS ...)` を含む `INSERT ... SELECT` が PostgreSQL で通らない見込み（`api-documentation.md` の K-145）。標準の `MERGE INTO ... USING (VALUES ...)` は、ログインの状態の行で既に H2 で使っている。PostgreSQL 15 以降は標準の `MERGE` を受ける。
- **Decision**: これらを H2 と PostgreSQL の両方に通る書き方（標準の `MERGE INTO ... USING`、更新と読み直しを同じトランザクションの中で分ける、型の変換をバインドの側で行う、など）に書き直し、repository の中で DB の種類による分岐を作らない。結果（返す値・更新の有無の判定・違反の区分）は今と同じにする。具体の SQL は機能設計で決め、両方で同じ結果になることを、文書を書く前に捨ての試しで確かめる（`project.md` の Corrections）。
- **Consequences**:
  - 良い点: 書き直した SQL は H2 の結合テストの全件（普段の verify）でそのまま確かめられ、PostgreSQL のときだけ通るコードが増えない（NFR6）。
  - 悪い点: H2 の今の SQL も変わるため、H2 での振る舞いが変わらないことを既存の結合テストで確かめる必要がある。`MERGE` の同時の実行での振る舞い（一意の違反の出方）が H2 と PostgreSQL で違うおそれがあり、捨ての試しと PostgreSQL の結合テストで確かめる。
- **Alternatives Rejected**:
  - DB の種類ごとに SQL を書き分け、repository の中で種類を見て選ぶ（Q2: B）: 種類での分岐が業務の部品に散り（ADR-001 と合わない）、PostgreSQL の側の SQL は普段の verify で通らないためカバレッジの下限を守りにくい。
  - ネイティブの SQL をやめて JPA の読み取りと保存に置き換える: 同時の実行での振る舞い（重なりの扱い）が変わり、今の結果と同じにする確かめが大きくなる。

## ADR-004: PostgreSQL の移行では外部キーを NO ACTION で作り、違反の区分の部品は変えない

- **Status**: Proposed（依頼者の答え Q3: A）
- **Context**: 下調べで、PostgreSQL の `ON DELETE RESTRICT` の外部キーでは、参照のある親を消すと SQLState 23001（restrict_violation）になり、Hibernate が制約の名前を取れず、今の区分の部品（`RoleStoreClassifier`・`StoreFailureClassifier`。23505・23503・23506 だけを見る）では想定外の失敗に分けられることを確かめた。ON DELETE の指定なし（NO ACTION）なら 23503 で制約の名前も取れる（下調べの2節、`architecture.md` の K-148）。H2 の移行には `ON DELETE RESTRICT` の外部キーが 7 つある（V10〜V12）。
- **Decision**: PostgreSQL 用の移行では、外部キーに ON DELETE の指定を付けない（NO ACTION）。制約の名前は H2 と同じにする。区分の部品は変えない。
- **Consequences**:
  - 良い点: 区分の部品に PostgreSQL の差を持ち込まず、H2 と同じ応答になる（FR3.2）。区分の部品の判定の表は変わらない。
  - 悪い点: H2 と PostgreSQL で外部キーの定義の文字が違う（意味は1つの文の中では同じ。違いは確かめの時点だけで、今のアプリは遅らせた確かめ（DEFERRABLE）を使わない）。後の Intent で DEFERRABLE を使うときは見直しが要る。この前提は PostgreSQL の移行の一式に注記として残す。
- **Alternatives Rejected**:
  - PostgreSQL でも `ON DELETE RESTRICT` のまま作り、区分の部品に 23001 を足す（Q3: B）: role と group の2つの区分の部品を変える。Hibernate が制約の名前を取れないため、どの制約の違反かを名前で照らす今の形が使えず、区分の作りを変える必要がある。

## ADR-005: PostgreSQL の結合テストを新しく作り、既存の H2 の結合テストは変えない

- **Status**: Proposed（依頼者の答え Q4: A）
- **Context**: `*IT` 278 本のうち 275 本が組み込みの H2 を起動し、テストの部品に H2 にしか通らない書き方（待ち合わせの `INFORMATION_SCHEMA.SESSIONS`、H2 の Java のトリガー、準備の `MERGE ... KEY`・`DATEADD`・`RANDOM_UUID()` など）が多い（`code-quality-assessment.md` の K-151）。要件は、PostgreSQL の結合テストを H2 の全件とは別のバリエーションとして作り、明示の指定のときだけ流すこと（FR5.1〜FR5.3、`team.md` の Testing Posture）と、H2 の結合テストの全件を減らさないこと（FR5.4）を求める。
- **Decision**: PostgreSQL 用の結合テスト（部品 `InternalDbPostgresTestSuite`）を新しく作る。要件の FR5.1 に並べた振る舞いと、内部DB を使う機能ごとの代表の API の流れ1本ずつを置く。既存の H2 の結合テストとテストの部品は変えない。テストの部品は H2 に頼る書き方を使わない。PostgreSQL のイメージは対象DB の PostgreSQL と同じ版とダイジェスト（`TargetDbImages` の値）を使う。明示の指定の名前と CI の手動の実行の入力は機能設計・NFR 設計で決める。
- **Consequences**:
  - 良い点: H2 の結合テストに手を入れず、普段の verify と CI の時間が変わらない（NFR5）。明示して流すときの時間を短く保てる。
  - 悪い点: 機能ごとの代表の流れは新しく書く必要があり、既存の H2 のテストと内容が重なる部分がある。PostgreSQL の結合テストは普段流れないため、壊れても気づくのが遅れうる（Practices Discovery で受け入れた危険）。
- **Alternatives Rejected**:
  - 既存の H2 の結合テストの代表のクラスを、内部DB の種類を切り替えて流せる形にする（Q4: B）: H2 に頼るテストの部品を多く書き直す必要があり、H2 の結合テストの結果を変えるおそれがある。

## ADR-006: 実現できるかの判断（単一のインスタンス・直接の接続・確かめた版だけ）

- **Status**: Proposed（要件定義の Q3・Q5 と前提による）
- **Context**: この Intent の範囲には Feasibility の段が無い。今のアプリは組み込みの H2 を前提に、インスタンスが1つであることに頼る所がある（`role/service/RoleTransferSlot` の説明など。`architecture.md` の K-154 (g)）。PostgreSQL ならインスタンスを複数にすることも考えられる。接続ごとの設定（待ちの上限）は、接続をまとめる中継（PgBouncer の transaction モードなど）を挟むと当てにならない（下調べの 5.3 節）。
- **Decision**: (1) PostgreSQL のときもインスタンスは1つの前提のまま（NFR7）。(2) アプリは PostgreSQL に直接つなぐ前提とし、中継は挟まない（要件の前提）。(3) 対応を約束するのは確かめた版（今は 18 系）だけ（NFR3）。この3つの前提の下で、要件は実現できると判断する。下調べの捨ての試しで、違反の区分・排他と待ち・失敗の後のトランザクションの振る舞いを PostgreSQL 18.6 で確かめている（下調べの2〜4節）。
- **Consequences**:
  - 良い点: 単一のインスタンスの前提に頼る今の作りを変えずに済む。待ちの上限を接続ごとの設定で H2 と同じにできる。
  - 悪い点: PostgreSQL にしても、インスタンスを増やして負荷を分ける使い方はできない（範囲の外）。中継を挟む配備をするときは見直しが要る。
  - 確かめていない前提（設計の段で確かめる）: `@Lob byte[]` の写し方と `bytea` の照合（K-149）、V8 の生成列の一意の PostgreSQL での形（K-144）、標準の `MERGE` の同時の実行での振る舞い（ADR-003）、`lock_timeout` が起動時の移行に当たる影響（NFR4）、排他の後に数える設計の READ COMMITTED での振る舞い（下調べの 5.3 節）。
- **Alternatives Rejected**:
  - 複数のインスタンスに対応する: 同時の操作・ジョブの重なり・セッションなど別の論点が多く、要件の Q3 で範囲の外とした。
  - 古い版（16・17 系）も約束する: 確かめていない版での約束は守れているかが分からない（要件の Q5）。
- **Security**: 中継を挟まず、資格情報は環境変数だけから受け取る（`project.md` の Forbidden）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/domain-design-questions.md`（Q1〜Q4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/practices-discovery/team-practices.md`
- `aidlc/spaces/default/codekb/mastersmith2/architecture.md`（K-146〜K-148・K-154）・`aidlc/spaces/default/codekb/mastersmith2/component-inventory.md`
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
