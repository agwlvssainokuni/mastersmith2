# 要件（261027-perf-targets-review）

依頼: 前の Intent 261025-deps-perf-carryover で Not Met だった性能・資源の目標を見直す（読み取りの p95: bizSearchContains は 3 秒未満・bizSearchLast と bizSearchLastSorted は 2 秒未満、dslMixed の memory.peak は 95% 未満、対象DB の anon の最大は 92% 以下。anon 85% の物差しは変えない）。k6 の合否の線と README を直して測り直し、前の物差しの値を並べて記録する。あわせて Dependabot の開いている PR（#80 ほか）を洗い出して取り込むか閉じる。 [desc]
範囲: bugfix（深さ Minimal、Test Strategy Minimal） [scope]

## Intent の分析

前の Intent で、読み取りの p95 の 4 件、`dslMixed` の `memory.peak`、対象DB（MySQL・MariaDB）の `anon` の最大が Not Met のまま持ち越された。依頼者は 2026-10-28 に、これらの目標の**緩和**を決めた [desc]。この Intent は、その緩和を要件として明示して記録し、台本と README の線を直して測り直し、Dependabot の知らせを片付ける。

- **目的1（緩和の記録）**: 緩めた線と、変えない線を区別して書く。緩和の理由は、部分一致が索引を使えず表の全部を読むこと、`memory.peak` に回収できるページキャッシュが含まれること [desc]。「緩和」と明記し、Not Met を黙って Met にしない。
- **目的2（測り直し）**: 新しい線で Not Met だった場面だけを測り直し、前の線での値を並べて記録する [Q2]。
- **目的3（Dependabot）**: 開いている PR を洗い出し、手元で取り込むか見送って閉じる [Q4]。
- **目的4（配備）**: Dependabot の取り込みでアプリの中身が変わりうるため、通常どおり配備する [Q5]。

利用者から見える画面と API の形は変えない。変えるのは `perf/`（台本と README と測定の手順）と記録、依存の版と lockfile、`.github/dependabot.yml` の `ignore`。

## 機能の要件

### FR1 目標の緩和の記録（部品 `perf-and-monitoring`）

- **FR1.1** 読み取りの p95 を次のとおり緩める。`bizSearchContains` は `p(95)` 3 秒未満、`bizSearchLast` と `bizSearchLastSorted` は 2 秒未満。それ以外の業務データの読み取りの場面は 1 秒未満のまま [desc]。理由: 部分一致（`bizSearchContains`）は索引を使えず、1要求で件数とページの2回、表の全部を読む。深い OFFSET（`bizSearchLast`・`bizSearchLastSorted`）は読み飛ばす行が多く、並べ替えの列に索引が無い `bizSearchLastSorted` は表の全部を並べ替える。どちらも作りの上の重さで、読み取りのプール 10 でも p95 が 1 秒を超えた [desc]。前回（261025-deps-perf-carryover）の最終の値（プール 10、2回）は、MariaDB の `bizSearchContains` 2,443.0・2,508.3 ms、`bizSearchLast` 1,191.2・1,191.3 ms、`bizSearchLastSorted` 1,549.6・1,535.3 ms、MySQL の `bizSearchContains` 1,786.8・1,848.0 ms で、新しい線の内に入る見込み。余裕は `bizSearchContains` で約 0.5 秒（MariaDB）。
- **FR1.2** `dslMixed` の `memory.peak` ÷ 2g の線を 90% 未満から 95% 未満に緩める。`anon` 85% の物差しは変えない [desc]。
- **FR1.3** 対象DB（MySQL・MariaDB）の `anon` の最大の線を 92% 以下とする。合格の条件は「`OOMKilled` が false・`oom_kill` が 0・`anon` の最大が 92% 以下」とし、`perf/README.md` に書いて、測定の手順の出力に値を並べて判定できるようにする [Q3]。前の線は `anon` 85% で、前回は MariaDB 87.7%・MySQL 91.3%（その後の測り直しで 85.01%・90.3%）が 85% を超えたが、合格の条件が「`OOMKilled` が false・`oom_kill` が 0」だったため Met としていた。今回はその条件に 92% 以下を足す（`anon` の 92% は新しい数値の条件で、85% の物差しは `dslMixed` に残す）。**分母**は、測るときに起動した対象DB コンテナの `memory.max`（`docker inspect` と cgroup で読み、値を記録に書く）とする。compose の既定（512〜768m）と README の運用値（MySQL 2688m など）が違うときは、README の運用値で起動して測り、その値を記録に書く。
- **FR1.4** 緩めた p95 の線は DB の種類を区別せず、場面の名前で 3 種類の対象DB（MySQL・MariaDB・PostgreSQL）に同じに当てる [Q1]。`perf/k6/scenarios.js` の `thresholdsFor` を直す。
- **FR1.5** 緩和の記録: 前の線（p95 1 秒・`dslMixed` の `memory.peak` 90% 未満・対象DB の `anon` は 85% の参考の線と「OOM なし」の合格の条件）と新しい線を、線ごとの理由（FR1.1・FR1.2・FR1.3）とともに `perf/README.md` と Build and Test の成果物に並べて書く。変えた後も、少なくとも1回は前の線での値を並べて記録する（`team.md`）。`dslMixed` の `memory.peak` の理由は、回収できるページキャッシュ（回収できる `file`・`inactive_file`）が peak に含まれ、止まるかどうかは `anon` で決まること。前回の `dslMixed` は 0.9099・0.8774・0.9010（90% 線）と、別の測り直しでの 94.4%・93.1%（内訳は `anon` 68.3%・回収できるページキャッシュ 23.7%）。

### FR2 測り直し（部品 `perf-and-monitoring`）

- **FR2.1** 測り直す場面は、Not Met だった場面だけ: MySQL・MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`、`dslMixed`、対象DB の `anon` の最大 [Q2]。ほかの場面と PostgreSQL は流さず、前回の結果を使う。
- **FR2.5（測り方）** 合否の回は 2 回流し、悪い側の値で判定する（1回でも線を超えたら Not Met）。`dslMixed` の `memory.peak` は前回 94.4%・93.1% で 95% の線との余裕が小さいため、2 回とも並べて記録し、揺れが大きい（回の差が 1 ポイント以上）ときは 3 回目を足す。読み取りの場面は書き込みの場面より先に流し、書き込みの場面は流さない（書き込みの後の読み取りは悪い側へ寄るため。`project.md`）。表の大きさ・種は前回と同じ状態（`perf/README.md` の手順どおり、種を入れた後に目印の語の当たりと最長の文字数を数えてから流す）にし、PC の眠りが入った回は使わず流し直す。
- **FR2.6（線の確かめ）** `thresholdsFor` を直した後、k6 の台本から場面ごとの線の表を `k6 inspect`（`--include-system-env-vars` 付き）で出し、`bizSearchContains` が 3000、`bizSearchLast`・`bizSearchLastSorted` が 2000、それ以外の業務データの読み取りの場面（`bizSearchFirst`・`bizDetail` など）が 1000 のままであること、場面名の取り違えが無いことを照らして記録する。
- **FR2.2** 各場面の結果を、新しい線と前の線の両方で Met / Not Met として並べて記録する。新しい線で Met でも、前の線で Not Met だった事実は消さない。
- **FR2.3** 新しい線でも Not Met の件は、目標を緩め直さずに Not Met のまま記録し、持ち越し先を依頼者に諮る。Not Met が残っても、配備と `main` への取り込みは進めてよい（持ち越し先は依頼者が決める）。
- **FR2.4** 測り直しは配備した使い捨てでない環境のアプリを止めて流す。持ち主は Build and Test（この Intent の流れに Performance Validation の段が無いため）。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。

### FR3 Dependabot の知らせ（部品 `build-and-verify`）

- **FR3.1** 開いている PR を `gh` で全件洗い出す。この段の時点で開いているのは #80（`otel/opentelemetry-collector` 0.163.2 → 0.163.2-386）の 1 件 [Q4]。Build and Test の前にも再確認し、増えていれば同じ受け方で扱う。
- **FR3.2** 取り込みは `team.md` の受け方に従う。GitHub の画面でマージせず、手元で版と lockfile（イメージは版のタグと index のダイジェスト、Dependabot が見ない固定先も同じ）を更新し、`./gradlew verify` を通してから `develop` に統合し、PR は閉じる。重大度 High 以上は取り込む。
- **FR3.3** 見送る更新（major の版、`verify` や E2E が通らないもの、悪意のあるパッケージの疑い）は、`.github/dependabot.yml` の `ignore` に理由と外す時期をコメントで書き、PR は理由を書いて閉じる。
- **FR3.4** #80 の `-386` のタグは、32 ビット向けの別のタグの可能性がある（前に #21 の `0.161.0-386` で同じことがあった）。取り込む前にタグの中身を確かめ、32 ビット向けなら見送って `ignore` に理由を書く [assumption]。

### FR4 配備

- **FR4.1** 通常どおり配備する [Q5]。WAR とイメージを作り直して入れ替え、起動確認とスモークテストを行い、戻し方（直前の版のイメージでの再配備）を用意する。見送りの結果、取り込む更新が無く WAR・イメージが変わらない（#80 を見送り、`backend/src/main` も変えない）ときは、配備は行わず、配備の段に「アプリの変更が無いため不要」と記録して進める。その判断は Build and Test の終わりに、WAR の SHA-256 とイメージの有無で確かめて依頼者に伝える。
- **FR4.2** `main` への取り込みは、依頼者の承認を得て `develop` から fast-forward で行う。`git push` は依頼者が行う。

## 非機能の要件

- **NFR1 目標の扱い**: 緩和は FR1 に限る。FR1 に書いていない目標（それ以外の p95 の線、`dslMixed` の `anon` 85%、カバレッジの下限 行 80%・分岐 70%）は変えない。物差しを変えるのは、要件定義で依頼者が明示に決めたときだけ（`team.md`）。この Intent の緩和はその決定に当たる。
- **NFR2 記録の正確さ**: 測っていない値を Met と書かない。測っていないものは前回の結果を使うと明記する。
- **NFR3 安全**: 依存の脆弱性は、`./gradlew verify` の OSV-Scanner で High 以上が出ない状態で統合する。パスワード・トークン・業務データの行の値・対象DB の接続情報を、ログ・監査・トレースの属性・エラー応答・記録に含めない。
- **NFR4 品質の関門**: 統合の前に `./gradlew verify` が通る。画面や認証・権限・業務データの `src/main` に触れるブランチは E2E も統合の前に通す。台本・README・文書・依存の版と lockfile だけのブランチは `verify` だけで統合し、Build and Test とリリースの前に E2E を1回流す（`team.md`）。カバレッジの測り方は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify`。
- **NFR5 互換と戻し**: 画面の機能と API の応答の形、DB スキーマは変えない。戻しは直前の版のイメージでの再配備。

## 制約

- 配備先は開発者の PC 上のコンテナの範囲。実在の宛先へメールを送らない。
- `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` の中身は直接変更しない。
- `git push` は依頼者が行う。コミットは提案して承認を得てから積む。
- 長いテストは `caffeinate -dims` で包む。内部DB の複写は 600 の権限でリポジトリの外に置く。

## 前提

- **A1** 部分一致の検索が索引を使えず表の全部を読むこと、`memory.peak` に回収できるページキャッシュが含まれることは、前の Intent の記録による。今回は再測定の前に再確認しない [assumption]。
- **A2** 新しい線で Met になる見込みは高いが、測るまで Met とは言えない。MySQL の `bizSearchContains` は表の大きさとバッファープールの兼ね合いで揺れうる（`project.md` の記録）[assumption]。
- **A3** アプリのコード（`backend/src/main`）は変えない見込み。変える必要が出たら、依頼者に諮る [assumption]。
- **A4** Q5 の配備は、Dependabot の取り込みでアプリの中身が変わる前提で通常どおり行う。変わらなければ FR4.1 のとおり諮る [assumption]。

## 範囲の外

- 部分一致の件数を「多数」「N 件以上」と出す変更、索引の追加、読み取りのプール以外の性能の作り直し。
- 他の目標（FR1 に挙げていないもの）の緩和。
- PostgreSQL と、Not Met でなかった場面の測り直し（前回の結果を使う）。
- 第3の束（他テーブル参照、`users.admin_flag` を縮める移行）と、配備先の決定を待つもの。

## 未解決の点

- 新しい線でも Not Met が残る件の持ち越し先（測り直しの結果で決まる。FR2.3。配備と取り込みは進めてよい）。
- #80 の `-386` のタグの扱い（FR3.4。確かめて決める。見送ると FR4.1 のとおり配備は行わない見込み）。
- 対象DB の資源の上限の値（FR1.3 で、測るときの `memory.max` を分母として記録すると決めた。README の運用値の最新の値はコード生成の計画で `perf/README.md` から読んで特定する）。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261027-perf-targets-review/project-description.json` [desc]
- 範囲: `aidlc/spaces/default/intents/261027-perf-targets-review/aidlc-state.md` の Scope（bugfix） [scope]
- 質問と答え: `aidlc/spaces/default/intents/261027-perf-targets-review/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q5]（Q1: A、Q2: A、Q3: A、Q4: A、Q5: B）
- 前の Intent の Not Met: `aidlc/spaces/default/intents/261025-deps-perf-carryover/` の Build and Test の記録と `aidlc/spaces/default/memory/project.md` の記録
- Dependabot の知らせ: `gh pr list`（この段で確かめた、#80 の 1 件）
- 前回の測定値: `aidlc/spaces/default/intents/261025-deps-perf-carryover/construction/build-and-test/build-and-test-summary.md`（U1-NFR1.1・NFR1.3・NFR3.2・NFR2.4、FR4.5-PEAK）と `aidlc/spaces/default/memory/project.md` の記録（`dslMixed` 94.4%・93.1%、対象DB の `anon` 85.01%・90.3%） [desc]
- レビュー: `aidlc/spaces/default/intents/261027-perf-targets-review/.aidlc-reviews/requirements-analysis/stage/e46fd63b88a6145e/1.json`（R-01〜R-06 を反映）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の A1〜A4）。A1・A3 はコード生成で、A2 は Build and Test の測り直しで、A4 は配備の段で確かめる。
- 未解決の点は「未解決の点」の節のとおり（残る Not Met の持ち越し、#80 のタグ、資源の上限の基準）。
