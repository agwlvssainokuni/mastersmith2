# 性能の試験の手順（Intent 261027-perf-targets-review）

この Intent の中心。Not Met だった場面だけを、新しい線で測り直す（要件 FR2）。手順は `perf/README.md` の「業務データの場面」「dslMixed の memory.peak と memory.stat の記録」「性能・資源の目標の見直し（Intent 261027-perf-targets-review）」に従う。

## 範囲と線

| 対象 | 場面・指標 | 新しい線（緩和） | 前の線 |
|---|---|---|---|
| MySQL・MariaDB | `bizSearchContains` の p95 | 3000 ms 未満 | 1000 ms 未満 |
| MySQL・MariaDB | `bizSearchLast`・`bizSearchLastSorted` の p95 | 2000 ms 未満 | 1000 ms 未満 |
| アプリ | `dslMixed` の `memory.peak` ÷ 2g | 0.95 未満 | 0.90 未満 |
| 対象DB（MySQL・MariaDB） | `OOMKilled` false・`oom_kill` 0・`anon` の最大 ÷ `memory.max` | 0.92 以下（新設の条件） | 85% は超えたら諮る参考の線 |

## 測り方（FR2.5）

- 合否の回は 2 回、悪い側の値で判定する。`dslMixed` は 2 回の差が 0.01 以上なら 3 回目を足し、3 回の最悪値で判定する。
- 読み取りを先に流し、書き込みの場面は流さない。種と表は前回と同じ規模・状態（種を入れた後に目印の語の当たりと最長の文字数を数えてから、表の大きさを測る）。
- 台本の全体を `caffeinate -dims` で包み、`pmset -g log` で眠りが入った回は使わない。
- 測る前にイメージの中の WAR の SHA-256 を記録する。
- 判定は `iteration_duration{scenario:<名前>}`（単調な時計）。`http_req_duration` は並べて記録するだけ。

## 記録

`evidence/remeasure-results.md` に、条件・場面×DB×回の p95・新旧の線の Met/Not Met・前回の値との比較を書く。`test-results.md` の Target Verification Matrix にも写す。Not Met は緩め直さず、持ち越し先を依頼者に諮る。
