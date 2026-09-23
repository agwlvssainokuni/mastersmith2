# ヘルスチェックの報告（health-check-report）

配備した版 `10742a3`（F3・F4 の修正）の健全性の確かめ。2026-09-23 17:12〜17:25 JST。

## 1. 健全性と上限

| 確かめ | 結果 | 判定 |
|---|---|---|
| ヘルスチェック（`docker compose ps`、`/actuator/health`） | 配備の後 約 10 秒で `healthy`、`{"status":"UP"}`。監査の確認のための停止（2回）の後も `healthy`・`UP` | 合格 |
| メモリの上限（`docker inspect` の `HostConfig.Memory`） | 2147483648（2GiB）。要件 FR2.2 の値 | 合格 |
| CPU の上限（`HostConfig.NanoCpus`） | 4000000000（CPU 4）。要件 FR2.2 の値 | 合格 |
| 起動の形（`/proc/1/cmdline`） | PID 1 が `java -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Tokyo -jar /app/mastersmith.war`。`MASTERSMITH_JAVA_OPTIONS` は設定していないため既定の引数だけ | 合格 |
| OOMKilled | false | 合格 |
| ログの形 | 1行1件の JSON（JSON でない行 0 件）、ERROR 0 件 | 合格 |

## 2. 性能の目標との関係

- 配備した環境には負荷をかけていない（配備の前・後の k6 は行わない。Deployment Pipeline の Q1: A）。
- 性能の目標（ログインの p95 1 秒以内、高い負荷で止まらない）は、Build and Test の段で、同じソース（`e4b10af`）から作ったイメージを配備と同じ上限（2g・CPU 4）で試験して満たした（`aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md` 4章。ログインの p95 は成功 940 ms・失敗 926 ms、refresh 60 秒×2回で停止なし）。
- 配備したアプリのメモリは、スモークテストの後の待機の状態で 約 390MiB / 2GiB だった（`docker stats`）。

## 3. 未確認の前提（戻しの練習を行わなかったため）

依頼者の決定（Q3: A）により、戻しの練習は行っていない。

| 残る未確認の前提 | 次に確かめる機会 |
|---|---|
| 第一の手（`~/.mastersmith-env-backup/env-202609231711` を戻して `docker compose up -d app`）で、上限 1g・CPU 2 に戻ること | `.env` の上限をもう一度変える配備のとき。なお、Build and Test の段で、上限 1g のコンテナが同じイメージで起動することは確かめた（`test-results.md` 4章の pre1g の回） |
| 第二の手（直前の版 `3287050` を取り出した場所で、`.env` のシンボリックリンクを置いて起動する）が動くこと | `Dockerfile` や compose を変える次の配備のとき |
| 直前の版が、新しい版が当てたスキーマの変更を含む DB で起動できること（前の Intent から残る） | スキーマの変更がある版を配備するとき |

## 4. 片付け・残したもの

- 残したもの（どれも Git 管理外）:
  - 内部DBのバックアップ `mastersmith-data-202609231711-before-deploy.tgz`・`mastersmith-data-202609231616-before-vm.tgz`
  - `.env` の複写 `~/.mastersmith-env-backup/env-202609231711`
  - 確かめ用のイメージ `mastersmith:pre-fix`（直前の版のイメージでもある）
- 消したもの: 監査の確認のための DB の複写と H2 の JAR（`~/.mastersmith-audit-check`）、負荷の試験の使い捨ての環境（Build and Test の段で `down -v`）。

## Sources

- `deployment-log.md`、`smoke-test-results.md`
- `docker compose ps`、`docker inspect`、`docker stats`、`curl` の出力
- `aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`

## Assumptions & Open Questions

- 未確認の前提は3節の表のとおり。
