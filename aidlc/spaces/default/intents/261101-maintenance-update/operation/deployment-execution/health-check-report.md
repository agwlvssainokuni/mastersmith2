# 健全性の確認の報告（health-check-report）

Intent 261101-maintenance-update。時刻は UTC。手順は `operation/deployment-pipeline/deployment-strategy.md` の 2節（S1）。

## 1. 入れ替えの直後と配備の後の複写の直後

| 項目 | 入れ替えの直後（healthy 12:17:23） | 複写の後の起動し直し（healthy 13:43:21） |
|---|---|---|
| コンテナの状態 | healthy | healthy |
| イメージ | `sha256:ffd2b8380460…`（`mastersmith:local` と一致） | 同左（一致） |
| `/actuator/health` | 200 | 200（healthy の判定） |
| メモリ・CPU の上限 | 2 GiB・4 CPU | 同左 |
| restart 回数・OOMKilled | 0・false | 0・false |
| JRE（`java -version`） | Temurin `25.0.5+8-LTS` | 同じイメージ |
| ERROR | 0 件 | 0 件 |
| WARN | 前の版から出ているもの 2 件（Flyway の `Database` 1・`BeanPostProcessorChecker` 1） | 同じ 2 件 |
| JSON でない行 | 0 件 | — |
| Flyway | 「Successfully validated 13 migrations」「Schema up to date」。`Migrating schema` 0 件 | `Migrating schema` 0 件 |
| 救済（初期管理者） | 0 件 | 0 件 |
| 初期管理者のログ | キー `maskedEmail` あり・`email` なし。`.env` の初期管理者のメールアドレスの値のログの中の件数 0（値は表示していない） | — |

- WAR の SHA-256: 作り直した WAR は `fe3382c51f62d33e30281c80b0ed794ea495c0d05c8015d592dddc3ef8ff6c41` で、Build and Test の WAR と同じ。
- 見本の対象DB と Mailpit は作り直していない（Mailpit は Up 20 hours のまま）。
- 戻し先: `mastersmith:pre-maintenance-update`（`d8a467b60f6f…`、前の Intent で配備した版）。
- アプリが止まった時間: 12:16:24〜12:17:23 の約 1 分（複写と数えを含む）、13:43:11〜13:43:21 の約 10 秒（配備の後の複写）。
- スモークテストの間（12:17:40〜13:43:00）のアプリのログは INFO 3 件だけ（対象DB の接続プールの起動 2・メールの送信 1）。ERROR・WARN 0 件、5xx の記録は無い。

## 2. 資源の上限と性能の目標

- アプリの上限（メモリ 2g・CPU 4）は前の版と同じ。配備の前の k6 は、Build and Test で同じ WAR の試験用のイメージを配備と同じ上限で流したため省いた（`cd-config.md` 3.1。決まりとの差は無い）。
- 配備の後に負荷の下での値は取っていない。

## 3. 内部DB

- スキーマの変更なし（版 13）。配備の前の複写（`…-202611022116-pre-maintenance-update.tgz`、40,012 バイト）と配備の後の複写（`…-202611022243-after-maintenance-update.tgz`、41,109 バイト）を読み取りで開けた（どちらも権限 600、`~/.mastersmith-backup/`）。監査の全体は 310 → 314。

## 4. 判定

- 健全性の確認はすべて通過。配備の完了の条件（healthy とスモークテストがすべて通る）を満たした。

## Sources

- `deployment-log.md`・`smoke-test-results.md`
- `operation/deployment-pipeline/deployment-strategy.md`（S1）・`cd-config.md`（3節・3.1・4節）
- `docker inspect`・`docker logs`（jq で項目名・ロガー・件数だけ）・`/actuator/health`・`docker compose exec -T app java -version`・`shasum -a 256`

## Assumptions & Open Questions

- None.
