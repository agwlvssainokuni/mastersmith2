# 結合テストと E2E の手順（Intent 260928-quality-followup）

Test Strategy は Minimal で、この段で新しいテストは作らない。コード生成で足した・直したテスト（`aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/unit-test-instructions.md` の 2節）を、統合の後の状態で全体の中で流す手順を書く。

## バックエンドの結合テスト

`./gradlew verify` の中の `:backend:integrationTest`（`*IT`、Spring と組み込み H2、対象DB は Testcontainers）。この Intent に関わるもの:

| テスト | 確かめること | 要件 |
|---|---|---|
| `config/H2CompactionByPoolSuspensionIT` | 0 本の待ちの上限 30 秒と、時間切れのときの診断 | FR3.1・FR3.3 |
| `common/observability/HistogramBucketsIT`（新規） | 決めた境界だけのバケット | FR4.1・FR4.2・NFR3 |
| `config/ExposureIT` | 公開の窓口が health だけのまま | NFR5 |
| `invitation/web/RegistrationApiIT`・`InvitationAuditIT` | README に書いた監査の有無の裏付け | FR6.3 |
| `targetdb/*`（`TargetDbImages` の digest を上げた3種類） | mysql 26.7.0・mariadb 13.0.2・postgres 18.6 の読み取り | FR5.3 |

## E2E（`verify` と CI の外）

```bash
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest --console=plain
```

- 050〜080 はブランドカラーとテーマのすべての組のアクセシビリティの検査（既知の違反の一覧は空）、090 は招待から登録までの流れ、100（新規）はアプリ独自の CSS のコントラストと、make-you-chic-ui に残る既知の違反（選ばれたタブ・primary のボタンの hover）の確かめ。
- 結果の json（`frontend/test-results/e2e-results.json`）に、仮の資格情報（パスワード・トークン・初期管理者のメールアドレス）が残っていないことを文字列の検索で確かめる（`project.md` の Testing Posture）。

## 診断の確かめ（FR3.3。1回だけ行い、コードに残さない）

- `H2CompactionByPoolSuspensionIT`: 上限を 500 ミリ秒にし、205 行の待ちの条件を一時的に満たせない形（`== -1`）にして流す。1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかったため。確かめたら `git checkout` で戻す。
- `InvitationAdminPage.test.tsx`: `LEAK_CHECK_TIMEOUT_MS` を 1 にして該当の1件だけを流し（`npx vitest run … -t 'keeps addresses and names out of storage'`）、戻す。
