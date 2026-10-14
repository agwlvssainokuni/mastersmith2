# スモークテストの結果（smoke-test-results）

Intent 261004-role-menu の配備の後の確かめ（`operation/deployment-pipeline/deployment-strategy.md` の 3節の S1〜S11）の結果です。時系列と数えた値の詳細は `deployment-log.md` にあります。

- 画面の操作（★）は依頼者が `http://localhost:8080/` で行いました。AI はアプリのログと監査の記録で裏付けました。
- 監査の記録は、アプリを止めて取った複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて数えました。値（メールアドレス・パスワード・DSL の本文）は表示していません。
- 監査の数え始め: T0 ＝ 2026-10-13T23:15:41Z（初回）、T1 ＝ 2026-10-14T06:17:59Z（S8 の再試行）、T2 ＝ 2026-10-14T07:56:31Z（直した版での確かめ直し）。

**判定: すべて通過**（S8 は初回の版で不具合が見つかり、直した版 `a16cd68` で確かめ直して通過。S11 の失敗の行 1 件は依頼者の操作の順による業務の拒否で、依頼者の確かめ待ち）

## 1. 版

| 版 | アプリのコード | イメージ | 確かめた範囲 |
|---|---|---|---|
| 初回 | `005730a`（`develop` の `2669c62`） | `sha256:e41a5b02fd0b…`（今は `mastersmith:role-menu-before-s8fix`） | S1〜S10（S8 は不通過） |
| 直した版 | `a16cd68`（S8 の不具合の直し） | `sha256:3247c5bdab79…`（`mastersmith:local`、今動いている版） | S1・S8 の確かめ直し・片付け・S11 |

## 2. 結果

| # | 確かめ | 行った人 | 結果 | 証拠 |
|---|---|---|---|---|
| S1 | 健全性と起動のログ | AI | 通過 | 初回: healthy・200。ERROR は「適用中の DSL を読めない…」1件だけ（版 1 の DSL が適用中だったため、見込みどおり）。WARN は前の版からの案内2件。直した版: healthy・200。ERROR 0（版 2 の DSL が適用済み）、WARN は同じ2件。どちらも救済の WARN 0、初期管理者のメールアドレスの値の件数 0（`deployment-log.md` の 9節・11.5） |
| S2 | 移行と JVM の設定 | AI | 通過 | 初回の起動で `Migrating schema` 3 件（V10〜V12）、版 12。直した版では新しい移行なし。`-XX:+ExitOnOutOfMemoryError` と `-XX:MaxRAMPercentage=50.0` が PID 1 に1件ずつ。初期管理者の INFO はキー `maskedEmail` だけで `email` なし（8節・9節・11.4） |
| S3 | ★ログイン | 依頼者 | 通過 | ホームと「ホーム」「業務」「管理」の区画が開けた（依頼者の報告、23:43:32Z）。`LOGIN_SUCCEEDED` あり |
| S4 | ★DSL の移し替え | 依頼者 | 通過 | 版 2 を生成して適用した。`DSL_GENERATED` 1・`DSL_APPLIED` 1（T0〜T1）。適用の履歴は 5 → 6 |
| S5 | ★ロールと権限の設定 | 依頼者 | 通過 | `ROLE_CREATED` 2・`ROLE_PERMISSION_CHANGED` 2（T0〜T1） |
| S6 | ★割り当て | 依頼者 | 通過 | `ROLE_ASSIGNED` 2（T0〜T1） |
| S7 | ★作業ロールの切り替えと業務のメニュー | 依頼者 | 通過 | `WORK_ROLE_SWITCHED` 1（T0〜T1）。依頼者の報告で業務の区画の出し分けは見込みどおり |
| S8 | ★自分の権限 | 依頼者 | **初回の版で不通過 → 直した版で通過** | 3節 |
| S9 | ★グループと利用者の詳細 | 依頼者 | 通過 | `GROUP_CREATED` 1・`GROUP_MEMBER_ADDED` 1（T0〜T1） |
| S10 | ★片付け（Q3: A 消す） | 依頼者 | 通過 | 初回: `ROLE_UNASSIGNED` 2・`ROLE_DELETED` 2・`GROUP_MEMBER_REMOVED` 1・`GROUP_DELETED` 1（T0〜T1）。S8 の確かめ直しで作ったロール2つも T2 以降に片付けた（`ROLE_UNASSIGNED` 2・`ROLE_DELETED` 2）。最後の状態はロール・権限の設定・割り当て・作業ロールの保存・グループ・メンバーがすべて 0 |
| S11 | 監査の記録 | AI | 通過（失敗の行 1 件は依頼者の確かめ待ち） | 4節 |

## 3. S8（自分の権限）の不具合と、直した版での確かめ直し

| 時点 | 内容 |
|---|---|
| 初回（23:43:32Z の報告） | 「自分の権限を見る」で権限が表示されなかった。「読み込んでいます」のまま変わらず、しばらく待つと誤りの表示になった |
| 使い捨ての環境での再現（03:30〜03:37Z） | 再現しなかった。テーブルの節の開閉を押していなかったため（`deployment-log.md` の 10節） |
| 配備したアプリでの再試行（T1 以降） | 再現した。木でテーブルの段を広げたところで固まり、ブラウザが反応しなくなった |
| 原因 | 画面だけの不具合。`useMyPermissions.ts` が API の `hasChildren` をテーブルの節にもそのまま渡し、テーブルの節を開くと同じ id の自分自身を子に持つ木になって描画が止まらなかった（`fix-notes.md`） |
| 直し | `a16cd68`（テーブルの節に開閉を出さない、スキーマ以外の id では子を読まない、再現のテスト）。依頼者の決定で直し、verify と E2E 230 件を作業ブランチで通して `develop` へ squash で統合した。スキーマの変更は無い |
| 配備し直し（07:54〜07:55Z） | WAR の SHA-256 `f5293bed…8c58a376` を確かめ、配っている `MyPermissionsPage` の資源に直しが入っていることを確かめた（`deployment-log.md` の 11節） |
| 確かめ直し（T2 以降） | 依頼者の報告（13:10:43Z）: テーブルに開閉の印（▸）が無い、テーブルを選ぶと値が出る、固まらない。監査で `ROLE_PERMISSION_CHANGED` 2・`WORK_ROLE_SWITCHED` 3 を裏付けた |

## 4. S11（監査の記録、T0 〜 2026-10-14T13:18:34Z）

配備の後の複写 `~/.mastersmith-backup/mastersmith-data-202610142218-after-role-menu-final.tgz` で数えました。

| 種類 | 結果 | T0〜T1 | T1〜T2 | T2 以降 | 合計 |
|---|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | SUCCESS | 2 | 0 | 0 | 2 |
| `LOGGED_OUT` | SUCCESS | 1 | 0 | 0 | 1 |
| `DSL_GENERATED` | SUCCESS | 1 | 0 | 1 | 2 |
| `DSL_PREVIEW_DISCARDED` | SUCCESS | 0 | 0 | 1 | 1 |
| `DSL_APPLIED` | SUCCESS | 1 | 0 | 0 | 1 |
| `ROLE_CREATED` | SUCCESS | 2 | 2 | 0 | 4 |
| `ROLE_PERMISSION_CHANGED` | SUCCESS | 2 | 0 | 2 | 4 |
| `ROLE_ASSIGNED` | SUCCESS | 2 | 2 | 0 | 4 |
| `WORK_ROLE_SWITCHED` | SUCCESS | 1 | 1 | 3 | 5 |
| `ROLE_UNASSIGNED` | SUCCESS | 2 | 0 | 2 | 4 |
| `ROLE_DELETED` | SUCCESS | 2 | 0 | 2 | 4 |
| `ROLE_DELETED` | FAILURE（`ROLE_IN_USE`） | 0 | 0 | 1 | 1 |
| `GROUP_CREATED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`・`GROUP_DELETED` | SUCCESS | 各 1 | 0 | 0 | 各 1 |
| `INITIAL_ADMIN_RESCUED`・`INITIAL_ADMIN_CREATED`・そのほか | — | 0 | 0 | 0 | 0 |

- 期待の件数は、依頼者の答えで次のように合わせた。
  - 順 4 の破棄は行われていなかった（`DSL_PREVIEW_DISCARDED` 0。プレビューは配備の前から無かった）。
  - 順 4 のログアウト 1 件を足した。
  - T2 以降の DSL の生成と破棄の各 1 件は依頼者の操作。
  - T1〜T2 の作成と割り当てと切り替えは、S8 の再試行の分。
- `ROLE_DELETED` の FAILURE 1 件（13:15:42Z、`ROLE_IN_USE`、アプリのログでは 409）は、割り当てを外す前にロールを消そうとして仕様どおり拒否されたもの。その後に割り当てを外して消し直し、成功している。

## 5. 戻しの練習（参考）

`rollback-runbook.md` の 6節の D1〜D6 はすべて通過しました（`deployment-log.md` の 13節、`health-check-report.md` の 4節）。

## Sources

- `deployment-log.md`（1節・6節〜14節、Assumptions & Open Questions の依頼者の答え）
- `fix-notes.md`
- `deployment-execution-questions.md`（Q1〜Q3、まとめの確認）
- `operation/deployment-pipeline/deployment-strategy.md`（3節・3.2・4節）・`cd-config.md`・`rollback-runbook.md`（6節）
- 配備の前と後の複写の読み取り（`org.h2.tools.Shell`、`ACCESS_MODE_DATA=r`）、`docker compose logs`・`docker logs`（レベル・ロガー・`code`・`status`・キーの名前・件数だけ）
- `git show 273c021`・`a16cd68`（`DslLifecycle.java`、`useMyPermissions.ts`）

## Assumptions & Open Questions

- [assumption] `ROLE_DELETED` の FAILURE 1 件は、依頼者が割り当てを外す前に消そうとした操作だと読んだ（時刻の並びと `ROLE_IN_USE` から）。依頼者に確かめる。
- [assumption] S3・S7・S9 の画面の見え方（区画・業務のメニューの出し分け・ロールの出どころ）は、依頼者の報告（「ほかは見込みどおり」）に頼った。AI は監査の件数で操作の有無だけを裏付けた。
- S3〜S10 の初回の確かめは、直す前の版（`e41a5b02fd0b`）で行った。直した版との差は `frontend/` の「自分の権限を見る」の木だけ（`git diff 876042b a16cd68`）のため、S8 のほかは直した版で繰り返していない。
