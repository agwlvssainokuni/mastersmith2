# 業務の決まり: u2-portable-sql（機能設計。Intent 261114-internal-db-postgresql）

この単位の決まりを `BR<グループ>.<連番>` で並べます。グループは、BR1 本人の表示の設定の保存、BR2 「あれば更新・無ければ挿入」の書き込み、BR3 書き直さない文、BR4 大きなバイナリの列（確かめだけ）、BR5 検索、BR6 テスト、BR7 構造です。源（`source`）は要件定義の FR・NFR、または質問の答え（`Q1: A` など。この段の `functional-design-questions.md`）です。

次の YAML のブロックが決まりの正（source of truth）です。

```yaml
rules:
  # ---- BR1 本人の表示の設定の保存（UserRepository.savePreferences） ----
  - id: BR1.1
    statement: "本人の表示の設定の保存は、更新と更新後の 2 列の取り出しを 1 文のまま、H2 と PostgreSQL の両方に通る形で行う（JDBC の生成キー）。"
    category: constraint
    applies_to: [UserPersistence, ENT-003]
    trigger: "本人が表示の設定（氏名・言語・テーマ・文字の大きさ・表示のタイムゾーン）を保存する"
    logic: >
      IF savePreferences を呼ぶ
      THEN H2 の FINAL TABLE (UPDATE ...) をやめ、UPDATE users SET ... WHERE user_id = ? を JDBC の生成キー（Session.doReturningWork の中で
      prepareStatement(sql, 列名の配列) と getGeneratedKeys()。列名は小文字の user_id・display_time_zone）で 1 文として流し、更新後の 2 列を受ける。
      内部DB の文の数は変えない（NFR2.1.2）。DB の種類で分岐しない（ADR-003）。
    violation: "H2・PostgreSQL の結合テストの失敗（PostgreSQL は U3）"
    source: "FR3.5; 質問 Q1: A（2026-11-22、依頼者の決定）"

  - id: BR1.2
    statement: "保存の結果の意味は今と同じにする: 本人がいなければ空、表示のタイムゾーンは change が真のときだけ書き換える。"
    category: constraint
    applies_to: [UserPersistence, ENT-003]
    trigger: "savePreferences の結果"
    logic: >
      IF 更新が 0 行（本人がいない）THEN 結果は空（Optional.empty）。IF change が偽 THEN display_time_zone は今の値のまま返す。
      IF change が真で zone が null THEN 列を空（NULL）にして返す。CASE WHEN :change = TRUE THEN CAST(:zone AS VARCHAR(64)) ELSE display_time_zone END の形は
      両方に通る（捨ての試し。束縛は名前つきのままでよい）。
    violation: "既存の UserRepositoryIT・PreferencesPartialUpdateIT・DisplayTimeZoneKeptByOtherUpdatesIT の失敗"
    source: "FR3.5; FR5.4"

  - id: BR1.3
    statement: "値は束縛変数だけで渡し、文字列を連結しない。氏名を伏せる型を、文字列にせず束縛の直前まで保つ。"
    category: constraint
    applies_to: [UserPersistence]
    trigger: "実装"
    logic: >
      IF JDBC の PreparedStatement で更新を流す THEN 氏名（伏せ字の型）・言語・テーマ・文字の大きさ・タイムゾーンの値は、束縛の直前に取り出す
      （今は SpEL の中で取り出している）。メソッドの引数と戻り値は今の形のまま（TraceAspect が引数を文字列にするため、氏名は伏せ字の型のまま渡す。project.md の Code Style）。
      例外は受けた所でクラス名だけを扱い、値（氏名・タイムゾーン）をログ・例外の文に入れない。
    violation: "PreferencesSecretLeakIT の失敗"
    source: "FR3.5; NFR2; project.md の Code Style"

  # ---- BR2 「あれば更新・無ければ挿入」の書き込み ----
  - id: BR2.1
    statement: "work_role_selections と dsl_previews への「あれば更新・無ければ挿入」は、更新 → 0 行なら挿入（セーブポイント）→ 鍵の重なりなら戻して更新をやり直す、の手順で行う。"
    category: policy
    applies_to: [RolePersistence, DslManagePersistence, ENT-001, ENT-002]
    trigger: "作業ロールの保存・プレビューを置く"
    logic: >
      IF 書き込みを行う
      THEN (1) UPDATE ... WHERE <鍵> = ? を流す。1 行に当たれば UPDATED で終わる。
      (2) 0 行なら、JDBC のセーブポイントを張って INSERT を流す。通れば INSERTED で終わる（セーブポイントは解放する）。
      (3) INSERT が鍵の重なり（SQLState 23505 で、文に表の名前を含む。BR2.3）なら、セーブポイントまで戻して同じトランザクションのまま UPDATE をやり直す。1 行に当たれば UPDATED_AFTER_CONFLICT。
      H2 の MERGE ... KEY は使わない（PostgreSQL に通らない）。標準の MERGE ... USING も使わない（同時の実行で後の側が 23505 で失敗し、H2 の「後勝ち」と変わる）。
      呼び出し側の戻りは、書いた行数 1 のまま（repository の口を変えない。契約 C5）。
    violation: "同時の実行のテスト（H2・PostgreSQL）の失敗"
    source: "FR3.5; 質問 Q2: A（依頼者の決定）; FR5.4"

  - id: BR2.2
    statement: "同時に同じ鍵（行が無い状態）へ書いた 2 つのうち、後に確定した方が残る（後勝ち）。H2 の今の結果と同じ。"
    category: constraint
    applies_to: [RolePersistence, DslManagePersistence]
    trigger: "同じ鍵へほぼ同時に 2 つのトランザクションが書く"
    logic: >
      IF 先の側が挿入を持ったまま（未確定）、後の側が書く
      THEN 先の側が確定するまで待った後、後の側は 23505 を受けて BR2.1 (3) で更新をやり直し、最終の行は後の側の値になる
      （DSL のプレビューは、DSL 管理の単位の機能設計が「後に確定した方が残る」と決めた振る舞いと同じ）。行が既にある鍵への同時の書き込みは、行の排他で直列になり、後の側の値が残る。
    violation: "同時の実行のテスト（H2）の失敗。PostgreSQL での確かめは U3"
    source: "FR3.5; FR5.4; 質問 Q2: A"

  - id: BR2.3
    statement: "「鍵の重なり」の判定は SQLState 23505 と、大文字にした文に表の名前を含むことで行う。"
    category: constraint
    applies_to: [RolePersistence, DslManagePersistence, ENT-001]
    trigger: "挿入が 23505 になったとき"
    logic: >
      IF SQLState が 23505 かつ 大文字にした文（制約の名前を含む）が表の名前（WORK_ROLE_SELECTIONS・DSL_PREVIEWS）を含む
      THEN 鍵の重なりとみなして BR2.1 (3) へ。そうでなければ想定外の失敗として扱う。H2 は制約名を大文字、PostgreSQL は小文字で返す
      （dsl_previews の主キーは H2 が CONSTRAINT_B のような自動名で、PostgreSQL は dsl_previews_pkey のため、文に含まれる表の名前で判定する）。
      今の RoleStoreClassifier の SELECTION_KEY の照合の仕方（大文字にして照合）と同じ。
    violation: "単体テスト（判定の表）の失敗"
    source: "FR3.5"

  - id: BR2.4
    statement: "セーブポイントの組み立ては、挿入を Hibernate の例外変換に通さない JDBC（Session.doWork の中）で行い、例外はその中で受ける。"
    category: constraint
    applies_to: [ENT-001]
    trigger: "実装"
    logic: >
      IF 挿入が 23505 になる THEN createNativeQuery(...).executeUpdate() で挿入して PersistenceException を受ける形にしない
      （Hibernate がトランザクションに rollback-only の印を付け、セーブポイントまで戻しても外れず、確定のときに UnexpectedRollbackException になる。捨ての試し）。
      挿入は Session.doWork の中の PreparedStatement で行い、SQLException をその中で受けて外へ出さない（外へ出すと PostgreSQL では変換の経路で同じ印が付く場合があった）。
      PostgreSQL は 23505 の後、セーブポイントを使わないと同じトランザクションの次の文が 25P02（中断）になる（H2 は中断しない）。
    violation: "同時の実行のテストの失敗（確定のときの UnexpectedRollbackException・25P02）"
    source: "FR3.5; ADR-003"

  - id: BR2.5
    statement: "上限切れは収束させず失敗として出す。鍵の重なりの後の更新が 0 行のときは想定外の失敗にする。"
    category: policy
    applies_to: [RolePersistence, DslManagePersistence]
    trigger: "待ちの上限（H2 約 4 秒・PostgreSQL 約 2 秒の lock_timeout）に当たる、または重なりの後の更新が 0 行"
    logic: >
      IF 待ちの上限切れ THEN 挿入・更新のやり直しに入らず、今までと同じ失敗（RowLockFailures で上限切れと分類。作業ロールは Busy、プレビューは今の経路）として出す。
      その後、セーブポイントまで戻せばトランザクションは使い続けられる（捨ての試し）。
      IF 23505 の後の更新が 0 行（先の側が行を消した等）THEN 想定外の失敗として扱う（結果を作らない）。
    violation: "単体テストの失敗"
    source: "FR3.5; FR5.4"

  - id: BR2.6
    statement: "RowLockFailures に PostgreSQL の SQLState 55P03（lock_not_available）を足し、JDBC の中で受けた生の例外も上限切れと分類する。"
    category: constraint
    applies_to: [common.persistence.RowLockFailures]
    trigger: "実装"
    logic: >
      IF 連なりの SQLException の SQLState が 55P03 THEN 上限切れ（今の 50200・40001・HYT00 と同じ扱い）。
      Hibernate の例外変換を通る経路（LockTimeoutException・PessimisticLockException）は今の型の判定で当たる。H2 の分類は変えない。
      businessdata の RowSqlFailureClassifier が 55P03 を競合として別に持つが、これは別の用途（対象DB）で、触れない。
    violation: "RowLockFailuresTest の失敗"
    source: "FR3.5; FR3.3 の分類（U1・U3）"

  - id: BR2.7
    statement: "書き込みの手順は 1 つの部品（common.persistence）に閉じ込め、repository は表ごとの更新・挿入の文と束縛だけを渡す。"
    category: policy
    applies_to: [ENT-001, RolePersistence, DslManagePersistence]
    trigger: "設計・実装"
    logic: >
      IF 2 つの表が同じ手順を使う THEN 手順（BR2.1〜BR2.5）は common.persistence の 1 つの部品に置き、RoleStore・DslPreviewRepository は文と束縛を渡すだけにする。
      部品は機能のパッケージ（role・dslmanage など）に依存しない。引数に個人に関する値・秘密を取る部品は TraceAspect の対象の層の外に置く
      （プレビューの本文・時刻・利用者 ID が引数になるが、いずれも今の repository の引数と同じ種類。部品の置き場が層の外のため、TRACE に文字列で出ない）。
    violation: "ArchUnit の境界テストの失敗"
    source: "FR3.5; ADR-003; NFR9"

  # ---- BR3 書き直さない文 ----
  - id: BR3.1
    statement: "DslAppliedRevisionRepository.copyFromPreview と、Auth・招待の今のネイティブの SQL は書き直さない。"
    category: constraint
    applies_to: [DslManagePersistence, AuthPersistence, InvitationPersistence]
    trigger: "設計・実装"
    logic: >
      IF 次の文 THEN そのまま使う: copyFromPreview（CAST(? AS ...) を含む INSERT ... SELECT）、LoginAttemptStateRepository.createIfAbsent（MERGE ... USING (VALUES ...)）、
      RefreshTokenRepository・InvitationRepository の FETCH FIRST つきの削除。実際の Repository の Bean 経由で H2・PostgreSQL の両方に通り、結果が同じことを捨ての試しで確かめた
      （10MB の SHA-256 一致・同時の createIfAbsent は両方で 23505）。PostgreSQL での確かめは U3。
    violation: "なし（書き直さない決まり）"
    source: "FR3.5; 契約 C5 の見込み"

  # ---- BR4 大きなバイナリの列（確かめだけ） ----
  - id: BR4.1
    statement: "DSL の本文 2 列（dsl_previews.yaml_bytes・dsl_applied_revisions.yaml_bytes）の写し方は U1 が直した形（@JdbcTypeCode(SqlTypes.LONG32VARBINARY)）を前提にし、この単位は H2 での確かめだけを持つ。"
    category: policy
    applies_to: [DslManagePersistence, ENT-001]
    trigger: "実装・テスト"
    logic: >
      IF 本文の列を読み書きする THEN 今の写し方（U1 のコード生成で直した組）のまま。H2 の結合テストの全件が通ること（ddl-auto: validate を含む）で確かめる。
      10MB ちょうどの本文の往復は、H2 の既存のテストに無ければ足す（プレビューを置いて読み、バイト列が一致する）。PostgreSQL での往復は U3。
      プレビューを置く書き込み（BR2.1）は、10MB の本文でも H2・PostgreSQL の両方で通ることを捨ての試しで確かめた。
    violation: "H2 の結合テストの失敗"
    source: "FR3.6; 契約 C3; U1 の質問 Q1: A"

  # ---- BR5 検索 ----
  - id: BR5.1
    statement: "利用者の検索は書き直さない。H2 と PostgreSQL の差（Unicode の端の 3 種）は既知の違いとして書く。"
    category: policy
    applies_to: [UserPersistence]
    trigger: "利用者の検索"
    logic: >
      IF 利用者の検索（ilike :pattern escape '\\'、SearchText.likePattern() が Locale.ROOT で小文字にして \\・%・_ をエスケープする）THEN コードは変えない。
      日本語・全角半角・カナ・%・_・\\・!・連続空白・結合文字・長い入力は、H2 と PostgreSQL で同じ結果（36 人の見本・約 55 個の入力）。
      違いは İ（PostgreSQL は i に当たる）・語末シグマ（H2 は σ と ς を同一視）・ǅ（PostgreSQL は ǆ に当たる）の 3 種だけで、既知の違いとして docs/internal-db.md に書く（U4）。
      PostgreSQL の standard_conforming_strings は on で、escape '\\' は \\ 1 文字として働く。
    violation: "なし（既知の違い）"
    source: "FR3.8; 質問 Q3: A（依頼者の決定）"

  # ---- BR6 テスト ----
  - id: BR6.1
    statement: "書き直した文は、普段の ./gradlew verify の H2 の結合テストの全件で確かめる。結果を変えず、テストは減らさない。"
    category: validation
    applies_to: [H2 の結合テスト]
    trigger: "普段の verify・verifyQuick"
    logic: >
      IF 書き直した文がある THEN 既存の結合テスト（UserRepositoryIT・PreferencesPartialUpdateIT・DisplayTimeZoneKeptByOtherUpdatesIT・RoleStore の IT・DslManageRepositoryIT など）が
      書き直した後も同じ結果で通る。足りない所は H2 の結合テストを足す。
    violation: "テストの失敗"
    source: "FR5.4; FR3.5"

  - id: BR6.2
    statement: "WorkRoleSelectionMergeIT は新しい手順の期待に書き直す。同時の実行（後勝ち・上限切れ・23505 の後のやり直し）の確かめを H2 で足す。"
    category: validation
    applies_to: [WorkRoleSelectionMergeIT, 新しい IT・単体テスト]
    trigger: "実装・テスト"
    logic: >
      今の WorkRoleSelectionMergeIT は H2 の MERGE ... KEY に対し、保持が約 4 秒を超えたときの上限切れ（誤りの番号 50200）だけを確かめている。
      新しい手順では、(a) 保持が短い（約 500ms）ときは後の側が待ってから 23505 を受け、やり直しで後の側の値が残る、(b) 保持が長い（約 4 秒超）ときは上限切れの失敗（RowLockFailures で上限切れ）、
      (c) 上限切れの後に同じトランザクションが使い続けられる（セーブポイントまで戻した後の次の文が通る）、を待ち合わせで確実に作って確かめる（スレッドの数・実時刻の sleep に頼らない。
      待ち合わせ用の部品は testsupport に置く）。DSL のプレビューを置く経路の同時の実行（後に確定した方が残る）も H2 の結合テストを足す（今は無い）。
      待ちの上限の秒数（H2 約 4 秒）は、テストの中で定数にせず、一度測って上限の目安として使う。
    violation: "テストの失敗"
    source: "FR5.4; FR3.5"

  - id: BR6.3
    statement: "鍵の重なりの判定（BR2.3）・手順の分岐（BR2.1・BR2.5）・RowLockFailures の 55P03（BR2.6）は、普段の verify の単体テストで全分岐を通す。"
    category: validation
    applies_to: [単体テスト, common.persistence, role.store, dslmanage.repository]
    trigger: "普段の verify・verifyQuick"
    logic: >
      IF パッケージごとのカバレッジの下限（行 80%・分岐 70%）がある THEN 新しい部品と書き換えた repository は、コンテナ無しの単体テスト（判定の表・更新 0 行・挿入が通る・重なり・重なり後の更新 0 行）と
      H2 の結合テストだけで下限を満たす。計測の除外を足さない。
    violation: "./gradlew verify のパッケージごとの下限の失敗"
    source: "FR5.4; team.md の Testing Posture"

  # ---- BR7 構造 ----
  - id: BR7.1
    statement: "業務処理の層と repository の口は内部DB の種類で分岐しない。既存の境界テストは緩めない。"
    category: constraint
    applies_to: [ArchUnit の境界テスト]
    trigger: "実装"
    logic: >
      IF user・role・dslmanage の本体のソースに手を入れる THEN 既存の境界テスト（UserBoundaryArchitectureTest・RoleBoundaryArchitectureTest・DslManageBoundaryArchitectureTest・
      ArchitectureTest）はそのまま通る（緩めない・消さない）。common.persistence に足す部品は機能のパッケージに依存しないことを確かめる検査を足す。
      TraceAspect の対象の層で個人に関する値を文字列にしない今の形（RedactedText など）を崩さない。
    violation: "境界テストの失敗"
    source: "NFR9; team.md の Code Style"
```

## 決まりの要約（`rules.md` の YAML から作った人が読む表）

| ID | 区分 | 一言 | 源 |
|---|---|---|---|
| BR1.1 | constraint | 表示の設定の保存は、JDBC の生成キーで 1 文のまま両方に通す | FR3.5・Q1: A |
| BR1.2 | constraint | 保存の結果の意味（本人がいなければ空・change が偽なら今の値）は今と同じ | FR3.5・FR5.4 |
| BR1.3 | constraint | 値は束縛だけで渡し、氏名の伏せ字の型を束縛の直前まで保つ | FR3.5・NFR2 |
| BR2.1 | policy | あれば更新・無ければ挿入は、更新 → 挿入（セーブポイント）→ 重なりなら更新やり直し | FR3.5・Q2: A |
| BR2.2 | constraint | 同時の実行は後勝ち（H2 の今の結果と同じ） | FR3.5・FR5.4 |
| BR2.3 | constraint | 鍵の重なりは SQLState 23505 と、文に表の名前を含むことで判定 | FR3.5 |
| BR2.4 | constraint | 挿入は JDBC（Session.doWork）で行い、例外はその中で受ける | FR3.5・ADR-003 |
| BR2.5 | policy | 上限切れは収束させず失敗として出す。重なりの後の更新が 0 行は想定外 | FR3.5・FR5.4 |
| BR2.6 | constraint | RowLockFailures に 55P03 を足す | FR3.5 |
| BR2.7 | policy | 手順は common.persistence の 1 つの部品に閉じ込める | FR3.5・NFR9 |
| BR3.1 | constraint | copyFromPreview・Auth・招待の SQL は書き直さない | FR3.5 |
| BR4.1 | policy | DSL の本文 2 列は U1 が直した写し方のまま。H2 での確かめだけを持つ | FR3.6・C3 |
| BR5.1 | policy | 検索は書き直さず、Unicode の端の違いは既知の違いとして書く | FR3.8・Q3: A |
| BR6.1 | validation | 書き直した文を H2 の結合テストの全件で確かめる | FR5.4 |
| BR6.2 | validation | WorkRoleSelectionMergeIT を書き直し、同時の実行の確かめを H2 で足す | FR5.4・FR3.5 |
| BR6.3 | validation | 新しい部品は単体テストと H2 の結合テストだけで下限を満たす | FR5.4 |
| BR7.1 | constraint | 種類で分岐しない。既存の境界テストは緩めない | NFR9 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR3.5・FR3.6・FR3.8・FR5.4・NFR2・NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-003・ADR-006）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C3・C5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-design-questions.md`（Q1: A・Q2: A・Q3: A、捨ての試しの結果）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（E14）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`
- コードで確かめたこと（2026-11-22）: `UserRepository`・`RoleStore`・`RoleStoreClassifier`・`DslPreviewRepository`・`DslAppliedRevisionRepository`・`RowLockFailures`・`RowLockFailuresTest`・`WorkRoleSelectionMergeIT`・`DslManageRepositoryIT`・`DslConcurrencyIT`
