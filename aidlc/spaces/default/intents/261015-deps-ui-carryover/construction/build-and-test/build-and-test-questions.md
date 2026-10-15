# Build and Test の質問（Intent 261015-deps-ui-carryover）

## 決まっていること（質問にしない）

- **E2E**: 依頼者の決定で、Intent の最後に1回だけ流す。この段で、4つの統合（`3472705`・`60b49e5`・`0b04d3a`・`776738e`）の後の `develop` で `./gradlew e2eTest` を流す。あわせて、ブランチ ④ の書体（`@fontsource/noto-sans-jp` 5.3.1）の読み込みと日本語の表示を確かめる（コード生成のレビューの R-03）。
- **`./gradlew verify`**: `develop` の先頭で、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直し、実測の数だけを記録する（`project.md` の学び）。
- **負荷の試験の持ち主**: Performance Validation の段が無いため、FR7.2・FR8・FR9.1・FR9.4 の k6 と測りはこの段で流す（`project.md` の学び）。目標は緩めない。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。
- **台本の事前の確かめ**: アプリを止める前に、`perf/README.md` の heredoc で作る3つの台本（`heap-run.sh`・`role-pool-5.sh`・`role-three.sh`）の構文と、1回目の結果のファイルが埋まることを、使い捨ての環境だけで確かめる（コード生成のレビューの R-05）。
- **FR9.3（メモリの設定）**: 測りの結果が FR9.4 の線（`dslMixed` の `memory.peak` がメモリの上限の 90% 未満、ヒープが最大ヒープに届かない）を満たせば、メモリの上限と `MaxRAMPercentage` は変えず、その判断と根拠を記録する。満たさなければ、そこで止めて依頼者に諮る。
- **FR8（ロールの書き込みの待ちの最大）**: 合否の回を 5 回流し、5 回すべて 20 ms 未満なら Met、1回でも 20 ms 以上なら Not Met。直すときは依頼者に諮る。
- **新しい jdk のイメージ**: `docker/hikari-pool.sh status` が新しい jdk のイメージで動くことを確かめる（計画 6節の 7）。
- **コード生成のレビューの R-01・R-02・R-04**: この段の記録（`build-and-test-summary.md`・`cross-unit-traceability.md`）で扱う。R-04 は D-10 の A のとおり、下位の record の名前は業務の名前として受け入れ、`DslModel` だけを伏せた範囲を明記する（依頼者が承認の場で受け入れた推奨）。

## 質問

### Q1. 負荷の試験の間、配備したアプリを止めるか

ロールの書き込みの待ちの最大（P1、目標 20 ms 未満）は数 ms の差を見るため、同じ VM で配備したアプリが動いていると結果が揺れやすくなります。VM のメモリは約 6 GiB で、使い捨ての環境（上限 2g）と配備したアプリ（2g）は同時に起動できる見込みです。

A. 試験の間だけ配備したアプリを止め（`docker compose stop`）、終わったら起動し直す。止めた時間を記録する（前の Intent と同じ形）
B. 配備したアプリを止めずに流す（VM の空きを先に確かめ、足りなければ止める）
X. Other (please specify)

[Answer]: A

### Q2. CI の確かめ

ブランチ ③・④ の統合（`0b04d3a`・`776738e`）はまだ push されておらず、CI が流れていません。この段で CI を試行ごとに確かめるには push が要ります（push は依頼者が行う）。

A. 今 push していただき、前の確かめの後のすべてのコミット（`3472705` から `776738e` まで）の CI を、この段で試行ごと（`gh run view --json attempt,jobs`）に確かめる
B. この段では CI を確かめず、push と CI の確かめを配備の段（Deployment Execution の main への取り込みの前）に回す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A — 負荷の試験の間だけ配備したアプリを止め（`docker compose stop`）、終わったら起動し直し、止めた時間を記録する。
- Q2: A — 依頼者が今 push し、`3472705` から `776738e` までの CI をこの段で試行ごとに確かめる。
- 決まっていること: E2E をこの段で1回流し書体の表示も確かめる、verify を clean 付きで流し直す、台本を使い捨ての環境で先に確かめる、FR9.3 は線を満たせば設定を変えない、FR8 は 5 回すべて 20 ms 未満で Met、新しい jdk で hikari-pool.sh を確かめる、R-01・R-02・R-04 はこの段の記録で扱う。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
