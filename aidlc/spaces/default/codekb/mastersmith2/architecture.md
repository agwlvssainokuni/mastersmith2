# アーキテクチャ（mastersmith2）

## Architecture Analysis

### System Overview

1つのプロセスの Web アプリケーションである。バックエンド（Java 25・Spring Boot 4.1.1）が REST API と、ビルド済みの画面（React の SPA）の配信の両方を受け持つ。成果物は画面のビルド結果を同梱した実行可能 WAR 1つで、コンテナ1つ（`Dockerfile`・`compose.yaml` の `app`）で動かす。JVM は `-XX:MaxRAMPercentage=75.0` で起動し、コンテナのメモリの上限（既定 2g）の 75%（約 1.5GiB）が最大ヒープになる。

データの置き場は2種類ある。

- 内部DB: 組み込みの H2 2.4.240（ファイル保存、コンテナでは `/app/data`、接続先 `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE`）。利用者・トークン・ロックの状態・監査ログ・DSL のプレビューと適用の履歴を置く。スキーマは Flyway（V1〜V6）が正本。接続プールは HikariCP（上限 既定 30）。
- 対象DB: MySQL・MariaDB・PostgreSQL のどれか1つ。スキーマを読むだけ。

### Architectural Style

**モジュール分けしたモノリス（層構造）**である。パッケージが機能ごと（`auth`・`access`・`audit`・`user`・`targetdb`・`dsl`・`dslmanage`）と共通（`common`・`config`）に分かれ、各機能の中が `web`・`service`・`domain`・`repository` などの層になる（`code-structure.md`）。境界は ArchUnit のテストで確かめている。機能の間は差し込み口（Bean の一覧）とアプリの中の出来事でつながり、`audit` は出来事の型にだけ依存する。

### Component Relationships

```mermaid
flowchart LR
  subgraph FE["画面 frontend/src"]
    FDSL["frontend-feature-dsl"]
    FOTH["ほかの画面の部品"]
    APIC["frontend-api-client"]
  end
  subgraph BE["バックエンド cherry.mastersmith"]
    CW["common-web"]
    CFG["config"]
    AUTH["auth"]
    ACCESS["access"]
    USER["user"]
    AUDIT["audit"]
    TDB["targetdb"]
    DSL["dsl"]
    DSLM["dslmanage"]
  end
  H2[("内部DB H2")]
  TGT[("対象DB")]

  FDSL --> APIC
  FOTH --> APIC
  APIC -- "HTTP /api/**" --> CW
  CW --> CFG
  CFG --> AUTH
  CFG --> ACCESS
  CFG --> DSLM
  AUTH --> USER
  ACCESS --> AUTH
  DSLM --> DSL
  DSLM --> TDB
  DSLM --> USER
  AUTH -- "出来事" --> AUDIT
  ACCESS -- "出来事" --> AUDIT
  DSLM -- "出来事" --> AUDIT
  AUTH --> H2
  USER --> H2
  AUDIT --> H2
  DSLM --> H2
  TDB --> TGT
```

文章による代替: 画面の各機能は共通の API の呼び出し（`frontend-api-client`）で同じオリジンの `/api/**` を呼ぶ。要求はまず `common-web` のフィルター（本文の大きさの上限など）と `config` のセキュリティの連鎖を通り、各機能のコントローラーに届く。`auth` は `user` で利用者を照合し、`access` は `auth` の主体を使う。`dslmanage` は `dsl`（読み込み・検証・適用中のモデル）と `targetdb`（対象DB のスキーマの読み取り）を使い、プレビューと履歴を内部DB に置く。`auth`・`access`・`dslmanage` は出来事を知らせ、`audit` が確定の後に内部DB に追記する。パッケージ間の依存の詳細は `dependencies.md`。

### Data Flow

1. 画面の要求は `Authorization: Bearer` を付けて送られ、サーブレットのフィルター、Spring Security の連鎖を通ってコントローラー（`web`）に届く。
2. トランザクションは `service` の層で始まり、`repository` の層が内部DB を読み書きする。DSL の管理では、対象DB を読むあいだ内部DB の接続を持ち続けないよう、トランザクションの境界を `DslRecordStore` に置く。
3. 業務エラーは `@RestControllerAdvice` の1か所で Problem Details（`code`・`traceId` 付き）に変わる。
4. 監査の対象の出来事は、確定の後に `audit` が別のトランザクション（2本目の接続）で追記する。

### Key Design Decisions

| 選択 | 内容 | 影響（今回の4件との関わり） |
|---|---|---|
| DSL の本文をバイト列のまま BLOB に置く | `dsl_previews.yaml_bytes`・`dsl_applied_revisions.yaml_bytes`（`V5`） | 識別とダウンロードが一致する代わりに、投入と適用で本文を2回書く（TD-1） |
| 適用はプレビューの行を `INSERT ... SELECT` で履歴へ写す | 10MB の本文をアプリに読み直さない（`DslAppliedRevisionRepository` の `COPY_SQL`） | DB の中で本文がもう1つ増える。H2 の SQL に依存する（TD-1） |
| 内部DB の詰め直しは閉じるときだけ | `DEFRAG_ALWAYS=TRUE`（`application.yaml` 137 行） | 動いている間はファイルが縮まない（TD-1、README の既知の制約） |
| 読み込みは節の木と位置の対応表を作る | `SafeYamlParser`（SnakeYAML の `Composer`）→ Jackson の木と `PositionMap` | 誤りの行と列を示せ、上限を検査できる代わりに、1回の投入で本文の数倍の形が同時に生きる（TD-2） |
| モデルを2つ持ち続ける | 適用中（`ActiveDslModelStore`）とプレビュー（`DslPreviewCache`）、どちらも `AtomicReference` の1件 | 10MB の DSL なら両方が大きい（TD-2） |
| 重い操作は同時に1つ | `DslHeavyOperationGate` | 投入の読み込みのメモリは同時に1つ分に抑えられるが、ログインなどほかの要求とは重なる（TD-2） |
| 結合テストは1つの JVM で文脈をキャッシュ | `integrationTest` は最大ヒープ 1g、`RANDOM_PORT` の文脈がそれぞれ Tomcat と Hikari を持つ | 一時的な失敗の候補の1つ（TD-3） |

### Improvement Opportunities

- 本文を1か所に置き、プレビューと履歴は識別で指す形にすれば、DB の中の複写が減る（未検証。後方互換の制約は `code-quality-assessment.md` の TD-1）。
- 読み込みの途中の形（文字列・節の木・Jackson の木・位置の対応表）を同時に持つ時間を短くする余地がある。ただし上限の検査と誤りの位置を保つ必要がある。
- `DslLifecycle`（462 行）は DSL の管理の操作をすべて持つ。

詳しくは `code-quality-assessment.md` を参照。

## Interaction Diagrams

### 1. DSL の投入と適用で本文が書かれる道（TD-1・TD-2）

```mermaid
sequenceDiagram
  autonumber
  participant P as DslAdminPage
  participant F as RequestSizeLimitFilter
  participant C as DslAdminController
  participant G as DslHeavyOperationGate
  participant LC as DslLifecycle
  participant RD as DefaultDslReader
  participant PC as DslPreviewCache
  participant ST as DslRecordStore
  participant AM as ActiveDslModelStore
  participant DB as 内部DB H2

  P->>F: POST /api/admin/dsl/preview（10MB まで）
  F->>C: byte[] の本文
  C->>G: 同時に1つの枠を取る
  C->>LC: submit(bytes)
  LC->>RD: read（文字列・節の木・Jackson の木・PositionMap）
  RD-->>LC: DslModel
  LC->>ST: placePreview
  ST->>DB: MERGE で dsl_previews に本文を書く（1回目）
  LC->>PC: put(previewId, model)
  C-->>P: プレビュー（違い・警告）
  P->>C: POST /api/admin/dsl/apply
  C->>LC: apply(previewId)
  LC->>ST: apply（1つのトランザクション）
  ST->>DB: INSERT ... SELECT で履歴へ本文を写す（2回目）
  ST->>DB: プレビューの行を消す
  ST->>DB: 21 件目以降の古い履歴を消す
  Note over DB: 消した本文の場所は、動いている間は再利用されない（README の既知の制約）
  LC->>AM: 確定の後にモデルを差し替える
  C-->>P: 適用後の状態
```

文章による代替: 投入の本文は大きさの上限のフィルターを通ってコントローラーに `byte[]` で届き、重い操作の枠を取ってから `DslLifecycle` が読み込みと検証を行う。通れば `MERGE` でプレビューの行に本文を書き（1回目）、モデルをプレビューの置き場に持つ。適用では、1つのトランザクションでプレビューの本文を `INSERT ... SELECT` で履歴へ写し（2回目）、プレビューの行と古い履歴を消す。確定の後に適用中のモデルを差し替える。消した本文の場所が動いている間に再利用されないため、内部DB のファイルが伸び続ける（TD-1）。読み込みの途中の形は TD-2。

### 2. 結合テストの HTTP の道（TD-3）

```mermaid
flowchart LR
  JVM["integrationTest の JVM 1つ<br/>最大ヒープ 1g"] --> CTX["キャッシュされた Spring の文脈<br/>RANDOM_PORT ごとに Tomcat と Hikari"]
  IT["AccessTokenApiIT"] --> API["AuthApi<br/>BeforeEach ごとに新しい"]
  API --> HC["HttpTestClient<br/>JDK HttpClient 既定の版<br/>接続 5 秒・要求 30 秒"]
  HC -- "http://localhost:番号" --> TOM["Tomcat 全アドレスで待ち受け"]
  CTX --> TOM
  TC["Testcontainers のコンテナ<br/>colima が番号を転送"] -. "同じ loopback の番号の空間" .-> TOM
```

文章による代替: 結合テストは1つの JVM（最大ヒープ 1g）で動き、Spring の文脈がキャッシュされて `RANDOM_PORT` ごとに組み込みの Tomcat と Hikari のプールを持ち続ける。`AccessTokenApiIT` は毎回新しい `AuthApi`（新しい JDK の `HttpClient`）で `http://localhost:<番号>` に送る。Tomcat は全アドレスで待ち受け、Testcontainers のコンテナの番号は colima が PC の loopback へ転送するため、番号の空間を共有する。失敗の原因の候補（負荷・番号の衝突・資源の使い過ぎ）はどれも未検証で、`code-quality-assessment.md` の TD-3 に書いた。
