# セキュリティの確かめの手順（Intent 261004-safety-carryover）

救済の口は認証と利用者の状態に関わり、監査と秘密の扱いにも触れるため、Minimal の戦略でもセキュリティの確かめをまとめる（`project.md` の Mandated・Forbidden）。上流は `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`。

## 関門（1コマンドの検査の中）

| 検査 | 止める基準 | この Intent での結果の置き場 |
|---|---|---|
| Gitleaks | 検出 1 件以上 | `test-results.md` 1節 |
| SpotBugs ＋ FindSecBugs | priority 1、または `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`（priority にかかわらず） | 同上。priority 2 の新しい警告3件は止める基準に当たらない |
| OSV-Scanner | 実行時の依存の High 以上、悪意のあるパッケージ、成果物を作る道具の High 以上 | `./gradlew osvScan --rerun` の結果 |

## 結合テストでの確かめ（脅威と対策）

| 脅威（STRIDE） | 対策 | 確かめるテスト |
|---|---|---|
| 情報の漏えい: 起動のログ・監査・トレースにメールアドレスやパスワードが出る | ログのキーは `maskedEmail`・`conditions`・`exceptionClass` だけ。監査の行は `entered_email` を空にする。`InitialAdminProperties.toString()` を伏せる | `InitialAdminSecretLeakIT`・`InitialAdminPropertiesTest`・`SecretTypesTest`・`AuthSuspensionSecretLeakIT` |
| 権限の昇格: 画面・API から救済の口を使う | 救済は起動時の設定だけで働き、画面・API の口は無い | 救済は `InitialAdminInitializer` の起動時の処理だけ（`InitialAdminRescueIT`） |
| なりすまし: 救済の前に出したトークンで入り続ける | 救済でリフレッシュトークンをすべて無効にする | `InitialAdminRescueIT`（救済の後に起動の前のリフレッシュトークンが拒否される） |
| 否認: 救済したことを後で否定できる | 監査に `INITIAL_ADMIN_RESCUED`（条件と対象）、ログに WARN | `InitialAdminRescueIT`・`AuditEventFactoryTest`・`AuditEventListenerTest` |
| 改ざん（一部の反映）: 救済の途中で止まり、一部だけ反映される | 1つのトランザクションで全部か無しか | `InitialAdminRescueIT`（失敗させる受け手で全部が取り消される） |
| 情報の漏えい: 例外の ERROR が二重に出て、ログの監視の件数が膨らむ | Tomcat の dispatcherServlet のロガーを止め、`ErrorPathController` の1行だけを残す。応答に内部の文を出さない | `FilterExceptionErrorLogIT`、この段の R-01 の実機の確かめ（`performance-test-instructions.md`） |

## 受け入れたリスク（依頼者の決定）

- 初期管理者を止めた・印を外しても、`.env` を替えずに再起動すると戻る（要件の「受け入れる振る舞い」、F5: A）。手順書（README）に先に `.env` を替える手順を書いた。
- Tomcat のロガーを止めたことで、応答を書き始めた後の例外や `/error` に回らない経路の例外のログも残らない（D3: A、レビューの R-02）。
