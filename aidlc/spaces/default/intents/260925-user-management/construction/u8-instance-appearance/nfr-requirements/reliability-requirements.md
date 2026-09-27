# Reliability Requirements — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の障害のときのふるまいと、依存を持たないことの要件です。要件定義の NFR5（接続の使い方）をこの単位に当て、要件定義に信頼性の NFR が無い FR8.2 由来の要件（起動を止めない・必ず答える）は、確かめる仕組みの NFR9（テスト）の枝番に寄せます。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」はこの段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号、U4 W2 は `construction/u4-display-foundation/functional-design/functional-spec.md` の W2。枝番はこの単位の中で振る。

## 前提

- 画面（U4）は、この API の答えが出るまで最初の画面を描かず、待ちに上限を置かない（U4 W2、U4 の Q2 A）。この API が返らないと、すべての画面の最初の描画が止まる。失敗（200 以外・通信の失敗）なら、画面は前に当てた値か既定のまま描く。そのため U8 には「速く・必ず答える」ことが要る。
- 既存の内部DB は、接続プールの上限（既定 30）に達すると接続を借りる待ちが起き、JMX による詰め直しの一時停止のあいだは内部DB を使う要求が再開まで待たされる（`backend/src/main/resources/application.yaml` の説明）。
- 配備先は開発者の PC 上のコンテナで、手元の監視は常には動かさない（`aidlc/spaces/default/memory/team.md`・`project.md` の Deployment）。

## 1. 要件

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR5.1 | `GET /api/appearance` の処理（画面入出力と業務処理）は内部DB の接続を借りない。パッケージ `cherry.mastersmith.appearance` は、DB アクセス（`repository` の層、`javax.sql.DataSource`、JPA・JDBC の型）に依存しない | 構造の検査（ArchUnit の決まりを `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` か U8 の境界テストに足す） | NFR5、要点 2 |
| NFR5.2 | トークンを付けない `GET /api/appearance` は、認証・認可の段階を含めて内部DB に触れない。そのため、接続プールが尽きたときや詰め直しの一時停止のあいだも、応答が内部DB の待ちで遅れない | 結合テスト（トークンを付けない GET の前後で、内部DB の接続を借りた回数（HikariCP の指標 `hikaricp.connections.acquire` の件数など）が増えない）。測り方の具体はコード生成で決める | NFR5、要点 2、U4 W2 |
| NFR9.1 | 設定が無い・空・許されない値のどの場合も、アプリの起動を止めない。設定の受け取りで起動が失敗しない（値を列挙の型に結び付けない） | 結合テスト（ブランドカラーに `red`、フォントファミリーに `mono` を設定して起動し、GET が 200 で `blue`・`sans` を返す。設定なしでも同じ） | FR8.2、BR1.3・BR1.4・BR1.7、要点 3 |
| NFR9.2 | `GET /api/appearance` は常に 200 と許される値を返し、想定内の業務のエラーを返さない（エラーの `code` の一覧を作らない）。想定外の失敗は既存の共通のエラー応答（500 / `INTERNAL_ERROR`、内部の例外のメッセージを載せない）に任せる | 単体テスト（判定のどの結果でも、返す2つの値が許される値のどれか）と、`tech-stack-decisions.md` の NFR9.8 の性質ベースのテスト | BR3.1、要点 3 |
| NFR9.3 | 可用性の目標はアプリ全体と同じとし、U8 だけの SLO は置かない。手元の監視を常に動かしていない間は、SLO の判定を `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）に引き継ぐ。目標を緩めて満たしたことにはしない | 確かめるテストは無い（判定の扱いの記録）。observability-setup の段で既存のダッシュボードに `uri="/api/appearance"` の値が出ることを確かめる | 要点 3、`project.md` の Deployment |

## 2. 失敗のときのふるまい

| 場合 | ふるまい | 要件 |
|---|---|---|
| 設定が無い・空・空白だけ | 既定（blue・sans）で起動し、警告は出さない | NFR9.1、BR1.3 |
| 設定が許されない値 | 既定で起動し、項目ごとに警告のログを1件出す | NFR9.1、BR1.4、`observability-requirements.md` の NFR9.4 |
| 内部DB の接続プールが尽きた・詰め直しの一時停止中 | 影響を受けずに答える（トークンを付けない要求） | NFR5.1、NFR5.2 |
| 使えないトークンを付けた要求 | 既存の扱いで 401（画面はトークンを付けない） | `security-requirements.md` の NFR4.4 |
| 想定外の失敗 | 既存の共通のエラー応答で 500。画面は前に当てた値か既定のまま描く（U4 W3） | NFR9.2 |

## 3. 戻しとデータ

- U8 は内部DB の表・移行を持たず、保存するデータも無い。戻しは既存の決まり（直前の版の成果物での再配備）のままで、U8 のための手順は足さない。直前の版のイメージには `/api/appearance` が無く、画面は失敗として既定のまま描く（U4 W3）。
- 設定の変更の戻しは、`.env` の2項目を元に戻してアプリを起動し直す。

## 4. 上流との差

承認済みの文書と食い違う記述は無い。要件に信頼性の NFR が無いため、FR8.2 由来の起動と応答の要件を NFR9（テスト）の枝番に寄せたこと、NFR5 の「接続を持たない」を招待メールの送信から U8 の応答にも当てたことだけを記録する（`traceability.json` の NFR5・NFR9 の対象に含める）。
