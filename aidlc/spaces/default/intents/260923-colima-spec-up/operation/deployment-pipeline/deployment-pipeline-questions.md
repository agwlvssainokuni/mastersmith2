# Deployment Pipeline — 質問

配備の仕組み（開発者の PC 上のコンテナ、入れ替え方式、手で行う手順、戻し方）は、前の Intent の `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/` と README の「コンテナでの起動と確認」を正とする。ここでは今回決まっていない点だけを聞く。

前提となる事実:

- 今動いているのは版 `3287050`（前の Intent で配備）。コンテナは直す前のイメージ・上限 1g のまま。
- 今回の変更（`e4b10af`）は `Dockerfile`・compose・文書だけで、アプリのコード・スキーマ・依存関係は変わらない。
- `.env` にはすでに `MASTERSMITH_CONTAINER_CPUS` の行がある（値は開いていないため不明。VM が CPU 2 だったので 2 以下のはず）。`MASTERSMITH_CONTAINER_MEMORY` の行は無い。
- 負荷の確かめ（k6 の loginSuccess・loginFailure・refresh、上限 2g・CPU 4）は、Build and Test の段で同じソースから作ったイメージで済んでいる。

## Q1. 配備の前の負荷の確かめ

前の Intent では、配備の前に使い捨ての環境で k6 を流した。今回は Build and Test の段で同じソース（`e4b10af`。その後の変更は記録と `perf/README.md` の文面だけ）から作ったイメージで、上限 2g・CPU 4 の試験がすでに通っている。

A. Build and Test の結果を正とし、配備の前には流さない（配備の後のスモークテストと healthy で確かめる）
B. 配備の前に、`verify` で作り直した WAR のイメージで、loginSuccess と refresh を 60 秒ずつ流し直す
X. Other (please specify)

[Answer]: A

## Q2. `.env` の変更（要件 FR2.2）

この PC の `.env` の `MASTERSMITH_CONTAINER_CPUS` を 4 にし、`MASTERSMITH_CONTAINER_MEMORY=2g` を足す。`.env` は秘密情報を含む。

A. AI が行う。値を表示せずに、`MASTERSMITH_CONTAINER_CPUS` の行を `=4` に置き換え、`MASTERSMITH_CONTAINER_MEMORY=2g` の行を足す。確かめは、この2つのキーの行だけを表示する
B. 依頼者が自分で編集する。AI は `docker compose config` の `cpus`・`mem_limit` の値だけで確かめる
X. Other (please specify)

[Answer]: A

## Q3. 戻し方の第一の手

前の Intent では、設定の値だけの変更だったため「まず `.env` で戻す」を第一の手にした。今回は `Dockerfile`（起動の形）も変わっている。

A. 二段にする。まず `.env` の2行を元に戻す（CPU を元の値、メモリの行を消す＝既定 1g）。作り直しは要らないが F3・F4 が戻る。直らなければ直前の版 `3287050` の WAR とイメージに戻す
B. 直前の版 `3287050` に戻すことだけにする（`.env` の値はそのまま残す。古い compose は `MASTERSMITH_CONTAINER_MEMORY` を読まないため 1g になる）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答のまとめ:

- Q1: 配備の前には負荷の試験を流し直さない。Build and Test の段の結果（`e4b10af` のソースのイメージ、上限 2g・CPU 4 でログインの p95 940・926 ms、refresh で停止なし）を正とし、配備の後は healthy とスモークテストで確かめる（A）
- Q2: `.env` の変更は AI が行う。値を表示せずに `MASTERSMITH_CONTAINER_CPUS` の行を `=4` に置き換え、`MASTERSMITH_CONTAINER_MEMORY=2g` の行を足す。確かめは、この2つのキーの行と `docker compose config` の値だけを表示する（A）
- Q3: 戻し方は二段にする。第一の手は `.env` を元に戻す（作り直し不要。F3・F4 は戻る）。直らなければ直前の版 `3287050` の WAR とイメージに戻す（A）

回答から置く前提（成果物に「前提」として書く）:

- `.env` を元に戻せるように、変更の前に `.env` をリポジトリの外（`/tmp` ではなく、利用者のホームの下など）に複写しておく。中身は表示しない。戻すときはその複写を戻す
- 配備の手順は前の Intent の `deployment-strategy.md` と README の「コンテナでの起動と確認」に沿う（未コミットの確認 → ハッシュを控える → `verify` → ボリュームの複写 → `.env` の変更 → `docker compose up -d --build` → healthy → スモークテスト）
- 配備の後に、`docker inspect` で上限（メモリ 2GiB・CPU 4）と、起動の形（PID 1 が java）を確かめる
- colima の VM は Build and Test の段で CPU 4・6GiB にしたまま残す。戻しの手順では VM は戻さない（VM を戻すときは `.env` の CPU を 2 以下にする必要がある旨を runbook に書く）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
