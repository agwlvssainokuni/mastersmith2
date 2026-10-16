# スモークテストの結果（smoke-test-results）

`operation/deployment-pipeline/deployment-strategy.md` の 3節の S1〜S10 の結果です。パスワードの要る操作と画面の操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けました。値（メールアドレス・パスワード・DSL の本文）は表示していません。

## 結果

| # | 確かめ | 行った人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | 健全性と起動のログ | AI | 通過 | healthy、`/actuator/health` 200・`{"status":"UP"}`。レベルの件数は INFO 37・WARN 2・ERROR 0。WARN は前の版から出ている2件（Flyway の H2 の版の案内、Spring の BeanPostProcessorChecker）だけ |
| S2 | JVM と移行 | AI | 通過 | `openjdk version "25.0.4.1" 2026-08-18 LTS`。PID 1 のコマンド行に `ExitOnOutOfMemoryError` 1件・`MaxRAMPercentage=50.0` 1件。Flyway の `Migrating schema` 0 件、「Current version of schema "PUBLIC": 12」「No migration necessary」。初期管理者の INFO「初期管理者は既にいるため、作成しませんでした」のキーは `timestamp,level,logger,thread,message,maskedEmail`（`email` は無い） |
| S3 | `hikari-pool.sh` | AI | 通過 | 新しい jdk のイメージの `docker/hikari-pool.sh status` が新しい JRE のアプリに対して終わりの値 0 で動き、「使用中 0・待機 30・合計 30・借りる待ち 0、ファイル 0.1MiB」を出した。内部DB のファイルは実際に 81,920 バイトで、0.1MiB と合う（Build and Test の T-14 を満たした） |
| S4 | 書体の配信 | AI | 通過 | 配る `index.html` の CSS（`assets/index-Da7dcKYK.css`）に `noto-sans-jp-japanese-400-normal-C7ZJEbOP.woff2` があり、書体のファイルが 200 で返った |
| S5 | ★ログインと書体の表示 | 依頼者 | 通過 | ログインでき、ホームが開き、日本語は崩れずに表示された（依頼者の答え）。監査の `LOGIN_SUCCEEDED` 1件で裏付け（Build and Test の T-15 を満たした） |
| S6 | ★Dropdown の収まり | 依頼者 | 通過 | 利用者の管理の一覧の表の右端の行の「操作」のメニューが、幅を狭めても画面の中に収まった（依頼者の答え） |
| S7 | ★畳んだサイドバーの印 | 依頼者 | 通過 | 畳むと今の項目に印が出た（依頼者の答え） |
| S8 | ★英語の表示のトップバー | 依頼者 | 通過 | 英語に切り替えるとトップバーと画面の文言が英語になり、日本語に戻した（依頼者の答え） |
| S9 | ★DSL の管理の画面 | 依頼者 | 通過 | 状態の表示に落とした項目の警告は出なかった（依頼者の答え）。プレビューは作っていない |
| S10 | 監査の記録 | AI | 通過 | 配備の後の複写で、T0（23:49:32Z）以降の `audit_events` は `LOGIN_SUCCEEDED` 成功 1件だけ。期待（S5 のログインの回数、ほかの種類と失敗の行は 0）と一致。依頼者の答え「ほかの操作なし」と合う。初期管理者は停止なし・印あり |

- 1行の長さの上限の拒否は確かめていない（Deployment Pipeline の Q2: B。結合テスト `DslAdminApiIT`・`RoleTransferLimitsApiIT` で確かめ済み）。

## 判定

**合格**（S1〜S10 のすべてが通過）。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md` 3節（確かめの一覧と監査の期待の件数）
- `construction/build-and-test/build-and-test-summary.md`（T-14・T-15）
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`

## Assumptions & Open Questions

- S5〜S9 は依頼者の目視の答えに基づく。画面の振る舞いは Build and Test の E2E（232 件）でも確かめ済み。
