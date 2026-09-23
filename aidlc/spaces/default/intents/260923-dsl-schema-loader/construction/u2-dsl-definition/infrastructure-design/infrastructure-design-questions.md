# Infrastructure Design — U2 DSL の定義（u2-dsl-definition）— 設計の要点の確認

U2 は外部に接続しない library で、基盤に足すのは依存・ビルド・JSON Schema の公開の道だけ。新しく依頼者に尋ねる論点は無いため、要点を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。成果物は cicd-pipeline.md と traceability.json。

## 設計の要点（案）

- **依存**: SnakeYAML 2.6（今は推移依存。直接の依存にして版を明示）と networknt json-schema-validator 3.0.6 を足し、lockfile を更新する。networknt の推移依存の `jackson-dataformat-yaml`・`snakeyaml-engine` は依存から外す（外せなければ ArchUnit で `dsl` から使わないことを確かめる）。依存の木で Jackson が Spring Boot の版（3.1.5）のままであることを確かめる（NFR 要件の学び）
- **JSON Schema の公開**（U2 の NFR 設計 6節）: 正本 `backend/src/main/resources/dsl/dsl-schema-v1.json` を、`bootWar` で WAR の静的なファイルの置き場（`WEB-INF/classes/static/dsl/`）へ複写する（既存の画面の `dist` の同梱と同じ仕組みに足す）。画面の `dist` と名前が重ならないよう、画面の側で `/dsl/` の下にファイルを置かない
- **公開の道の許可**: 既存のセキュリティの決まり（`SecurityRuleContributor` の仕組み）で、`GET /dsl/dsl-schema-v1.json` だけをログインなしで許す。本文の上限の Filter（U4 の NFR 設計で認証・認可の後に移す）の影響は受けない（GET で本文なし）
- **画面の開発サーバー**: Vite のプロキシに `/dsl` を足し、バックエンドから JSON Schema を取る（今は `/api`・`/actuator` だけ）
- **検査**: 既存の `./gradlew verify` のまま。OSV-Scanner で足した依存の脆弱性を、Gitleaks で秘密情報を確かめる（既存）。正規表現の打ち切りの前提の実測と、10MB の DSL のメモリ・時間の測定は Build and Test
- **成果物**: cicd-pipeline.md と traceability.json

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U2 の基盤設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

---

## 確認の後の直し（2026-09-24、Infrastructure Design の Request Changes）

上の要点の「公開の道の許可」（`SecurityRuleContributor` の仕組みで `GET /dsl/dsl-schema-v1.json` だけをログインなしで許す）は、レビューの指摘（今の `SecurityConfig` は `/api/**` の外を `anyRequest().permitAll()` でログインなしで通す）を受け、依頼者の Request Changes で「許す設定は足さない」に変えた。正は cicd-pipeline.md 2節。上の確認の記録は書き換えずに残す。
