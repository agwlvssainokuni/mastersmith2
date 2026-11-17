# 外の待ちの一覧 — internal-db-postgresql

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、チームの外にあって Bolt を待たせうるもの（外の API・データ・承認・他のチームの受け渡し）を書く。

この Intent は、ほぼチームの中で完結する。上流（make-you-chic-ui・java-mustache-processor）への依頼は無く、外の API も使わない。

| 外のもの | 持ち主 | 待つ Bolt | 待ちの長さ | 遅れたとき |
|---|---|---|---|---|
| Flyway の PostgreSQL の部品（Maven Central） | Flyway の開発元 | B1 | 取得だけ（待ちなし） | ライセンス・依存の木・OSV-Scanner で問題があれば、依頼者に諮って版を選ぶ |
| PostgreSQL 18 のコンテナのイメージ（対象DB と同じ版とダイジェスト） | 公式イメージの配布元 | B1・B3・B4・捨ての試し | 取得済み（対象DB のテストで使用中） | 新しい版に上げるときは対象DB と一緒に上げる（契約 C6） |
| コンテナの実行環境（colima） | 依頼者の PC | 捨ての試し・B1〜B3 の PostgreSQL の結合テスト | 起動だけ | 起動して流し直す（飛ばした状態で統合しない、`team.md`） |
| 依頼者の判断（C3 で単一の写し方が無いとき） | 依頼者 | B1（U1 の機能設計）・B2 | 判断まで | U1 の機能設計の承認の場で諮る |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/delivery-planning/bolt-plan.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`
- `aidlc/spaces/default/memory/team.md`（Way of Working・Deployment）
