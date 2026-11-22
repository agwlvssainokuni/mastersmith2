# エンティティ: u2-portable-sql（機能設計。Intent 261114-internal-db-postgresql）

この単位は、内部DB の表を増やしたり変えたりしません（表の形は U1 の移行が持つ）。新しく持つデータは、**H2 にしか通らなかった書き込みの文を両方の DB に通る形に直したときの、書き込みの手順と結果の持ち物**だけです。Hibernate・JDBC・接続プールが持つ状態（セーブポイント・rollback-only の印など）は、エンティティにせず `rules.md` の決まりとして書きます（`project.md` の Code Style）。内部DB の既存のエンティティ（User・WorkRoleSelection・DslPreviewRecord・DslAppliedRevisionRecord など）の持ち主と形は変えません。

次の YAML のブロックがエンティティの正（source of truth）です。

```yaml
entities:
  - id: ENT-001
    name: KeyedUpsert
    description: >
      「同じ鍵の行が無ければ足し、あれば書き換える」書き込み 1 件の手順の入力。更新の文・挿入の文・鍵・同時の実行で鍵が重なったときの扱いを持つ。
      対象は work_role_selections（鍵 user_id）と dsl_previews（鍵 preview_slot）の 2 表だけ。H2 の MERGE ... KEY の代わりに、両方の DB に通る形で書く。
    attributes:
      - name: table
        type: enum
        required: true
        allowed_values: [work_role_selections, dsl_previews]
      - name: keyColumn
        type: string
        required: true
        allowed_values: [user_id, preview_slot]
        constraints: "table ごとに 1 つ。主キーの 1 列"
      - name: conflictConstraint
        type: string
        required: true
        constraints: >
          鍵の重なりを表す一意の制約の名前（H2 は pk_work_role_selections・dsl_previews の自動名を大文字で、PostgreSQL は小文字で返す）。
          違反の判定は SQLState 23505 かつ大文字にした文に表の名前を含むこと（RoleStoreClassifier と同じ照合の仕方）
      - name: updateSql
        type: string
        required: true
        constraints: "UPDATE ... WHERE <鍵> = ?。値は束縛変数だけで渡す（文字列を連結しない）"
      - name: insertSql
        type: string
        required: true
        constraints: "INSERT ... VALUES (...)。値は束縛変数だけで渡す。時刻の列は直接の値の代入のため CAST を付けない（捨ての試しで両方に通る）"
    constraints:
      - "鍵は主キーの 1 列だけ（複数列の鍵・条件つきの一意は扱わない）"
      - "この単位で使うのは 2 表だけ。後の Intent で同じ形の表を足すときは、この手順（BR2.1）を使い回す"

  - id: ENT-002
    name: UpsertOutcome
    description: >
      ENT-001 の書き込みが終わった結果の区分（列挙）。呼び出し側（repository）は、書いた行数 1 を返すだけで、区分を業務処理の層へ渡さない
      （repository の口は変えない。契約 C5）。単体テスト・結合テストが、どの道を通ったかを確かめるために持つ。
    attributes:
      - name: code
        type: enum
        required: true
        unique: true
        allowed_values: [UPDATED, INSERTED, UPDATED_AFTER_CONFLICT]
        constraints: >
          UPDATED: 更新が 1 行に当たった。INSERTED: 更新が 0 行で、挿入が通った。
          UPDATED_AFTER_CONFLICT: 更新が 0 行で、挿入が鍵の重なり（23505）になり、セーブポイントまで戻して更新をやり直したら 1 行に当たった
    constraints:
      - "鍵の重なりの後の更新が 0 行（先の側が消した等）になったときは、結果を作らず想定外の失敗として扱う（BR2.5）"

  - id: ENT-003
    name: SavedPreferences
    description: >
      本人の表示の設定の保存の結果（保存した後の利用者 ID と表示のタイムゾーンの 2 列）。今の UserRepository.SavedPreferences（インターフェース）の形を変えない。
      この単位は、結果を取り出す方法（H2 の FINAL TABLE → JDBC の生成キー）だけを変える。
    attributes:
      - name: userId
        type: long
        required: true
        constraints: "更新に当たった本人の利用者 ID"
      - name: displayTimeZone
        type: string
        required: false
        constraints: "保存した後の値（変えないときは今の値、空にしたときは null）。最大 64 文字（V14 の列と同じ）"
    constraints:
      - "本人がいなければ結果は空（0 行）"
```

## 人が読む要約

| ID | エンティティ | 持ち主の部品 | 何を表すか |
|---|---|---|---|
| ENT-001 | KeyedUpsert | UserPersistence・RolePersistence・DslManagePersistence（共通の手順を `common.persistence` に置く） | 「あれば更新、無ければ挿入」の書き込み 1 件。2 表（work_role_selections・dsl_previews） |
| ENT-002 | UpsertOutcome | 同上 | 書き込みの結果の区分（更新・挿入・鍵の重なりの後の更新） |
| ENT-003 | SavedPreferences | UserPersistence | 本人の表示の設定の保存の結果（今の形のまま） |

- 新しい表・列・移行は足さない。既存のエンティティの持ち主・形は変えない（U1 のコード生成で直した大きなバイナリの 2 列の写し方を除く）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`（UserPersistence・RolePersistence・DslManagePersistence）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md`（ADR-003・ADR-006）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C3・C5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR3.5・FR3.6・FR3.8・FR5.4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-design-questions.md`（Q1: A・Q2: A・Q3: A、捨ての試しの結果）
- `aidlc/spaces/default/memory/team.md`（Code Style・Testing Posture）・`aidlc/spaces/default/memory/project.md`（Code Style・Forbidden・Corrections）
- 読んだ知識の置き場: `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`・`checklist-dsl.md`（DSL の本文の列）・`checklist-timezone.md`（時刻の列）・`checklist-auth-audit.md`（利用者の設定の保存・秘密の漏えい）
- コードで確かめたこと: `UserRepository.savePreferences`・`RoleStore`・`RoleStoreClassifier`・`DslPreviewRepository`・`DslAppliedRevisionRepository`・`RowLockFailures`・`WorkRoleSelectionMergeIT`・`UserSearch` まわり（`SearchText`・`UserRepository` の検索の問い合わせ）
