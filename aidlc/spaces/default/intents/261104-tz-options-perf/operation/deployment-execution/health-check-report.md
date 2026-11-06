# 健全性の報告（health-check-report）— Intent 261104-tz-options-perf

時刻は UTC。ログは JSON として読み、ロガーと項目の名前と件数だけを出した（値は表示しない）。

## 1. 入れ替えの直後（S1、20:55:52）

| 確かめ | 通る条件 | 結果 |
|---|---|---|
| 健全性 | healthy・`/actuator/health` 200 | healthy（起動から約 10 秒）・200 |
| 資源 | メモリの上限 2 GiB・CPU 4 | 2,147,483,648 バイト・4 |
| イメージ | コンテナのイメージ ID と `mastersmith:local` が一致 | 一致（`4ee5fb45593e`） |
| WAR | 配備した WAR と同じ | コンテナの中の WAR の SHA-256 が `e2065c43…` |
| V14 | Flyway の `Migrating schema` が 1 件・版 14 | 1 件・14 |
| ERROR | 0 件 | 0 件（ログ 40 行、すべて JSON。INFO 38・WARN 2） |
| WARN | 前の版から出ているものだけ | 2 件（Bean の後処理の案内・Flyway の H2 の版の案内） |
| 救済 | 0 件 | 0 件 |
| 初期管理者のメールアドレス | そのものが出ない | 伏せ字のキー `maskedEmail` の行が 1 件、`.env` の値そのものは 0 件 |

## 2. スモークテストの間と配備の後の複写（S10）

- スモークテストの間（〜21:36:56）のログ 55 行: ERROR 0・5xx 0。WARN 11 件は、起動の 2 件と、想定内の 4xx の 9 件（`smoke-test-results.md` 3節）。メールアドレス 0・JWT の形 0。
- 順 10 の起動し直し（21:37:07）: healthy、イメージ ID は `4ee5fb45593e` のまま。
- 配備の後の複写: スキーマの版 14、`users.display_time_zone` の列あり・値の入った行 0、初期管理者は停止 0・印 1（`deployment-log.md` 2.2）。

## 3. 戻しの練習（`rollback-runbook.md` 3節、Q2: A）

| 項目 | 内容 |
|---|---|
| 使ったもの | 配備の後の複写 `…-after-tz-options-perf.tgz` を使い捨てのボリュームへ展開（所有者 10001）。戻し先 `mastersmith:pre-tz-options-perf`（`ffd2b8380460`）を使い捨てのコンテナで起動。ネットワークなし（`--network none`）・メモリ 2 GiB・CPU 4。環境変数は動いているアプリのコンテナの値を権限 600 のファイルに写して渡した（値は表示していない） |
| 起動 | 21:38:31 に起動、21:38:40 に `/actuator/health` 200（約 9 秒） |
| ログ | 40 行、すべて JSON。ERROR 0。`Migrating schema` 0 件。Flyway は 14 件の移行を検証して成功し、「スキーマは最新、移行は不要」として進んだ。救済 0 件 |
| WARN | 3 件。前の版から出ている 2 件と、Flyway の `DbMigrate` の「スキーマの版 14 が、手元の最後の移行 13 より新しい」の 1 件。最後の 1 件は戻したときにだけ出る想定内の知らせで、止まらない（`deployment-log.md` 4節） |
| 判定 | 通過。V14 の後の内部DB で前の版が起動することを、実物のイメージで確かめた（261102 の U2 と `docs/deployment.md` の V14 の項が配備の段に回した確かめ） |
| 片付け | 使い捨てのコンテナ・ボリューム・環境変数のファイルを消し、残っていないことを確かめた。動いているアプリは healthy のまま |

## 4. 残る前提

- 戻しの練習は起動の確かめだけで、対象DB とメールにつながず、ログインと画面は確かめていない（`rollback-runbook.md` 3節のとおり）。実際に戻したときは R2（依頼者のログインと画面）で確かめる。
- 監視（lgtm）は起動していない（F2: A）。手元の監視を常に動かしていない間の SLO の判定は Unverified のまま（前の Intent までと同じ）。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S1・S10）・`rollback-runbook.md`（2.3・3節）
- `deployment-log.md`・`smoke-test-results.md`
- 実行したコマンドの出力（`docker inspect`・`docker logs`・`/actuator/health`・`sha256sum`・複写の読み取り）
