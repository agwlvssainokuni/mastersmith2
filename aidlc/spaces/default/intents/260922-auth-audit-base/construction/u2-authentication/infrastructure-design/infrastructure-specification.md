# Infrastructure Specification — U2 認証（u2-authentication）

U2 の基盤の設計。入力は U2 の NFR Design（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`）、Domain Design の部品の一覧（`components.md`。Authentication と UserAccount）、U2 の `functional-spec.md` である。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。設計の方針の確定回答は `infrastructure-design-questions.md`（Q1・Q2）にある。

U2 は U1 の基盤（U1 の `infrastructure-design/infrastructure-specification.md`: コンテナ1台、`docker compose`、Temurin JRE 25、`/app/data` のボリューム、`.env` から環境変数）の上で動き、独自のコンテナ・サービスを持たない。本書は、U2 のために U1 の基盤に足す・変える点だけを書く。

## 1. 配備（Deployment）

| Facet | Choice | Rationale |
|---|---|---|
| 実行の形 | U1 と同じコンテナ・同じプロセス | ADR-008、U2 の `logical-components.md` |
| 資源の大きさ（U1 の値の上書き） | コンテナの CPU の上限を 4 にする（U1 の設計の「CPU 2」を上書き）。メモリは U1 のとおり 1GB | 同時 10 件のログインの bcrypt（cost 12）の照合を1秒以内に収めるため（Q2、U2 の NFR1.1・NFR1.3、U2 の `performance-design.md` 1章） |
| タイムゾーン（U1 の基盤に足す） | コンテナのタイムゾーンを `Asia/Tokyo` にする。compose の環境変数 `TZ` と、JVM のタイムゾーンの指定（`-Duser.timezone=Asia/Tokyo`）の両方で揃える | 使い終わったトークンの削除の時刻（日本時間 3 時 30 分）とログの時刻の表記を利用者の感覚に合わせる（Q1） |
| 時刻の保存 | 有効期限・ロックの解除・監査ログの日時は、時点（UTC）として保存し、タイムゾーンで値を変えない。現在時刻は注入できる時計から得る | タイムゾーンを変えても判定が変わらないように（U2 の NFR9.4） |
| 定期実行 | Spring の定期実行で1日1回、日本時間の 3 時 30 分（設定で変更可）。コンテナの外の定期実行の仕組みは使わない | U2 の `scalability-design.md` 2章 |
| ネットワーク | 追加なし。U2 の API は U1 と同じ 8080 で受ける。外への通信は無い | U1 の基盤のとおり |
| 保存 | 追加なし。利用者・ロックの状態・リフレッシュトークンの表は、U1 の内部DB（`/app/data`）に置く | Domain Design のデータの持ち主 |

## 2. 基盤のサービス（Infrastructure Services）

| Service | Role | Configuration | Notes |
|---|---|---|---|
| H2（U1 の内部DB） | database | U2 の表（利用者、ロックの状態、リフレッシュトークン、ダミーの記録）と索引を Flyway の U2 のファイルで作る。行の排他の待ちの上限 3 秒 | U2 の `scalability-design.md` 1章・`reliability-design.md` 1章 |
| そのほか | — | 使わない | キャッシュ・待ち行列・外部の認証の基盤は持たない |

## 3. 設定と秘密情報

| 種類 | 渡し方 | 内容 |
|---|---|---|
| 署名鍵（秘密） | `.env` から環境変数 | 32 バイト以上の乱数を Base64 にした文字列。無い・短いと起動しない（U2 の `security-design.md` 4章） |
| 初期管理者（秘密） | `.env` から環境変数 | メールアドレスとパスワード。無い・不正なら作らずに警告して起動を続ける |
| そのほかの設定 | 環境変数、または `application.yaml` の既定値 | ロックのしきい値・時間、トークンの有効期限、cost、削除までの日数と時刻 |
| 見本 | `.env.example` | 名前だけを置き、値は空。署名鍵の作り方（乱数 32 バイトを Base64 にする手順、例: `openssl rand -base64 32`）をコメントで書く |

- CI は署名鍵を使わない。テストは、テストの中で作った仮の鍵を使う（リポジトリに鍵を置かない）。
- 鍵の交換は、`.env` の値を替えてコンテナを作り直す（U2 の NFR3.4。発行済みのアクセストークンは 401 になり、画面は更新で取り直す）。

## 4. 単位で共有する基盤（Shared Infrastructure）

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| コンテナの CPU の上限（4） | U1（コンテナ） | U2（照合）、ほか全単位 | U2 の要件のために U1 の値を上書きした。U1 の `infrastructure-specification.md` の「CPU 2」より本書を優先する（Code Generation の計画で compose に反映する） |
| コンテナのタイムゾーン（`Asia/Tokyo`） | U1（コンテナ） | U2（定期実行）、U1（ログの時刻）、ほか全単位 | U1 の基盤に本書で足した設定。U1 の `compose.yaml` に書く |
| 内部DB の U2 の表 | U2 | U2 だけ（U3 は U2 の部品を通して利用者を得る） | ほかの単位は U2 の表を直接読み書きしない |
