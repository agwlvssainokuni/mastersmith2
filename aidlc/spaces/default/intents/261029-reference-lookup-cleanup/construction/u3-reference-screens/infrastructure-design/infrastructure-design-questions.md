# Infrastructure Design の質問 — U3 u3-reference-screens（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。U3 は画面（frontend）の部品だけで、新しい設定・コンテナ・依存は足さず、基盤の論点は NFR 要件と NFR 設計で決まっています。

## 決まっていること（質問しません）

- 画面は既存の WAR に同梱して配る（`team.md` の Code Style）。配備先は開発者の PC 上のコンテナのまま。
- 新しい npm の依存は足さない。make-you-chic-ui の固定先も変えない（NFR 設計で今の部品の口で満たせると確かめた）。
- 初回の JavaScript の量は既存の確かめ（`check-bundle-size.mjs`）のまま。業務データの画面の chunk は manifest で測って記録する（NFR2.44）。
- 監視は画面の側には足さない。サーバー側の参照の API の量と 503 は、U1 の基盤の設計で足すパネルと既存の警報で見る。
- E2E は 190（代表の流れ）と 195（axe）を `./gradlew e2eTest` で流し、`verify` と CI の外に置く。U3 は frontend の本体に触れるため、統合の前に E2E を流す（`team.md`）。見本の対象DB（PostgreSQL）がコンテナで動いていることが前提。
