# エンティティ — U5 navigation

出典の表記: FR・NFR・C は `requirements.md`、AC は `stories.md`、Q1〜Q6 と SP（この段で決める設計の要点）は `functional-design-questions.md` の答え（まとめの確認で承認）、C1・C3・C5・C9 は `contract-summary.md`、dsl-BR は `construction/dsl-v2/functional-design/rules.md`、role-BR は `construction/role/functional-design/rules.md`、TP は `team.md`、PM は `project.md`。

この単位は内部DB の表を持たない（読み取りだけで、保存するものが無い）。ここに書くのは、この単位が定義して画面（U7）と分け合う応答の形、業務処理の中の値の形、アイコンの許した名前の一覧のファイルだけである。Spring Security の判定・指標・ログの仕組みはエンティティにせず、`rules.md` の決まりとして書く（PM の Code Style の学び）。

```yaml
entities:
  # ---------- 応答の形（契約 C9 を Q1〜Q4 で直した形） ----------
  - id: ENT-001
    name: NavigationResponse
    description: 業務のメニューの API（GET /api/me/navigation）の応答。作業ロールの実効の主権限で絞った木と、空のときの理由
    attributes:
      - {name: items, type: "list<NavNode>", required: true, constraints: "DSL の順のまま。空を許す"}
      - {name: emptyReason, type: "EmptyReason or null", required: true, constraints: "items が空のときだけ値を持ち、空でなければ null（BR3.3、Q3 A）"}
    relationships:
      - {to: NavNode, cardinality: "0..n", direction: owns}
      - {to: EmptyReason, cardinality: "0..1", direction: references}

  - id: ENT-002
    name: NavNode
    description: 絞った後のメニューの項目1つ（N 階層、深さ 5 段まで）
    attributes:
      - {name: id, type: string, required: true, unique: "1つの応答の中で一意", constraints: "絞る前の DSL の木での位置の道。各段の 0 から数えた番号を . でつなぐ（例 2.0）。前置きは付けない（BR3.1、Q2 A）"}
      - {name: label, type: DisplayLabel, required: true, constraints: "DSL の値をそのまま（エスケープ・前後の空白の除去をしない。空の文字列も返す）（BR3.2）"}
      - {name: icon, type: NavIconName, required: true, constraints: "許した名前の一覧にある名前。DSL が null・空・一覧に無いなら list（BR4.2）"}
      - {name: table, type: "NavTableRef or null", required: true, constraints: "navigable が true のときだけ値を持つ（BR3.5）"}
      - {name: navigable, type: boolean, required: true, constraints: "テーブルを指し、そのテーブルの実効の主権限が READ・FULL のとき true（BR3.5）"}
      - {name: items, type: "list<NavNode>", required: true, constraints: "子。空を許すが、navigable が false の項目では空にならない（BR2.3）"}
    constraints:
      - "navigable が false なら items は 1 つ以上（子の無いまとまりを返さない、BR2.3）"
      - "navigable が true なら table は null でない"
    relationships:
      - {to: NavNode, cardinality: "0..n", direction: "owns（子）"}
      - {to: NavTableRef, cardinality: "0..1", direction: references}
      - {to: DisplayLabel, cardinality: "1..1", direction: owns}

  - id: ENT-003
    name: NavTableRef
    description: 項目が指すテーブルの組（DSL の TableRef を応答の名前にしたもの、契約 C9）
    attributes:
      - {name: schemaName, type: string, required: true, min_length: 1}
      - {name: tableName, type: string, required: true, min_length: 1}
    constraints:
      - "名前はエンコードせずに返す。道への組み立てとエンコードは画面（U7）が行う"

  - id: ENT-004
    name: DisplayLabel
    description: 表示名の組（Q1 A）。既存の DSL の API と同じ形で、画面が表示の言語で選ぶ
    attributes:
      - {name: ja, type: string, required: true, constraints: "空の文字列（未設定）を許す"}
      - {name: en, type: string, required: true, constraints: "空の文字列（未設定）を許す"}

  - id: ENT-005
    name: EmptyReason
    description: 業務のメニューが空の理由（Q3 A）
    allowed_values:
      - {value: NOT_CONFIGURED, meaning: "適用済みの DSL が無い、または DSL の menus が空"}
      - {value: NOTHING_VISIBLE, meaning: "DSL の menus はあるが、作業ロールが無い・すべて NONE などで絞った結果が空"}

  - id: ENT-006
    name: TableAccessResponse
    description: テーブルの画面の置き場の権限の問い合わせ（GET /api/me/table-access?schema=…&table=…）の 200 の応答（Q4 A）
    attributes:
      - {name: schemaName, type: string, required: true}
      - {name: tableName, type: string, required: true}
      - {name: displayName, type: DisplayLabel, required: true, constraints: "DSL のテーブルの表示名（label）"}
      - {name: main, type: string, required: true, allowed: [READ, FULL]}
    constraints:
      - "NONE・DSL に無い・DSL が無いときはこの形を返さず 403 ACCESS_DENIED（BR5.2）"

  - id: ENT-007
    name: TableAccessQuery
    description: 置き場の問い合わせの入力（問い合わせの引数）
    attributes:
      - {name: schema, type: string, required: true, min_length: 1, constraints: "長さの上限は置かない（DSL の名前に上限が無いため。BR5.1）"}
      - {name: table, type: string, required: true, min_length: 1, constraints: "同上"}

  # ---------- 業務処理の中の値の形 ----------
  - id: ENT-008
    name: TableAccessResult
    description: 置き場の問い合わせの業務処理の結果（sealed interface の record、TP の Code Style）
    variants:
      - {name: Visible, attributes: [schemaName, tableName, "displayName: DisplayName", "main: READ か FULL"]}
      - {name: NotVisible, attributes: [], constraints: "NONE・DSL に無い・DSL が無いを区別しない（理由を持たない）"}

  - id: ENT-009
    name: MenuFilterInput
    description: 絞る純粋な関数（MenuFilter）の入力（SP 4）
    attributes:
      - {name: menus, type: "list<DslMenuItem>", required: true, constraints: "適用中のモデルの menus（dsl-BR2.1〜BR2.3 で 5 段以内）"}
      - {name: tableMain, type: "operation(schemaName, tableName) -> MainPermission", required: true, constraints: "テーブルの階層の実効の主権限。本番は写しの main(schema, table, null)（C5）"}
      - {name: iconOf, type: "operation(string or null) -> NavIconName", required: true, constraints: "アイコンの照らし合わせ（BR4.2）"}

  - id: ENT-010
    name: MenuFilterOutput
    description: 絞る関数の出力
    attributes:
      - {name: items, type: "list<NavNode>", required: true}

  - id: ENT-011
    name: NavIconName
    description: 許したアイコンの名前（文字列）。値は AllowedNavIconList にある名前だけ
    attributes:
      - {name: value, type: string, required: true}
    constraints:
      - "既定は list（BR4.2）"

  # ---------- アイコンの一覧のファイル（Q5 B） ----------
  - id: ENT-012
    name: AllowedNavIconList
    description: アイコンの許した名前の一覧のテキストのファイル。サーバーはこれを読み、画面のテストがこれと app/registry の一覧の一致を確かめる
    attributes:
      - {name: location, type: path, required: true, default: "backend/src/main/resources/navigation/allowed-icons.txt"}
      - {name: names, type: "list<string>", required: true, constraints: "1行に1つ。# で始まる行（ライセンスヘッダー）と空行は読まない。重ならない。list を含む（BR4.1・BR4.3）"}
      - {name: initialContent, type: "list<string>", required: true, default: "menu・chevron-down・chevron-up・close・check・bell・user・search・edit・trash・download・settings・home・list・info・success・warning・danger（18 個、make-you-chic-ui の固定先 e82b651）"}
```

## 要約

- **応答の木**: `NavigationResponse`（`items`・`emptyReason`）が `NavNode` の木を持つ。項目は `id`（絞る前の DSL の木での位置の道）・`label`（`{ja, en}`）・`icon`（許した名前、既定 `list`）・`table`（`{schemaName, tableName}` か null）・`navigable`・`items` を持つ。契約 C9 の形に、Q1〜Q3 の答えで `id`・`emptyReason` を足し、表示名を組にした。
- **置き場の問い合わせ**: 入力は問い合わせの引数 `schema`・`table`（どちらも必須で空を許さない。長さの上限は置かない）。業務処理は `Visible` か `NotVisible` を返し、200 の応答は表示名の組と主権限（READ・FULL）。見られないときは理由を持たない。
- **絞る関数**: 入力は DSL の `menus` と「テーブルの主権限を返す操作」と「アイコンを照らす操作」で、出力は `NavNode` の木。純粋な関数として性質ベースのテストを当てる。
- **アイコンの一覧**: `backend/src/main/resources/navigation/allowed-icons.txt` に 18 個を1行ずつ置く（Q5 B）。
- 内部DB の表・監査の種類・指標は足さない。
