# エンティティ — u2-display-time-zone

出典: 契約 C2・C3（`inception/contract-design/contract-summary.md`）、部品 UserPreferences・TimeZoneNamePolicy・DisplayTimeZoneContext・OtherScreenTimes（`inception/domain-design/components.md`）、ADR-001、質問と答え（`functional-design-questions.md` の Q1: B・Q2: C・Q3: A）。

U2 が内部DB に新しく保存するのは、利用者の設定の行に足す列1つだけ。フレームワークの仕組み（Spring の JSON の読み取り・Jackson の設定、React の状態）は、エンティティにせず決まりとして書いた。名前は論理の名前で、型名・クラス名は Code Generation で決める。今の氏名・言語・テーマ・文字の大きさ（`Preferences` の4つ）は変えない。

```yaml
entities:
  - name: UserPreference
    description: 利用者の設定の行に足す、表示のタイムゾーンの保存値。今の利用者の行（言語など）の1列の追加で、新しい表ではない。
    attributes:
      - { name: userId, type: identifier, required: true, unique: true, constraints: "既存の利用者の ID（行の主キー）。本人の行だけを読み書きする" }
      - { name: displayTimeZone, type: string, required: false, unique: false, constraints: "NULL 可・既定なし。NULL は「ブラウザに合わせる」。値は TimeZoneName の判定を通った IANA の地域の名前を、送られたまま。長さ 1〜64。内部DB の列は display_time_zone（VARCHAR(64)）" }
    relationships:
      - { entity: TimeZoneName, cardinality: "0..1（値があるとき、その値は TimeZoneName として有効）", direction: derived }

  - name: TimeZoneName
    description: タイムゾーンの名前として有効かを判定する値（保存しない）。サーバー側の純粋な判定の入力と結果。
    attributes:
      - { name: value, type: string, required: true, constraints: "1〜64 文字。ZoneId.getAvailableZoneIds() の要素と大文字小文字を区別して完全一致。前後の空白を除かない。固定のオフセット表記は集合に無いため無効" }
    relationships: []

  - name: DisplayTimeZoneUpdate
    description: 保存の要求の displayTimeZone の3つの状態（保存しない。要求の読み取りから保存までの受け渡しの値）。
    attributes:
      - { name: kind, type: enum, required: true, constraints: "keep（項目が無い。列を書かない）・clear（明示の null。列を NULL にする）・set（文字列。名前を照らして置く）" }
      - { name: name, type: string, required: false, constraints: "kind が set のとき。文字列でない型は、名前として通らない値（空の文字列と同じ）として set で運び、拒否に至る。入れた値は応答・ログに載せない" }
    relationships:
      - { entity: UserPreference, cardinality: "N:1（保存の結果として列が変わる、または変わらない）", direction: outbound }

  - name: DisplayTimeZoneState
    description: 画面の側で配布する、効いている表示のタイムゾーンの状態（保存しない。React の状態）。
    attributes:
      - { name: status, type: enum, required: true, constraints: "loading（読み込み中）・ready・error（読み込みの失敗。契約 C2 に足す状態、Q1: B）" }
      - { name: timeZone, type: string, required: false, constraints: "ready のときだけ。解釈できる IANA の名前（保存済み・ブラウザ・最後の手段は UTC）。loading・error では null" }
      - { name: source, type: enum, required: false, constraints: "ready のときだけ。saved・browser・saved-unresolvable" }
      - { name: savedName, type: string, required: false, constraints: "保存済みの名前（無ければ null）。source が saved-unresolvable のとき、案内に出す" }
    relationships:
      - { entity: UserPreference, cardinality: "1:1（ログイン中の本人の分を読み込んだ写し）", direction: inbound }

  - name: TimeZoneOption
    description: プリファレンスの選択肢の1つ（保存しない。画面で組み立てる）。
    attributes:
      - { name: value, type: string, required: true, constraints: "選択の値。ブラウザに合わせるは空の文字列（送るときは null）、ほかは IANA の名前" }
      - { name: label, type: string, required: true, constraints: "ブラウザに合わせるは文言、ほかは「名前 (±HH:mm)」。解釈できない保存済みの名前は名前だけ" }
    relationships:
      - { entity: DisplayTimeZoneState, cardinality: "N:1（選択が保存値を映す）", direction: derived }
```

## 要約

- **永続するのは1つだけ**: 利用者の設定の行の `displayTimeZone`（NULL 可の1列）。ほかの3つ（名前の判定・保存の3状態・画面の状態・選択肢）は保存しない値。
- **保存の3状態（DisplayTimeZoneUpdate）**: 項目が無いときだけ列を書かない。これが前の版の画面の `PUT` との互換の要。
- **画面の状態（DisplayTimeZoneState）**: ready のときは `timeZone` が必ず解釈できる名前。`error` は契約 C2 への足すだけの変更で、前の版の画面は無関係（画面の側だけの契約）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位・契約・部品: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`inception/contract-design/contract-summary.md`・`inception/domain-design/components.md`
- 決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-001）
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-design-questions.md`

## Assumptions & Open Questions

- [assumption] 列の名前 `display_time_zone`・型 `VARCHAR(64)`・移行の版 `V14` は、今の移行の最後が `V13` であることに基づく。別の移行が先に入れば番号を繰り上げる。
- [assumption] 文字列でない型を「名前として通らない値」で運ぶ形は論理の表現で、実装は要求の読み取りの中の専用の型で表す（Code Generation で決める）。
