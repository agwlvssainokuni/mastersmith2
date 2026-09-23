# Deployment Execution — Questions

配備の前の確認として、この PC の実行環境を読み取りだけで調べた（2026-09-23 15:06 JST）。

| 確認 | 結果 |
|---|---|
| 作業ツリー | アプリのソースに未コミットの変更は無い。`git status` に出るのは、この段の記録のディレクトリ（`aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-execution/`）だけ。`HEAD` は `3287050`（修正は `d948544`、その後は記録のコミットだけ） |
| 検査 | Build and Test の段で `./gradlew verify` がすべて通った（`d948544`）。配備の手順 3 で、`HEAD` でもう一度実行する |
| DB のスキーマの変更 | 無い（V1〜V4 のまま）。スキーマの変更の実行は要らない |
| 依存するサービス | 無い（内部DBは組み込みの H2）。手元の監視 `lgtm` は止まっている |
| Docker（colima） | 動いている。VM は CPU 2、メモリ 2GiB |
| 動いているアプリ | `mastersmith-app-1`（イメージ `mastersmith:local`、前回配備した版 `7040876`）が healthy。CPU の上限 2（`.env` の `MASTERSMITH_CONTAINER_CPUS` で設定済み）、メモリの上限 1GB |
| `.env` | ある。`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の行は無い（既定の 30 が効く）。値は表示していない |
| 負荷の確かめの道具 | k6 のイメージ `grafana/k6:2.3.0`、`htpasswd`、`openssl` がある。使い捨ての環境のボリュームは残っていない |
| バックアップ | ルートに前回までのバックアップ `mastersmith-data-*.tgz` が3つある（中身は読んでいない） |

VM のメモリが 2GiB のため、配備したアプリ（上限 1GB）と使い捨ての環境のアプリ（上限 1GB）を同時には動かせない。負荷の確かめのあいだは、配備したアプリを止める必要がある。

### Q1. 配備をいつ、誰が行いますか？

配備の手順（`aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/deployment-strategy.md`）は、検査、負荷の確かめ、バックアップ、入れ替え、スモークテストの順である。負荷の確かめのあいだ（10〜15 分の見込み）は、いま動いているアプリ（`7040876`）が止まる。

- A. いまから、依頼者の承認のもとで AI がコマンドを実行する。パスワードが要る操作（初期管理者でのログインなどのスモークテスト）だけ依頼者が行い、AI は監査イベントとログで裏付ける
- B. 依頼者が自分で手順を実行し、AI は結果の記録だけを行う
- C. いまは行わない（別の時間に行う）
- X. Other (please specify)

[Answer]: A. いまから、依頼者の承認のもとで AI がコマンドを実行する。パスワードが要る操作（初期管理者でのログインなどのスモークテスト）だけ依頼者が行い、AI は監査イベントとログで裏付ける

### Q2. 配備の後、監査イベントを確かめるためにアプリを一度止めてもよいですか？

スモークテストの「監査イベント」の項目は、アプリを止めてボリュームを複写し、複写したファイルを読み取りで開いて確かめる（前の Intent と同じ方法）。止めている時間は1分ほどである。

- A. 止めてよい。止めて確かめた後、起動し直して healthy を確かめる
- B. 止めない。監査イベントの確認は、負荷の確かめ（使い捨ての環境）の結果で代える
- X. Other (please specify)

[Answer]: A. 止めてよい。止めて確かめた後、起動し直して healthy を確かめる

## Consolidated Summary Confirmation

回答をまとめると、次のとおりです。

- 配備は、いまから、依頼者の承認のもとで AI がコマンドを実行する。手順は `deployment-strategy.md` の 1〜9 のとおり: 作業ツリーの確認とハッシュの控え、`./gradlew verify`、使い捨ての環境での k6（同時 10 件のログインを 60 秒）、バックアップ、`docker compose up -d --build`、healthy、スモークテスト（Q1: A）
- 負荷の確かめのあいだは、いま動いているアプリ（`7040876`）を止める。確かめが通らなければ配備せず、止めていたアプリを起動し直す
- パスワードが要るスモークテスト（ログイン・管理者向け領域・ログアウト）は依頼者が行い、AI は監査イベントとログで裏付ける。個人に関する値は表示しない（Q1: A）
- 監査イベントの確認のために、配備の後にアプリを1分ほど止めてボリュームを複写し、読み取りで確かめる。その後、起動し直して healthy を確かめる（Q2: A）
- 作業ツリーの確認では、この段の記録のディレクトリ（`operation/deployment-execution/`）は対象から外す（アプリのソースに未コミットの変更が無いことを確かめる）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
