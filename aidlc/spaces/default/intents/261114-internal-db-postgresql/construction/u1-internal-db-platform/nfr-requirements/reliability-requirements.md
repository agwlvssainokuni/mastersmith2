# 信頼性の要件 — u1-internal-db-platform

上流の要件 NFR4（信頼性）と、起動の失敗・戻し・テストの確実さに関する決まりを `NFR4.x` に分けます。可用性の数値の目標（SLA・SLO）は、配備先が開発者の PC 上のコンテナだけのため、この Intent では置きません（`team.md` の Deployment）。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR4.1 | PostgreSQL のとき、上限なく待ち続ける経路を残さない。接続を作るたびに `lock_timeout` を 2 秒にし、行の排他の読み取りの待ちの上限のヒント（3000 ms）には手を入れない。H2 のときは接続のたびの文を足さない。 | 単体テストで、接続プールの初期化の文が PostgreSQL のときだけ `SET lock_timeout = '2s'` になること。書き込みの待ち・行の排他の待ち・行き詰まりの確かめは U3 | 要件 NFR4、FR3.3、BR3.1〜BR3.3 |
| NFR4.2 | 起動時の移行（V1〜V14 と、後の Intent で足す移行）は、`lock_timeout` 2 秒を設定した接続のままで、空の PostgreSQL のすべてで通る。 | PostgreSQL の結合テストで、本番と同じ接続の設定のまま空のデータベースへ移行が通り、エンティティとの照合（`ddl-auto: validate`）も通ること（BR2.10） | 要件 NFR4、BR2.10、BR3.4 |
| NFR4.3 | 外の接続が移行の表（Flyway の履歴の表）を握っているときの起動は、有限の時間で結果が決まる（成功するか、`MIGRATION_FAILED` で起動を止める）。無限に待たない。移行の接続だけ待ちの上限を外すかどうかは、NFR Design で決める（機能設計 BR3.4 の持ち越し）。 | NFR Design の決定と、その確かめ（コード生成の計画に置く）。どちらの形でも「待ち続けない」ことを確かめる | 要件 NFR4、BR3.4、機能設計の持ち越し |
| NFR4.4 | 起動時の失敗（空の接続先・対応しない接続先・接続できない・移行の失敗）は、有限の時間で起動を止め、プロセスは異常の終了になる。起動したまま内部DB が使えない状態を残さない。失敗の記録は NFR2.2 のとおり。 | 結合テスト（コンテナ無し）で、閉じたポートの接続先・空の接続先・対応しない接続先のそれぞれで起動が失敗すること（FR5.5） | 機能設計の質問 Q2: A、BR1.3、BR1.4、BR5.1 |
| NFR4.5 | 内部DB が PostgreSQL のときも、ヘルスチェックは、届けば UP・届かなければ DOWN を返す。確認の時間の上限は今のまま（制限時間付きの確認）。 | PostgreSQL の結合テストで、UP と、接続を落としたときの DOWN | BR3.6、要件 FR3.9 |
| NFR4.6 | 前の版で作った H2 の内部DB のファイルのまま、新しい版が起動する。H2 の移行（V1〜V14）は 1 文字も変えず、置き場も今のまま。 | 前の版で作った H2 のファイルでの起動の確かめ（FR5.5）と、H2 の移行のファイルが変わっていないことの確かめ（BR2.8） | 要件 FR1.1・FR2.2、BR2.1 |
| NFR4.7 | スキーマの変更は前進のみとし、H2 と PostgreSQL の移行は同じ版の番号で組にする。1 つ前の版のアプリが動く後方互換を保つ。H2 の既定は変えないため、H2 の配備の戻し方（直前の版のイメージ）はこの Intent の前と同じ。PostgreSQL の内部DB は、この Intent で初めて作るため、前の版には戻せない（前の版は PostgreSQL を知らない）。その場合の戻しは U4 の手順（`pg_dump` などの複写）で扱う。 | 移行の 2 つの置き場の突き合わせ（BR2.8）。戻しの手順の記述は U4 | `team.md` の Deployment、BR2.3、BR2.7 |
| NFR4.8 | PostgreSQL の結合テストは、コンテナの実行環境が無いとき、手元では警告を出して飛ばし（黙って飛ばさない）、CI で明示して流したときは飛ばさず失敗する。専用のタスクを明示したのに 0 件で通ることは無い。 | テストの部品（`InternalDbPostgresRuntimeCheck`）の単体テストと、`-PitTests` の形がタグ付きのクラスだけに当たったときの失敗の確かめ（BR6.3・BR6.4） | 要件 FR5.3、BR6.3、BR6.4 |
| NFR4.9 | PostgreSQL の結合テストは、実行の順に依存しない。コンテナはテストの JVM ごとに 1 つ起動して使い回し、空のデータベースが要るテストは専用のデータベースを作って、終わったら消す。名前には実行ごとに一意の値を入れる。 | 結合テストの部品の読み取りと、順を入れ替えた実行 | BR6.5、BR6.6、`team.md` の Testing Posture |
| NFR4.10 | 起動のログに、内部DB の種類（H2 または PostgreSQL）だけを INFO で 1 行出す。接続先の値・利用者名・URL は出さない。どちらの DB で起動したかを、運用で確かめられるようにするため。 | 単体テストで、1 行が出ることと、値が出ないこと | 新しい要件（運用の確かめ）。NFR2.2 と同じ漏れの守り |

## 起動の失敗と戻しの扱い

- 起動の失敗は、利用者の画面には現れない（アプリが起動しないため）。運用者がアプリのログの 1 行（ENT-005）で、原因の種類（failureKind）・SQLState・失敗した移行の版の番号を知り、環境変数の設定か、PostgreSQL の状態を直す。
- 空の PostgreSQL からの起動は、初期管理者の自動作成も含めて、H2 の初回の起動と同じ決まりで動く（BR2.11）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR4、FR1.1・FR2.2・FR3.3・FR3.9・FR5.3）[desc]
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR1.3・BR1.4・BR2.1・BR2.3・BR2.7・BR2.8・BR2.10・BR2.11・BR3.1〜BR3.4・BR3.6・BR5.1・BR6.3〜BR6.6）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（W1〜W5、BR3.4 の持ち越し）
- `aidlc/spaces/default/memory/team.md`（Deployment・Testing Posture）

## Assumptions & Open Questions

- [assumption] 起動の失敗でプロセスが異常終了するのは、Spring Boot の既定の挙動（`ApplicationContext` の初期化の失敗）に頼る。専用の終了コードは決めない。
- NFR4.3 の持ち越し: 外の接続が移行の表を握っているときの `lock_timeout` 2 秒の影響と、移行の接続だけ待ちの上限を外すかを、NFR Design で決める。
