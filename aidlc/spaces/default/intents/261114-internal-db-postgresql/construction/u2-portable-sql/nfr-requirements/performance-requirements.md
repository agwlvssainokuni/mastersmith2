# 性能・検査の時間の要件 — u2-portable-sql

上流の要件 NFR1（性能）・NFR5（検査の時間）を、この単位（H2 にしか通らない書き込みの文の書き直し）の `NFR1.x`・`NFR5.x` に分けます。この単位の実行時の変更は、書き込みの文の形と、最初の 1 回の書き込みで文が 1 本増える経路だけです。数値の目標は、H2 のときの今の目標のまま変えません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR1.1 | 内部DB が H2 のとき、性能・資源の目標（応答時間・メモリなど）は変えない。`savePreferences` は内部DB の文の数を 1 文のまま変えない（前の Intent の NFR2.1.2 を守る）。 | 既存の結合テストの全件が通ること。`savePreferences` が 1 回の呼び出しで流す文が 1 本であること（結合テストで文の数を数えるか、コードの読み取りで確かめる） | 要件 NFR1、機能設計 BR1.1、質問 Q1: A |
| NFR1.2 | 「あれば更新・無ければ挿入」の書き込み（作業ロールの保存・プレビューを置く）は、行が既にあるとき文 1 本（`UPDATE`）で、今の `MERGE ... KEY`（1 文）と同じ数。行がまだ無い最初の 1 回だけ、更新 → 挿入の文 2 本になる（セーブポイントの追加の往復を含めても、接続を借りる回数は増えない）。同時の実行で重なったときだけ、さらに更新をやり直す文 1 本が足される。 | 結合テストで、行が既にあるときの文の数（1）と接続を借りる回数が、書き直しの前と同じであること（`ConnectionAcquireCounter` と同じ形。結合テストの部品に既にある） | 要件 NFR1、機能設計 BR2.1、BR2.7 |
| NFR1.3 | 10MB の DSL の本文を置く書き込みは、本文のバイト列を余分に複製しない（呼び出しの引数の `byte[]` をそのまま束縛する）。ヒープの使い方・上限は今のまま。 | コードの読み取り（束縛に渡す前に複製していないこと）と、既存の 10MB の DSL の結合テスト（今のヒープ 1g の設定で通ること） | 要件 NFR1、機能設計 BR4.1 |
| NFR1.4 | PostgreSQL のときの値（書き込みの所要時間など）は、この単位では測らない。基準の値の記録は U1 の起動の時間（移行の時間・起動から UP まで）だけが持つ。k6・perf の台本・環境には手を入れない。 | Build and Test の成果物に「U2 の書き込みの所要時間は測っていない」と書く | 要件 NFR1（PostgreSQL の値は基準として記録するだけ。測るかは NFR 要件の段で決める） |
| NFR5.1 | 通常の `./gradlew verify`・`./gradlew verifyQuick`・CI に足すテストは、コンテナを使わない単体テストと、H2 の結合テストだけにする。PostgreSQL の結合テスト（U3）は専用のタスクだけで流れ、この単位は足さない。 | `./gradlew verify` が専用のタスクに依存しないこと（U1 の BR6.1・BR6.2 のまま） | 要件 NFR5、機能設計 BR6.1 |
| NFR5.2 | 同時の実行の確かめ（H2）は、待ち合わせで確実に作り、先の側の保持は約 500 ms とする。上限切れ（H2 は約 4 秒）の確かめは 1 本にまとめる。足す結合テストの時間の合計は、全件の `verify` の増加が約 30 秒以内（約 4 秒の上限切れ 1 本を含む）。 | Build and Test で、全件の `verify` の時間と CI の検査の job の時間を、基準（中央値 21分32秒）の 1.1 倍（約 23分42秒）・約 42 分の線と並べて記録する | 要件 NFR5、`team.md` の Testing Posture |
| NFR5.3 | 検査の時間が基準の 1.1 倍か約 42 分の線を超えたら、原因（足したテストか、環境か）を確かめて依頼者に諮る。この単位では判断の手順だけを持ち、設定を持たない。 | Build and Test の記録 | 要件 NFR5 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR1・NFR5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR1.1・BR2.1・BR2.7・BR4.1・BR6.1〜BR6.3）・`functional-spec.md`（F3・F5・F6・F10）
- `aidlc/spaces/default/memory/team.md`（Testing Posture）
- `docs/verification.md`（CI の検査の時間の基準）

## Assumptions & Open Questions

- [assumption] 全件の `verify` の増加が約 30 秒以内は目安で、合否には使わない。合否は基準の 1.1 倍と約 42 分の線（NFR5.3）。
