# 性能の試験の手順（Intent 261015-deps-ui-carryover）

## 方針

- Performance Validation の段が無いため、FR7.2・FR8・FR9.1・FR9.4 の k6 と測りはこの段で流す（`project.md` の学び）。目標は緩めない。測れなかったものは `Unverified`、外れたものは `Not Met`。
- 手順の本体は `perf/README.md` の「性能の測り直し（Intent 261015-deps-ui-carryover の FR7.2・FR8・FR9.1・FR9.4）」の (a)(b)(c)。ここでは流し方の順と合否だけを書く。
- 使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で流し、本物のデータと監査ログを汚さない。試験の間だけ配備したアプリを止める（依頼者の決定 Q1: A）。
- 台本の全体を `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で眠りが無いことを確かめる。眠りが入った回は数えずに流し直す。

## 順と合否

| 順 | 測り | 合否 |
|---|---|---|
| 0 | 台本の事前の確かめ（`zsh -n` と、使い捨ての環境での1回目） | 3つの台本の構文が通り、結果のファイルが埋まる（コード生成のレビューの R-05） |
| 1 | (a) ロールの書き込みの待ちの最大を合否の回 5 回（上限 11・VUS 5・2 分） | 5 回すべてで acquire の MAX の最大が 20 ms 未満なら Met、1回でも 20 ms 以上なら Not Met。時間切れの増分 0・`rolePoolLimit_500` 0 件（FR8.4） |
| 2 | (b) 10 MiB の DSL の投入と、行を分けた悪い形の 5 MiB の権限の YAML の確かめ・適用のヒープ | OutOfMemoryError と JVM の停止が無く、`OOMKilled` が false（FR9.4） |
| 3 | (b) `dslMixed` の `memory.peak` | メモリの上限（変えないときは 2g）の 90% 未満（FR9.4） |
| 4 | (c) ロールの3場面を手順書の順と単独で | どちらでも checks の fails 0、setup が `ROLE_NO_CHANGE` や不一致で止まらない（FR7.2） |
| 5 | 新しい jdk のイメージで `docker/hikari-pool.sh status` | 動く（計画 6節の 7） |

- FR9.3: 2・3 の合否を満たせば、メモリの上限と `MaxRAMPercentage` は変えず、その判断と根拠を記録する。満たさなければ止めて依頼者に諮る。
- FR8.2: 20 ms 以上の回は、その回のログの WARN・ERROR、`jvm.gc.pause` の MAX、`docker stats` の CPU を並べ、事実と仮説を分けて書く。直す形は依頼者に諮る（FR8.3）。
- 結果の記録: `perf-run-notes.md`（この段のディレクトリ）と `build/perf-results/`。
