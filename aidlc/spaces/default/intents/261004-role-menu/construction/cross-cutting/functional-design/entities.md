# エンティティ（Entities）— U1 cross-cutting

出典: `unit-of-work.md`（U1）、`contract-summary.md`（C1・C2）、`components.md`（AccessControl・SharedTreeView・AppFrame）、`requirements.md`（NFR1.3・NFR4.1〜NFR4.3・C5）、この段の答え `functional-design-questions.md`（Q1: B、Q2〜Q6: A、まとめの確認 Looks correct）。

この単位は内部DB の表を持たない。ここに書くのは、この単位が定義して他の単位と分け合う「データの形」（Java の注釈の値、画面の登録の型の項目、共有の木の節の形）だけである。Spring Security の判定・ArchUnit・ESLint などフレームワークと道具の仕組みはエンティティにせず、`rules.md` の決まりとして書く（`project.md` の Code Style の学び）。

```yaml
entities:
  - id: ENT-001
    name: ApiAccessLevel
    description: API の分類の値。コントローラーの口がどの範囲の利用者に開かれているかを表す（契約 C1、ADR-008）
    kind: enumeration
    location: backend common.security
    values:
      - name: PUBLIC
        meaning: ログインしなくても届く（例 ログイン・登録の完了・見た目の設定・問題の種類の説明・/error）
      - name: AUTHENTICATED
        meaning: ログインだけが要る（/api/** の既定。例 /api/me/**）
      - name: ADMIN
        meaning: 管理者の印が要る（/api/admin の下）
    constraints:
      - 値は3つだけ。値を足すのは互換の変更として後の Intent で扱い、名前と意味は変えない

  - id: ENT-002
    name: ApiAccess
    description: コントローラーの口に付ける分類の印（Java の注釈、実行時に読める）
    kind: annotation
    location: backend common.security
    attributes:
      - name: value
        type: ApiAccessLevel
        required: true
        description: 分類の値を1つ
    targets: [型（コントローラーのクラス）, 方法（RequestMapping を持つ方法）]
    constraints:
      - 1つの口に効く印は1つだけ。型と方法の両方には付けない（BR1.1）
      - common.security に置き、どの機能のパッケージにも依存しない（BR1.8）
    relationships:
      - target: ApiAccessLevel
        cardinality: many-to-one
        direction: ApiAccess -> ApiAccessLevel

  - id: ENT-003
    name: SidebarSection
    description: 登録するサイドバーの項目の区画（見出しの区分）。文字列リテラルの union
    kind: enumeration
    location: frontend app/registry/types.ts
    values:
      - name: ADMIN
        meaning: 管理のメニューの区画（見出しの文言と並びは骨組み U7 が持つ）
    constraints:
      - 今は ADMIN の1つだけ。業務のメニューは登録ではなく U5 の API から作るため、ここに足さない（契約 C2）

  - id: ENT-004
    name: SidebarItemRegistration
    description: 差し込み口2「サイドバーの項目の登録」。既存の型に section と icon を足す（Q6 A）
    kind: value-type
    location: frontend app/registry/types.ts
    attributes:
      - {name: id, type: string, required: true, unique: 全機能を通じて, description: 既存}
      - {name: labelKey, type: string, required: true, description: 既存。表示言語ごとの文言の鍵}
      - {name: path, type: string, required: true, description: 既存。登録済みの画面の URL}
      - {name: order, type: number, required: true, description: 既存。区画の中の並び}
      - {name: visibleWhen, type: "VisibleWhen（LOGGED_IN・ADMIN）", required: true, description: 既存}
      - {name: section, type: SidebarSection, required: true, description: 足す。どの区画に出すか}
      - {name: icon, type: "IconName（make-you-chic-ui）", required: false, description: 足す。無ければ骨組みが既定のアイコンを当てる}
    constraints:
      - section が ADMIN の項目は visibleWhen が ADMIN（BR5.3）
      - icon は make-you-chic-ui の許したアイコンの名前だけ（BR5.4）
    relationships:
      - target: SidebarSection
        cardinality: many-to-one
        direction: SidebarItemRegistration -> SidebarSection

  - id: ENT-005
    name: LoginStateProvider
    description: 差し込み口4「ログイン状態の提供元の登録」。既存の型に任意の logout を足す（Q4 A）
    kind: value-type
    location: frontend app/registry/types.ts
    attributes:
      - {name: getLoginState, type: "() => LoginState または Promise", required: true, description: 既存}
      - {name: subscribe, type: "(listener) => 受け取りをやめる関数", required: false, description: 既存}
      - {name: logout, type: "() => Promise<void>", required: false, description: 足す。ログアウトの手続き。auth が渡す}
    constraints:
      - 全機能を通じて最大1つ（既存の決まり）

  - id: ENT-006
    name: SharedTreeNode
    description: 共有の木 shared/tree の節（契約 C2 の nodes の要素）
    kind: value-type
    location: frontend shared/tree
    attributes:
      - {name: id, type: string, required: true, unique: 1つの木の中で読み込んだすべての節を通じて}
      - {name: label, type: string, required: true, description: 表示名。文字として出す（信頼できない入力として扱う）}
      - {name: hasChildren, type: boolean, required: true, description: 開閉のボタンを出すか。子は開いたときに読む}
      - {name: badges, type: "SharedTreeBadge の配列", required: false, default: 空}
    relationships:
      - target: SharedTreeNode
        cardinality: one-to-many
        direction: 親 -> 子（loadChildren で読んだもの）
      - target: SharedTreeBadge
        cardinality: one-to-many
        direction: SharedTreeNode -> SharedTreeBadge

  - id: ENT-007
    name: SharedTreeBadge
    description: 節の横に添える短い印（例 「⚠ 今の DSL に無い」「メニューに出ない」）
    kind: value-type
    location: frontend shared/tree
    attributes:
      - {name: id, type: string, required: true, unique: 1つの節の中で}
      - {name: text, type: string, required: true, description: 文字で意味を示す（色だけに頼らない）}
      - {name: tone, type: "neutral・warning", required: false, default: neutral}

  - id: ENT-008
    name: SharedTreeLabels
    description: 共有の木の文言。呼ぶ側が表示言語に合わせて渡す（C2 に足す互換の変更）
    kind: value-type
    location: frontend shared/tree
    attributes:
      - {name: expand, type: "(label) => string", required: true, description: 例「販売DB を開く」}
      - {name: collapse, type: "(label) => string", required: true, description: 例「販売DB を閉じる」}
      - {name: loading, type: string, required: true}
      - {name: loadFailed, type: string, required: true}
      - {name: retry, type: string, required: true}
      - {name: empty, type: string, required: true, description: 子が0件のとき}

  - id: ENT-009
    name: SharedTreeChildState
    description: 共有の木の中で節ごとに持つ子の読み込みの状態（木の中だけの状態で、外へ出さない）
    kind: state
    location: frontend shared/tree
    attributes:
      - {name: nodeId, type: string, required: true}
      - {name: status, type: "notLoaded・loading・loaded・failed", required: true, default: notLoaded}
      - {name: children, type: "SharedTreeNode の配列", required: false, description: loaded のときだけ}
    relationships:
      - target: SharedTreeNode
        cardinality: one-to-one
        direction: SharedTreeChildState -> SharedTreeNode
```

## 要約

- バックエンドは、分類の値 `ApiAccessLevel`（PUBLIC・AUTHENTICATED・ADMIN）と、それを1つだけ持つ注釈 `ApiAccess` の2つ。どちらも `common.security` に置き、表を持たない。
- 画面の登録の型は、`SidebarItemRegistration` に `section`（必須、今は `ADMIN` だけ）と `icon`（任意）を足し、`LoginStateProvider` に任意の `logout` を足す。
- 共有の木は、節 `SharedTreeNode`（id・label・hasChildren・badges）、印 `SharedTreeBadge`、文言 `SharedTreeLabels`、木の中だけで持つ子の読み込みの状態 `SharedTreeChildState` の4つ。
