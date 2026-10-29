# エンティティ — U2 u2-reference-write-check

出典: 単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U2）・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work-story-map.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR3・NFR1・NFR3）、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`・`decisions.md`（ADR-003・ADR-005・ADR-006）、契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3・C4）、ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（US1.5・US3.1〜US3.3）、この段の答え `functional-design-questions.md`（Q1: A・Q2: A と「決まっていること」）。

**内部DB に保存するエンティティは無い。** この単位は内部DB に新しい表を持たず、移行ファイルも変えない（要件 NFR7、`components.md` の「エンティティの持ち主」）。参照先の行と参照元の行は対象DB のデータで、アプリが独自に持つデータではないため、エンティティにしない（`project.md` の学び）。監査の行は既存の `audit_events` の形のまま使い、列も値も足さない（契約 C4）。

ここに書くのは、作成・更新の要求1件の中だけで作って使い、保存しない値の型である（前の Intent の U2 `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/functional-design/` と同じ書き方）。既存の型（`TableAccessView`・`ColumnAccess`・`ColumnWriteRules`・`TypedValue`・`BoundValue`・`RowKey`・`WriteRejection`・`BusinessDataFieldError`・`BusinessDataFieldErrorReason`・`BusinessDataAuditReason`・`BusinessDataChangedEvent`）と、U1 が作る型と口（`ReferenceTarget`・`ReferenceStatus`・`ReferenceValue`・`ReferenceCheckResult`、確かめの口 `check` と件数の状態の口 `countState`。U1 の `entities.md`・functional-spec.md の 10.1 節）は変えずに使い、ここには載せない。使い方は `rules.md` に書く。型の名前は案で、新しいクラスを作ることはコード生成の計画に明記して依頼者の承認を得る（`team.md`）。

```yaml
entities:
  - name: ReferenceCheckItem
    description: "保存前に確かめる参照1つ（作成の参照のカラム、または更新で値が変わった参照のカラム）。参照先は DSL から引き、利用者の入力からは作らない"
    persisted: false
    attributes:
      - { name: column, type: string, required: true, constraints: "参照元のカラムの名前。写しの DSL にあり、検証（既存）を通ったカラムだけ" }
      - { name: target, type: ReferenceTarget, required: true, constraints: "U1 の targetOf で、要求1件の権限の写しの DSL から引く（BR2.1）" }
      - { name: value, type: ReferenceValue, required: true, constraints: "検証を通った型つきの値（TypedValue）から作る。NULL の値は作らない（BR2.3）" }
      - { name: origin, type: enum, required: true, allowed_values: [CREATE, CREATE_REQUIRED, UPDATE_CHANGED], constraints: "CREATE_REQUIRED は必須の参照項目（BR3.1）。UPDATE_CHANGED は更新前の値と違うもの（BR2.4）" }
    constraints:
      - "文字列にするときはカラムの名前と origin だけを出し、値を伏せる（BR7.1）"
      - "並びは DSL のカラムの並び（BR2.7）"
    relationships:
      - { to: ReferenceTarget, cardinality: "1", direction: "ReferenceCheckItem → ReferenceTarget（U1 の型）" }
      - { to: ReferenceValue, cardinality: "1", direction: "ReferenceCheckItem → ReferenceValue（U1 の型）" }

  - name: RequiredReferenceState
    description: "作成で、必須の参照項目（BR3.1）ごとに決める状態。値を入れられるか（入力不可の行き止まりか）を表す"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: status, type: ReferenceStatus, required: true, constraints: "U1 の ReferenceStatus。REFERENCE は U1 の countState（⑩ の読み取りの接続で 201 行目まで読む。NO_PERMISSION は問い合わせない）の結果、LOOKUP は accessOf だけで決める（BR3.2）。同じ参照先は要求1件で1回（BR4.7）。LOOKUP に TOO_MANY は無い" }
      - { name: bodyState, type: enum, required: true, allowed_values: [VALUE, EMPTY], constraints: "EMPTY は本文に無い・null、または \"\" で ⑨ の既存の検証が空として誤りにしたもの。⑨ を通った \"\"（文字の型で REQUIRED が無いカラム）は VALUE（BR2.3・BR3.1）。status によって変わらない" }
    constraints:
      - "status が NO_PERMISSION・TOO_MANY のとき: VALUE → REFERENCE_NOT_FOUND、EMPTY → NOT_EDITABLE_NOT_NULL（BR3.3・BR3.4）"
      - "status が AVAILABLE のとき: VALUE は通常の確かめ、EMPTY は保留した既存の検証の結果を戻す（保留した誤りが無い EMPTY は本文に無いときだけで、既存の流れのまま保存へ。BR3.5）"
    relationships: []

  - name: PreviousReferenceValues
    description: "更新で、更新前の行から読んだ参照のカラムの値（比べるためだけに持つ）"
    persisted: false
    attributes:
      - { name: values, type: "map<column, TypedValue|UNREADABLE>", required: true, constraints: "本文に入っている参照のカラムだけ。値は本文と同じ読み方で TypedValue にする。読めない・切られた値は UNREADABLE（BR2.5）" }
    constraints:
      - "Map のまま層をまたがない。repository から service へは値を伏せる型で渡す（team.md の Code Style、BR7.1）"
      - "比べ終えたら捨てる。監査・ログ・応答に出さない"
    relationships: []

  - name: ReferenceStepOutcome
    description: "保存前の参照の段（判定の順の ⑩）の結果。結果の型（sealed interface の record）で、業務処理の中だけで使う"
    persisted: false
    attributes:
      - { name: case, type: enum, required: true, allowed_values: [PROCEED, INVALID, REJECTED, ROW_MISSING, FAILED] }
      - { name: fieldErrors, type: "list<BusinessDataFieldError>", required: false, constraints: "INVALID（400 の REQUIRED など）と REJECTED（409 の REFERENCE_NOT_FOUND・NOT_EDITABLE_NOT_NULL）のとき。カラムごとに1件。値を持たない（BR1.3・BR4.4）" }
      - { name: rowMissing, type: enum, required: false, allowed_values: [ROW_NOT_FOUND, ROW_NOT_UNIQUE], constraints: "ROW_MISSING のとき（更新前の行の読み取り。BR2.6）" }
      - { name: failure, type: enum, required: false, allowed_values: [TIMEOUT, UNAVAILABLE, MISMATCH], constraints: "FAILED のとき。接続を借りられない失敗は UNAVAILABLE。ログに出す SQLState・業者の番号・例外のクラスの名前を添える（BR4.5・BR7.1）" }
    constraints:
      - "どの case も、参照先・参照元の行の値を持たない"
      - "PROCEED のときだけ書き込みの接続を借りる（BR4.1）"
    relationships:
      - { to: BusinessDataFieldError, cardinality: "0..n", direction: "ReferenceStepOutcome → BusinessDataFieldError（既存の型）" }
```

## 要約

- 保存する表は無い。足す型はどれも、作成・更新の要求1件の中だけで作って捨てる値である。
- `ReferenceCheckItem` は確かめる参照1つで、参照先は DSL から引き（利用者の入力からは作らない）、値は検証を通った型つきの値から作る。
- `RequiredReferenceState` は、作成で必須（NOT NULL）の参照項目に値を入れられるかの状態で、Q1: A の行き止まりの応答（`REFERENCE_NOT_FOUND` と `NOT_EDITABLE_NOT_NULL`）を決める。
- `PreviousReferenceValues` は、更新で「値が変わった参照だけ」を確かめるために読んだ更新前の値で、比べ終えたら捨てる。
- `ReferenceStepOutcome` は、保存前の参照の段の結果（進む・400・409・行が無い・対象DB の失敗）で、既存の `WriteRejection` と項目の誤りに写す。新しい code・項目の誤りの理由・監査の理由は足さない。
- 値を持つ型は、文字列にするときに値を伏せる（`TraceAspect` の TRACE の出力に出さない）。
