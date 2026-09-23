# External Dependency Map — dsl-schema-loader

Bolt（1回分の作りの区切り。設計から実装・テストまでを通し、動くものができて終わる）を待たせうる、外からのものを示す。開発は依頼者1名と AI で閉じており、外のチームの受け渡し・承認の待ち・外部の API は無い。

| 外からのもの | 持ち主 | 待ちの長さ | 使う Bolt | 遅れたときの手当て |
|---|---|---|---|---|
| YAML・JSON Schema の部品（Maven Central） | 各部品の作者 | 無し（取得するだけ） | B1 | 選定の候補を複数用意する。ライセンス・脆弱性の関門（High 以上で止める）を通らなければ次の候補にする |
| JDBC ドライバー3種類（MySQL Connector/J・MariaDB Connector/J・PostgreSQL JDBC） | 各 DB の提供元 | 無し（取得するだけ） | B2・B3 | ライセンスの確認で採用できなければ、代わりのドライバーを依頼者と相談する |
| Testcontainers の部品と、コンテナのイメージ（MySQL・MariaDB・PostgreSQL・ryuk） | Testcontainers・各 DB の提供元（Docker Hub など） | イメージの初回の取得の時間 | B2・B3・B4 | イメージは版で固定する（NFR12）。取得できないときは、colima の中の既存のイメージで試す |
| colima（手元のコンテナの実行環境）と GitHub Actions のコンテナの実行環境 | 依頼者の PC・GitHub | 無し | B2・B3・B4 | 手元でコンテナが無いときは警告して対象DB のテストだけを飛ばすが、統合の前には必ず通す（team.md の Way of Working） |
| 依頼者のレビューと承認 | 依頼者 | 依頼者の都合 | すべて | 各段の承認の確認で止まって待つ |
| `origin` へのプッシュと CI の再確認 | 依頼者 | 依頼者の都合 | すべて | CI が失敗したら、次の Bolt に進む前に直す（team.md の Way of Working） |
