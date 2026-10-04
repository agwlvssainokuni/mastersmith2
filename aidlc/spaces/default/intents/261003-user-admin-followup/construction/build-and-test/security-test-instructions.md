# 安全の確かめの手順（261003-user-admin-followup）

- この Intent には安全の要件 S1（FR8・NFR2: 一意の制約の違反の文の漏えい）がある。Test Strategy は Minimal だが、bugfix の下限（不具合を再現する回帰のテスト）として漏えいの結合テストを足したため、確かめ方をここにまとめる（段の定義の「Minimal でも安全の手順書を作ってよい」）。
- 守る決まり: `project.md` の Forbidden（メールアドレスをアプリのログとエラー応答に含めない、パスワード・トークンをログ・監査・トレースの属性・エラー応答に含めない）。

## 1. 漏えいの結合テスト（S1、FR8・NFR2）

### 1.1 確かめた経路と直し方

- 計画の段の捨ての確かめで、既定の INFO で Hibernate のロガー `org.hibernate.orm.jdbc.error` の WARN が、重なったメールアドレスを含む違反の文を出すと分かった（計画 6節）。アプリが違反を受けて結果の型にする前に Hibernate が自分で出すため、受ける所の直しだけでは止まらない。
- 直し方は案 C（依頼者の決定 D1）:
  - 案 A: `backend/src/main/resources/application.yaml` で `logging.level.org.hibernate.orm.jdbc.error: OFF`。
  - 案 B: `GlobalExceptionHandler` の想定外の誤り（5xx）の ERROR で、一意の違反の連なり（`UniqueViolations` の判定。Spring の `DataIntegrityViolationException`・Hibernate の `ConstraintViolationException`・SQLState `23505`）はクラスの名前だけを出す。応答は 500 `INTERNAL_ERROR` のまま。

### 1.2 テストと確かめる出力

| テスト | 経路 | 確かめる出力 |
|---|---|---|
| `user/service/UserUniqueViolationSecretLeakIT` | `uk_users_email`（同時の利用者の作成。登録の完了も同じ口） | 既定の INFO と、`cherry.mastersmith` を TRACE にした場合のアプリのログ（標準出力・標準エラー出力）。TRACE では追跡が働いていることも確かめる |
| `invitation/service/InvitationUniqueViolationSecretLeakIT` | `uk_invitations_pending_email`（同時の招待） | 同上 |
| `common/error/web/GlobalExceptionHandlerTest` | 想定外の一意の違反（3通り）と、違反でない SQL の誤り | ERROR がクラスの名前だけになり、応答は 500 `INTERNAL_ERROR` |

- 形は既存の `*LeakIT` と同じ。値の無いことは `JsonLogRecords.assertContainsNoSecret` で確かめ、大文字にした形も出てはならない値に入れる。
- 監査: 作成の口は監査の行を書かない（行は 0 件）。招待の経路の監査とエラー応答には、業務の結果の型（`EmailAlreadyUsed`・招待中の勝った側）で受けるため、例外の文が届かない。
- トレースの属性: 2つの経路のスパンに値を載せる処理は無い（コードで確かめた）。外へ送る前の除き方は既存の `SanitizingSpanExporter`（`SanitizingSpanExporterTest` で確かめ済み）に任せる。
- ほかの一意の制約（トークンのハッシュ・アプリが作る ID）は、利用者が重なった値を作れないため対象の外（計画 5節 R-04）。

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.user.service.UserUniqueViolationSecretLeakIT' \
  --tests 'cherry.mastersmith.invitation.service.InvitationUniqueViolationSecretLeakIT'
./gradlew :backend:test --rerun --tests 'cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest'
```

- 再現の確かめ（コード生成の段で実施済み）: 直し（`application.yaml` の1行と `GlobalExceptionHandler` の変更）を一時的に外すと漏えいのテスト4件がすべて落ち、違反の目印を含む行は `org.hibernate.orm.jdbc.error` の行だけだった。戻した後は通る。

## 2. `verify` の中の安全の検査（8 の段 `verifySecurity`）

| 検査 | 道具 | 統合を止める基準 |
|---|---|---|
| 静的解析 | SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1（重大度 High）。加えて `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず止める。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| 依存関係の脆弱性 | OSV-Scanner（`osvScan`。lockfile と `vendor/make-you-chic-ui` の lockfile を含む） | 重大度 High 以上。画面の開発時だけの依存は警告にとどめるが、悪意のあるパッケージ（`MAL-`）と `config/npm-build-tools.txt` の道具の High 以上は止める |
| 秘密情報の検出 | Gitleaks（`gitleaksScan`。リポジトリの履歴全体） | 1件でも見つかれば止める。誤検知の除外は `.gitleaks.toml` に理由を書き、パスと値の形で絞る |

- コミットの前（pre-commit フック）でも Gitleaks とフォーマットの検査が動く。CI も同じ `verify` を呼ぶ。
- 単独で流すとき: `./gradlew verifySecurity`（前の段の成果物が要るため、通常は `verify` 全体で流す）。
- この Intent で足した依存は無い（make-you-chic-ui の固定先を上げても `frontend/package-lock.json` は変わらなかった）。

## 3. 報告とログの秘密の値の確かめ

| 対象 | 確かめ方 | 期待 |
|---|---|---|
| E2E の json の報告（`frontend/test-results/e2e-results.json`）と報告の部品 | 報告の部品の確かめと json の文字列の検索（`project.md` の学び 2026-09-28） | パスワード・トークン・メールアドレスが 0 件 |
| 負荷の試験のアプリのログ（`perf/README.md` の手順 4''） | 件数だけを数える（値は表示しない） | `perf-ua` は `enteredEmail`（既知の例外）の行の数と一致し、それ以外の行では 0。`MVStoreException`・`password`・`Bearer `・`eyJ` は 0 |
| k6 の出力（`limit-k6.log`） | `@example` の件数 | 0 |

- `enteredEmail` は、監査の記録の失敗の ERROR で手で記録を補うための既知の例外としてメールアドレスそのものを載せるキー（`project.md` の Corrections 2026-09-29）。新しい漏えいの経路ではない。
- k6 の結果の JSON（`limit.json`）の `setup_data.userAdminIds` には、試験用の利用者のメールアドレス（予約のドメイン `@example.test` の仮の値）が鍵として入る。`build/` は git の対象外で、トークン・パスワードは含まれない。

## 4. 結果（この段の実測。詳細は `test-results.md`）

- 漏えいの結合テスト（4件）と `GlobalExceptionHandlerTest`（13 件）は、`develop` の `a1b41e7` の clean 付きの `verify` で通った。
- `spotbugsGate`・`gitleaksScan`・`osvScan` は同じ `verify` で通った。
- E2E の報告の秘密の値: 0 件（コード生成の段の Step 20）。
- 負荷の試験: `perf-ua` 16 件（すべて `enteredEmail`、`auditEventType=LOGIN_SUCCEEDED`）、ほかは 0 件。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`（4節 D1、5節 R-04、6節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`（1節、2節、3.3節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/unit-test-instructions.md`（3節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/t1-load-test-results.md`（5節 秘密の漏えいの件数）
- `README.md`（「1コマンドの検査」「依存関係の脆弱性の判定（OSV-Scanner）」）
- `aidlc/spaces/default/memory/team.md`（Code Style の静的解析とセキュリティ検査、Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden・Corrections）

## Assumptions & Open Questions

- 外部エクスポートを有効にした場合に Hibernate の WARN が外へ送られる経路は、案 A でロガーを止めたため出どころが無くなると見ている。外部エクスポートを有効にした状態で一意の違反を起こす確かめは、この Intent では行っていない（計画 6節の「確かめていないこと」）。
