# 結合テストの手順（Intent 261004-safety-carryover）

Test Strategy は Minimal だが、bugfix の回帰と要件の確かめのため、この Intent で足した・直した結合テストをまとめる。単体テストの範囲を絞ったコマンドは `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/unit-test-instructions.md`。計画は `code-generation-plan.md`、結果の要約は `code-summary.md`。

## 対象と確かめること

| テスト | 要件 | 確かめること |
|---|---|---|
| `user/service/InitialAdminIT` | FR1.4・FR1.5 | 利用者がいないときに作り、`INITIAL_ADMIN_CREATED` が1行残る |
| `user/service/InitialAdminRescueIT` | FR1.1〜FR1.3・FR1.2a・FR1.6a・FR1.6b・NFR2・R-08 | 3つの条件（停止中・印なし・パスワードの不一致）の組み合わせで救い、`INITIAL_ADMIN_RESCUED` と条件の列の値、途中の失敗で全部が取り消されること、条件に当たらないときは何も変わらないこと、規則外のパスワードでは救わないこと |
| `user/service/InitialAdminSecretLeakIT` | NFR1 | TRACE を有効にしても、メールアドレス・パスワード・トークンがログと監査に出ない |
| `auth/web/AuthSuspensionSecretLeakIT`（直した） | NFR1 | `rescueInitialAdmin` の追跡の行が伏せ字で出る（G1: A） |
| `useradmin/web/UserAdminBusyLogTraceIT` | FR3.1 | 409 `USER_ADMIN_BUSY` の L3 の各行に、同じ `traceId` の L4 がちょうど1行 |
| `common/error/web/FilterExceptionErrorLogIT` | FR4.1・FR4.1a・FR4.2・NFR6 | 認証のフィルターの中で接続を借りられない要求で、ERROR が1行だけ（`ErrorPathController`）、応答は 500 で内部の文を含まない |

## 流し方

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.service.InitialAdminIT' \
  --tests 'cherry.mastersmith.user.service.InitialAdminRescueIT' \
  --tests 'cherry.mastersmith.user.service.InitialAdminSecretLeakIT' \
  --tests 'cherry.mastersmith.auth.web.AuthSuspensionSecretLeakIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminBusyLogTraceIT' \
  --tests 'cherry.mastersmith.common.error.web.FilterExceptionErrorLogIT'
```

- 内部DB は組み込みの H2 で、コンテナは要らない（`team.md` の Testing Posture）。全体は `verify` の中の `integrationTest` で毎回流れる。
- 待ち合わせは `sleep` ではなく決まった合図で行う（`RowLockHolder`・テストの中の接続の保持）。
- E2E（`./gradlew e2eTest`）は統合の前には流さず、配備の前（Deployment Pipeline の段）で流す（Code Generation の D5・D7: B、この段の Q3: B）。そのとき起動のログの救済の WARN が 0 件であることも件数で確かめる。
