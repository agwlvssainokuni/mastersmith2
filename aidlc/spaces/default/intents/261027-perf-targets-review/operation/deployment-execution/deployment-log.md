# 配備の記録（deployment-log）

Intent 261027-perf-targets-review。流れは `operation/deployment-pipeline/cd-config.md` の 3節。時刻は UTC。

## 1. 結果

- 配備した版: アプリのコードは `main`（`94538ef`）と同一。配備の記録は `develop` の `154c27b` 以降。WAR の SHA-256 は **`ff50498b88a97ac122ac39336ed946908389b1ddcacb14665e8412deead6f9f0`**（作り直しの前後・動いていたイメージの中・今のイメージの中がすべて同じ）。
- 動いているコンテナ: `mastersmith-app-1`、イメージ `mastersmith:local`（`sha256:e0ede6ddd5c4…`）、メモリ 2 GiB・CPU 4、restart 0、healthy。
- 戻し先: `mastersmith:pre-perf-targets-review`（`sha256:3075065a2653…`、中身は同じ WAR・同じ作成時刻）。
- スモークテスト S1〜S6 はすべて通過（`smoke-test-results.md`）。

## 2. 手順の記録

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | — | 関門: アプリのソースの未コミット 0 件。`git diff --stat main HEAD -- . ':!aidlc'` は 6 ファイル（`perf/`・`.github/dependabot.yml`、219 行追加・12 行削除）。`MASTERSMITH_IMAGE_TAG` の残り 0。AC 電源・ディスク 32% | 通過 |
| 2 | — | `./gradlew :backend:bootWar --rerun-tasks`（画面のビルドを含む） | 成功。SHA-256 は前後とも `ff50498b…`。画面のビルド（`static/` 98 件）と読み取りのプール既定 10 が WAR の中にある |
| 3 | 03:17 頃 | `docker tag mastersmith:local mastersmith:pre-perf-targets-review` | ID `3075065a2653…` を確認 |
| 4 | — | 救済の見込みを依頼者に確認（Q1: A） | 初期管理者について変更なし。実際にも救済のログ 0 件 |
| 5 | 03:17:57〜03:17:58 | T0 を控え `docker compose stop app`（依頼者の承認 Q2: A） | 停止 |
| 6 | 03:18:00 | `docker compose --profile targetdb-postgres up -d --build app` | イメージは新しくビルドされた（`e0ede6ddd5c4`、キャッシュで 2 秒）が、**コンテナは作り直されず旧イメージ（`3075065a2653`）のまま起動し直しただけ**だった（下の 3節） |
| 7 | 03:18:09 | healthy、`/actuator/health` 200、起動のログ | 旧イメージで起動。ERROR 0、Flyway 移行 0 件（現在の版 13）、救済 0 |
| 8 | （依頼者の操作後） | スモークテスト S3・S4（依頼者）、S5（AI） | 通過 |
| 9 | 10:24:37 | 配備の後の複写: `docker compose stop app` → `tar czf` → `docker compose start app`（停止は 1 秒未満）。複写は `~/.mastersmith-backup/mastersmith-data-202610291924-after-perf-targets-review.tgz`（権限 600、39,247 バイト）。読み取りで開いて T0 以降の監査を数えた（S6）。展開した作業ディレクトリは削除 | `LOGIN_SUCCEEDED` 1・`LOGGED_OUT` 1 |
| 10 | 10:25:15〜10:25:25 | コンテナを新しいイメージにそろえるため `docker compose --profile targetdb-postgres up -d --no-build --force-recreate --no-deps app` | Recreated。healthy まで約 10 秒。イメージ `e0ede6ddd5c4`、`/actuator/health` 200、ERROR 0、救済 0、Flyway の現在の版 13 |
| 11 | — | 試験用タグの片付け: `docker rmi mastersmith:perf-targets-review`（タグだけ、Q4: A） | `Untagged`。`mastersmith:local`・`pre-perf-targets-review` は残る |

- アプリが止まった時間: 順 5〜7 で約 11 秒、順 9 で約 1 秒未満、順 10 で約 10 秒（見込みの 1〜2 分より短い）。見本の PG・Mailpit は作り直していない。
- `.env`・内部DB のスキーマ・見本の PG は変更なし。

## 3. 計画との差・気づき

1. **旧イメージのまま起動し直した**: `up -d --build app` はイメージを新しくビルドしたが、コンテナを作り直さず、旧イメージの ID（`3075065a2653`）のコンテナを起動し直した。新しいイメージは同じ WAR・同じ作成時刻で、ID だけが違う（ビルドの書き出しの差、見立て）。スモークテスト S3・S4・S6 は旧イメージのコンテナで行い、その後 `--force-recreate` で新しいイメージのコンテナにそろえた。作り直したあとは healthy と起動のログ（S1・S2）を取り直したが、ログイン・一覧と詳細は取り直していない（WAR が同一で、同じデータのボリュームを使うため）。`mastersmith:local` のタグと動いているコンテナが食い違う状態を残さないため。次の配備では、`--force-recreate` を最初から付けるか、`docker inspect` でコンテナのイメージ ID を確かめる。
2. **戻し先のタグ**: `pre-perf-targets-review` は旧イメージ ID を指す。中身は同じなので、戻し先として使える。
3. **FR4.1 と Q5: B**: 取り込む更新が無く中身が変わらなかったが、依頼者の決定（Q1: A）で通常どおり配備した。配備で得た確かめは、再ビルドの WAR が同一であること、入れ替え後の起動、`main` と配備した版のアプリのコードが同じことの確認にとどまる。
4. **Build and Test の証拠ファイルに JWT が入っていた（CI の `gitleaksScan` の失敗）**: 測り直しの k6 の結果 JSON 12 ファイル（`construction/build-and-test/evidence/biz-*.json`）に、使い捨ての環境のアクセストークン（JWT）が各 10 件、計 120 件入っていた。Build and Test の `test-results.md` 3節・`build-and-test-summary.md` の「資格情報 0 件」は、`token` の語だけで探して `eyJ…` の形を探さなかったため誤りだった（承認済みの記録は書き換えず、ここに差を記録する）。トークンは HS256、有効期間 300 秒（期限切れ）、署名鍵は片付け済みの使い捨ての環境の仮の鍵で、`sub` にメールアドレスは無い。手元の `verify` は証拠ファイルをコミットする前に流したため通り、`154c27b`（統合の squash）をプッシュした後の CI（run 40857381092、1 回目）で `jwt` ルール 120 件により失敗した。依頼者の決定（A）で、12 ファイルの JWT を `[REDACTED-JWT]` に置き換えて `154c27b` を amend した（新しいコミットは `805db12`、件名は同じ）。`HEAD` の履歴で `gitleaks git` は検出 0 件。`origin/develop` は旧 `154c27b` のままのため、依頼者の force-push（`--force-with-lease`）が要る。公開の履歴に旧コミットは SHA 指定で残りうるが、トークンは失効済み。
5. **Dependabot**: 開いている PR は #80 のみ。`develop` の push で Dependabot が新しく走り（run 4085739…）、その結果で開いている PR が増えていないかは `main` の取り込みの前に確かめる（4節）。

## 4. `main` への取り込み（CI の確認）

### 4.1 `main` の取り込みの前の CI の確認（試行ごと）

前の取り込み（`94538ef`）の後のすべてのコミットを `gh run view --json attempt,jobs` で確かめた。

| コミット | run | 試行 | 結論 | 検査の job | 備考 |
|---|---|---|---|---|---|
| `ce6228f` | 40603111141 | 1 回目のみ | success | — | 前の Intent の取り込みの結果の記録 |
| `bb7e9ce` | 40699458915 | 1 回目のみ | success | 24 分 1 秒 | 新しい Intent の作成 |
| `ab0d806` | 40699582097 | 1 回目のみ | success | 31 分 56 秒 | Reverse Engineering・Requirements Analysis の記録 |
| `154c27b`（amend で置き換え済み） | 40857381092 | 1 回目 | **failure** | 23 分 | `gitleaksScan` が jwt ルールで 120 件。k6 の結果 JSON の JWT。amend で `805db12` に置き換え、force-push で公開の `develop` から外した |
| `805db12` | 40862040636 | 1 回目 | **failure** | 30 分 36 秒 | `gitleaksScan` が同じ 120 件。CI は全ブランチを取得（`fetch-depth: 0`）し、その時点で Dependabot の #80・#81・#82 のブランチが旧 `154c27b` を持っていたため。ソースの履歴（`HEAD`）は検出 0 件 |
| `805db12` | 40862040636 | 2 回目（依頼者が再実行） | **success** | 31 分 14 秒（11:40:24〜12:11:38Z） | #80 は依頼者が閉じてブランチを削除、#81・#82 は Dependabot が `805db12` に付け替えた後 |

- 1 回目の失敗は、手元で再現できる決まった原因（旧コミットを持つ参照が公開側に残っていた）で、原因を直した後の再実行で通った。不安定なテストではない。`team.md` の「二度目に落ちたら」には当たらない（別の原因ではなく、同じ原因の連続）。
- 検査の job の時間 31 分 14 秒は、CI の上限 `timeout-minutes: 60` の 7 割（約 42 分）の内。
- Dependabot の開いている PR は #81（i18next 26.5.10 → 26.5.11）・#82（react-i18next 17.1.8 → 17.1.9）。どちらも今回の Intent の範囲外で、`team.md` の受け方（手元で版と lockfile を更新して取り込み、PR は閉じる）で後で扱う。#80 は閉じ済み。

### 4.2 取り込みの前の確かめ

- `main`（`94538ef`）は `develop` の祖先で、fast-forward できる。
- WAR を作ったコミットと `develop` の先頭の差は `aidlc/`・`perf/`・`.github/dependabot.yml` だけ。アプリのコードは `main` と同一。

（取り込みの結果は、依頼者の承認のあとに追記する）

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `construction/build-and-test/test-results.md`・`evidence/remeasure-results.md`
- `deployment-execution-questions.md`（Q1: A、Q2: A、まとめの確認 Looks correct）
- 実行したコマンドの出力（`docker inspect`・`docker logs`・`gh run view`・複写の読み取り）

## Assumptions & Open Questions

- [assumption] 新しいイメージ `e0ede6ddd5c4` の中身は旧イメージと同じ（WAR の SHA-256、作成時刻が同じ）。イメージの層の差は調べていない。
