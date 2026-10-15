# コード生成のまとめ（261015-deps-ui-carryover）

> この文書は、ブランチごとに書き足していく。今はブランチ ①（依存の更新、Step 1〜11）の分だけを書いている。ブランチ ②・③ と Step 45 の追跡（FR12.1 の一覧の結果など）は、それぞれのブランチで書き足す。

## 1. ブランチ ①: 版とダイジェストの確かめ（Step 2）

確かめた日: 2026-10-15。読み取りだけで確かめた（`docker buildx imagetools inspect`、Docker Hub のタグの一覧、`git ls-remote`、`gh pr diff`）。

### 1.1 イメージ

| イメージ | 前の版・ダイジェスト（先頭 12 文字） | 新しい版 | 新しい index のダイジェスト | linux/arm64・linux/amd64 |
|---|---|---|---|---|
| `eclipse-temurin`（jre） | `25.0.4_7-jre-noble`・`b573af9e3311` | `25.0.4.1_1-jre-noble` | `sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab` | あり（arm64 は `linux/arm64/v8`） |
| `eclipse-temurin`（jdk、FR1.2a） | `25.0.4_7-jdk-noble`・`2feab631bffc` | `25.0.4.1_1-jdk-noble` | `sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4` | あり（arm64 は `linux/arm64/v8`） |
| `grafana/otel-lgtm` | `0.34.0`・`b966ea107831` | `0.36.0` | `sha256:57c04cbd8e7022f32bb84c4ac005d79baf8b08e2c5be44facf11f5f1f9944ba2` | あり |
| `axllent/mailpit` | `v1.31.2`・`74d609a42ec2` | `v1.31.5` | `sha256:94f6b70c1436defae6799d9ac7ff0eeff9540465b842026d5cdb018f1c0234ea` | あり |
| `grafana/k6`（FR1.2c） | `2.3.0`・`9c2dee7f8ed7` | `2.3.0`（据え置き） | `sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34`（変わらない） | あり |

- FR1.2a（前提 A1）: jdk の同じ版のタグ `25.0.4.1_1-jdk-noble` があったため、jdk の固定先（`docker/hikari-pool.sh`・`perf/README.md`・`README.md`）も jre と同じ版にそろえる。据え置きは無い。
- FR1.2c: Docker Hub の `grafana/k6` のタグの一覧（2026-10-15 の時点）で、2 系の最新は `2.3.0`（2026-09-21 公開）で今の固定先と同じだった。`1.8.2`（2026-10-12 公開）は 1 系の版で、新しい版ではない。版とダイジェストは変えない。

### 1.2 GitHub Actions

| アクション | 前のハッシュ・版 | 新しい版 | タグが指すコミット（`git ls-remote`） | プルリクエストのハッシュとの一致 |
|---|---|---|---|---|
| `actions/setup-node` | `820762786026…`・v7.0.0 | v7.1.0 | `949feb2413d6458794dcd2491c4babbbce0c15c1` | 一致（#43） |
| `gradle/actions/setup-gradle` | `9c971963bec3…`・v6.3.0 | v6.4.0 | `3f5f9adaf7d9fecd50b5935e54106014257a94e6`（注釈つきのタグ `b9bee63e…` の先） | 一致（#25） |
| `actions/upload-artifact` | `043fb46d1a93…`・v7.0.1 | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | 一致（#40） |
| `actions/download-artifact` | `3e5f45b2cfb9…`・v8.0.1 | v8.0.2 | `9000827ccba6bdab643e8b6fd33ac0654aef8333` | 一致（#44） |

### 1.3 Dependabot のプルリクエスト（20 件、2026-10-15 の時点で開いているもの）

| # | 対象 | 前 → 後 |
|---|---|---|
| 49 | `@vitejs/plugin-react` | 6.1.1 → 6.1.2 |
| 48 | `@typescript-eslint/parser` | 8.70.1 → 8.71.1 |
| 47 | `com.diffplug.spotless` | 8.10.3 → 8.10.4 |
| 46 | gradle-wrapper | 9.8.0 → 9.8.1 |
| 45 | `oxlint` | 1.85.0 → 1.87.0 |
| 44 | `actions/download-artifact` | 8.0.1 → 8.0.2 |
| 43 | `actions/setup-node` | 7.0.0 → 7.1.0 |
| 42 | `@types/node` | 26.6.3 → 26.6.5 |
| 41 | `vite` | 8.3.1 → 8.3.4 |
| 40 | `actions/upload-artifact` | 7.0.1 → 7.0.2 |
| 39 | `axe-core` | 4.13.0 → 4.14.0 |
| 38 | `eclipse-temurin` | 25.0.4_7-jre-noble → 25.0.4.1_1-jre-noble |
| 37 | `net.jqwik:jqwik` | 1.10.1 → 1.10.2 |
| 36 | `grafana/otel-lgtm` | 0.34.0 → 0.36.0 |
| 35 | `axllent/mailpit` | v1.31.2 → v1.31.5 |
| 34 | `stylelint` | 17.15.0 → 17.16.0 |
| 32 | `eslint` | 10.11.0 → 10.12.0 |
| 27 | `@vitest/coverage-v8` | 5.0.2 → 5.0.3 |
| 25 | `gradle/actions/setup-gradle` | 6.3.0 → 6.4.0 |
| 24 | `vitest` | 5.0.2 → 5.0.3 |

## 2. ブランチ ①: 取り込んだ更新と見送った更新（Step 10 の時点）

- 取り込む: 19 件（#49・#48・#47・#46・#45・#44・#43・#42・#41・#40・#38・#37・#36・#35・#34・#32・#27・#25・#24）。あわせて Dependabot が見ない固定先（jre・jdk・otel-lgtm・mailpit）をそろえた。
- 見送る: 1 件（#39 `axe-core` 4.13.0 → 4.14.0）。4.14.0 で axe の規則 `label-content-name-mismatch`（WCAG 2.5.3）が既定で流れるようになり、`./gradlew e2eTest` で 100 件が落ちた（080・100・120・130・160。make-you-chic-ui の Dropdown のトリガーと作業ロールのメニューの項目）。脆弱性の直しを含まないため FR1.3 のとおり見送り、`.github/dependabot.yml` の `ignore` に理由と外す時期を書いた。詳しくは `generation-notes.md` の Step 10。
- プルリクエストの閉じ方（Step 12、D-12: A）は、依頼者の push の後に行う。

## 3. ブランチ ①: 確かめの結果（最後の状態）

| 確かめ | 結果 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 21:51〜22:06） | 成功。backend 単体 2,102・結合 1,165（失敗 0・SKIPPED 0）、frontend 1,590。backend 行 97.9%・分岐 92.5%、frontend 行 95.42%・分岐 90.25%。OSV-Scanner 失敗の条件 0・警告 18 |
| `./gradlew e2eTest`（axe-core を戻した後、21:40〜21:51） | 230 passed・失敗 0 |
| 新しい jre のイメージの起動（Step 9） | 使い捨てのコンテナで約 10 秒で `/actuator/health` 200、Java 25.0.4.1+1、ERROR 0 |
| スリープ（`pmset -g log`） | verify・E2E の間に Sleep・DarkWake 無し |
