# Deployment Execution — Questions

配備の前の確認として、この PC の実行環境を読み取りだけで調べた（2026-09-23 16:50 JST）。

| 確認 | 結果 |
|---|---|
| 作業ツリー | アプリのソースに未コミットの変更は無い。`git status` に出るのは、この段の記録のディレクトリ（`aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-execution/`）だけ。`HEAD` は `10742a3`（修正は `e4b10af`、その後は記録と `perf/README.md` の文面のコミットだけ） |
| 検査 | Build and Test の段で `./gradlew verify` がすべて通った（`e4b10af`）。配備の手順 3 で、`HEAD` でもう一度実行する |
| DB のスキーマの変更 | 無い（V1〜V4 のまま）。スキーマの変更の実行は要らない |
| 依存するサービス | 無い（内部DBは組み込みの H2）。手元の監視 `lgtm` は止まっている |
| Docker（colima） | 動いている。VM は CPU 4、メモリ 6GiB（Build and Test の段で作り直し済み） |
| 動いているアプリ | `mastersmith-app-1`（前回配備した版 `3287050` のイメージ、今は `mastersmith:pre-fix` のタグ）が healthy。上限は CPU 2・メモリ 1GB |
| `.env` | ある。`MASTERSMITH_CONTAINER_CPUS` の行がある（今の上限から値は 2）。`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_JAVA_OPTIONS` の行は無い。値は表示していない |
| `.env` の複写の置き場 | `~/.mastersmith-env-backup/` はまだ無い（手順 5 で作る） |
| バックアップ | ルートに `mastersmith-data-*.tgz` が5つある（直近は VM の作り直しの前の `mastersmith-data-202609231616-before-vm.tgz`。中身は読んでいない） |

### Q1. 配備をいつ、誰が行いますか？

配備の手順（`aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/deployment-strategy.md` 1節の 1〜12）の順は次のとおり。アプリが止まるのは、手順 4 から healthy になるまで（数分の見込み）である。

1. 検査
2. バックアップ（内部DBのボリュームと `.env` の複写）
3. `.env` の2行の変更
4. 入れ替え
5. 上限と起動の形の確認
6. スモークテスト

- A. いまから、依頼者の承認のもとで AI がコマンドを実行する。パスワードが要る操作（初期管理者でのログインなどのスモークテスト）だけ依頼者が行い、AI は監査イベントとログで裏付ける
- B. 依頼者が自分で手順を実行し、AI は結果の記録だけを行う
- C. いまは行わない（別の時間に行う）
- X. Other (please specify)

[Answer]: A. いまから、依頼者の承認のもとで AI がコマンドを実行する。パスワードが要る操作（初期管理者でのログインなどのスモークテスト）だけ依頼者が行い、AI は監査イベントとログで裏付ける

### Q2. 配備の後、監査イベントを確かめるためにアプリを一度止めてもよいですか？

スモークテストの「監査イベント」の項目は、アプリを止めてボリュームを複写し、複写したファイルを読み取りで開いて確かめる（前の Intent と同じ方法）。止めている時間は1分ほどである。

- A. 止めてよい。止めて確かめた後、起動し直して healthy を確かめる
- B. 止めない。監査イベントの確認は行わず、ログの確認で代える
- X. Other (please specify)

[Answer]: A. 止めてよい。止めて確かめた後、起動し直して healthy を確かめる

### Q3. 戻し方の練習をしますか？

`rollback-runbook.md` の戻しの手順（第一の手: `.env` の複写を戻す／第二の手: 直前の版 `3287050` を取り出した場所で起動する）は、まだ動かしていない。

- A. 行わない。未確認の前提と次に確かめる機会を記録に残す
- B. 第一の手だけ練習する（配備の後、`.env` の複写を戻して起動し、1g・CPU 2 に戻ることを確かめ、再び新しい `.env` に戻す。アプリは2回作り直しになる）
- C. 第一・第二の手の両方を練習する（時間がかかる）
- X. Other (please specify)

[Answer]: A. 行わない。未確認の前提と次に確かめる機会を記録に残す

## Consolidated Summary Confirmation

回答をまとめると、次のとおりです。

- 配備は、いまから、依頼者の承認のもとで AI がコマンドを実行する。手順は `deployment-strategy.md` 1節の 1〜12 のとおり（Q1: A）
  - 作業ツリーの確認とハッシュの控え、`./gradlew verify`
  - アプリを止めてボリュームを複写、`.env` をホームの下へ複写
  - `.env` の2行の変更（値は表示しない）、`docker compose config` での確認
  - `docker compose up -d --build`、healthy、`docker inspect` で上限（2GiB・CPU 4）と起動の形（PID 1 が java）
  - スモークテスト
- パスワードが要るスモークテスト（ログイン・管理者向け領域・ログアウト）は依頼者が行い、AI は監査イベントとログで裏付ける。個人に関する値は表示しない（Q1: A）
- 監査イベントの確認のために、配備の後にアプリを1分ほど止めてボリュームを複写し、読み取りで確かめる。その後、起動し直して healthy を確かめる（Q2: A）
- 戻しの練習は行わない。未確認の前提と次に確かめる機会を記録に残す（Q3: A）
- 作業ツリーの確認では、この段の記録のディレクトリ（`operation/deployment-execution/`）と監査ログは対象から外す（アプリのソースに未コミットの変更が無いことを確かめる）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

### Q4. スモークテスト（依頼者の操作）の結果

配備（版 `10742a3`、17:12 に healthy）の後、ブラウザで次を確かめる: 1) ログイン画面が表示される、2) 初期管理者でログインしてホームが表示される、3) 「管理」で管理者向け領域が開ける、4) ログアウトでログイン画面に戻る。

- A. すべて通った
- B. 通らない項目があった（どれかを書く）
- X. Other (please specify)

[Answer]: A. すべて通った（依頼者の回答: 「すべて通った」）
