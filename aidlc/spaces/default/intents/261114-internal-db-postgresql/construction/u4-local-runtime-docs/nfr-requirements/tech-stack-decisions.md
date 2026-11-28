# 技術選定と品質の決まり — u4-local-runtime-docs

この単位で足す・選ぶ技術と、その理由を書きます。上流の要件 NFR1（性能）・NFR3（対応の範囲）・NFR4（信頼性）・NFR5（検査の時間）・NFR6（カバレッジ）・NFR7（インスタンス）・NFR8（依存）・NFR9（構造）を、ここで `NFRx.y` に分けます。この単位はアプリのコードを変えず、手元の配備の設定と文書だけを変えます。

## 新しく足すもの

| 部品 | 版 | 使う場所 |
|---|---|---|
| コンテナのイメージ `postgres` | 18.6（見本の対象DB と同じ版とダイジェスト `sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`） | compose の内部DB 用のサービス（profile で選んだときだけ起動） |

アプリの依存（Gradle・npm）は足しません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR1.1 | アプリの性能・資源の目標（応答時間・メモリなど）は変えない。既定の手元の配備（H2）の起動と資源は、この単位の変更の前と同じ（compose の既定のサービスの設定を変えない）。 | `docker compose config`（profile なし）の結果が、この単位の変更の前と、内部DB 用のサービスを除いて同じであること | 要件 NFR1 |
| NFR3.1 | 対応の範囲は PostgreSQL 18 系（イメージ `postgres:18.6` のダイジェスト）だけ。文書の「対応する版」に、確かめた 18 系だけを書き、それ以外の版・種類（MySQL・MariaDB など）を対応とは書かない。イメージの版とダイジェストは、見本の対象DB・テストの定数 `TargetDbImages` と同じ値にする。 | `compose.yaml` の内部DB 用のサービスのイメージと、見本の対象DB のイメージ・`TargetDbImages` の値が一致すること（`grep` で比べる） | 要件 NFR3・FR4.2 |
| NFR4.1 | 内部DB 用の PostgreSQL のコンテナが健全（`pg_isready`）になってからアプリが接続を試みる形にし、起動の順で接続に失敗して止まる経路を作らない（healthcheck と、アプリ側の起動の待ち。待ちの上限は設計で決める）。 | 手元で profile を選んで起動し、アプリが healthy になること（Deployment Execution） | 要件 NFR4 |
| NFR4.2 | PostgreSQL の内部DB のバックアップと戻し（`pg_dump`・`pg_restore`）の手順は、Deployment Execution で使い捨ての内部DB を相手に 1 回練習し、戻した後に件数（利用者・ロール・監査の種類ごと）が一致することを確かめる。H2 だけの運用の道具は、H2 のときだけのものと文書に明記する。 | Deployment Execution の記録 | 要件 FR4.4、`team.md` の Deployment |
| NFR5.1 | この単位は `./gradlew verify`・`verifyQuick`・CI の時間に加えるテストを足さない。文書の変更とリポジトリの設定の変更は、フォーマット・リンタ・Gitleaks の範囲で確かめる。 | `src/test` に差分が無いこと。Build and Test で CI の時間を基準と並べて記録する | 要件 NFR5 |
| NFR6.1 | アプリのコードを変えないため、カバレッジの計測の対象・下限は動かない。 | `backend/src/main` に差分が無いこと | 要件 NFR6 |
| NFR7.1 | アプリのインスタンスは 1 つの前提のまま。compose で内部DB 用の PostgreSQL を 1 つだけ置き、複製・レプリカは作らない。 | `compose.yaml` のサービスが 1 つで、`deploy.replicas` を使わないこと | 要件 NFR7 |
| NFR8.1 | 依存を足さない。コンテナのイメージは、Dependabot が見ない固定先（`compose.yaml`）と、テストの定数 `TargetDbImages` が持つ同じ版・ダイジェストに揃える。内部DB 用の固定先（compose のサービス）を `.github/dependabot.yml` の固定先の一覧（コメント）に足し、上げるときは一緒に上げると書く。ライセンス: `postgres` のイメージは PostgreSQL License（Apache License 2.0 とは別）。採用の理由（対象DB と同じ版のイメージで、新しい種類の依存ではない）を、`docs/internal-db.md` ではなく設計の記録（`tech-stack-decisions` と NFR 設計）に残す。 | `.github/dependabot.yml` の差分。`compose.yaml` と `TargetDbImages` の値の一致 | 要件 NFR8・FR4.2、`team.md` の「新しい依存を足すときはライセンスを確かめる」 |
| NFR9.1 | アプリの本体のソース（`src/main`）・既存の境界テスト・H2 のテストに手を入れない。`.env.example` は値を空のまま項目だけ足す。 | `git diff --stat` で `backend/` に差分が無いこと | 要件 NFR9、`unit-of-work.md` の U4 の境界 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR1・NFR3〜NFR9・FR4）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`（U4 の節）
- `aidlc/spaces/default/memory/team.md`（Deployment・静的解析とセキュリティ検査）・`project.md`
- `compose.yaml`・`.github/dependabot.yml`（読み取り）

## Assumptions & Open Questions

- [assumption] `postgres` のイメージのライセンスは PostgreSQL License（寛容なライセンス）として扱い、採用の理由を設計の記録に残す。U1 の `flyway-database-postgresql` のライセンス（Apache License 2.0 と仮定）は、Build and Test で依存の木とあわせて確かめる。
