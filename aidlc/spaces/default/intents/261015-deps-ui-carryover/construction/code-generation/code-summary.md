# コード生成のまとめ（261015-deps-ui-carryover）

> この文書は、ブランチごとに書き足していく。今はブランチ ①（依存の更新、Step 1〜11）と ②（画面・コードの小さな直し、Step 13〜29）の分を書いている。ブランチ ②・③ と Step 45 の追跡（FR12.1 の一覧の結果など）は、それぞれのブランチで書き足す。

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

## 4. ブランチ ②: make-you-chic-ui の固定先の更新（Step 15）

| 項目 | 値 |
|---|---|
| 更新の前 | `5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6` |
| 更新の後 | `a9309c4ee863b162236571a7d6300c62fd4b4f70` |
| 中身の変更 | なし（`git -C vendor/make-you-chic-ui checkout` だけ。`status --porcelain` は空） |
| package の版の定義と lockfile | 変わらない |
| 先に満たした型の差 | `SidebarLabels.containsCurrent`（必須）を、Step 14 で注記の無い `navLabels` に足した |

## 5. ブランチ ②: FR12.1 の一覧の各指摘の結果

| 束 | 指摘 | 結果 |
|---|---|---|
| K2 | dsl-v2 R-04（360px・表の名前の重なり） | 直した。060・120 の E2E に 360px の悪い側の組（dark・lg・blue）を足し、横のはみ出しなしと行の操作のメニューの収まりが通った。名前の重なりは D-8: A で直さず記録だけ（`DslErrorList`・`TransferErrorList`・`TransferCheckTable` の領域と表の名前は役目が違い、axe の違反は無い） |
| K2 | dsl-v2 R-05（`DslError` の文字列） | 直した。`DslSecretLeakIT` が直す前に2件とも落ち（`DslReadResult.Invalid` の `DslError`、`ProblemDetail` の `DslErrorItem`）、直した後に通った（D-11: A） |
| K2 | dsl-v2 R-07（メニューを比べない） | 直した（D-6: A）。違いが無いときの文言と、違いの表の上の説明の1文で伝える |
| K2 | dsl-v2 R-08（落とした枝を画面に出さない） | 直した（D-7: A）。状態の応答の `prunedMenuItems` と、1以上のときの `DslStatusPanel` の警告 |
| K3 | navigation R-03（record の文字列） | 直した（D-10: A）。`NavigationSecretLeakIT` が直す前に落ち、直した後に通った。あわせて見つけた dsl の `ActiveDslModelStore#current()` の漏れ道（DSL のモデルを丸ごと TRACE に出す）も、依頼者の決定 A で `DslModel.toString` を伏せて直した（6節の差1） |
| K3 | navigation R-04（`MenuFilter` の Javadoc） | 直した。深さの前提と境界のテストの持ち主を書いた |
| K4 | cross-cutting R-02（共有の木の失敗の文と区画） | 直した。失敗の文を `role="alert"`、区画を `role="group"` と名前（`childArea`）にした |
| K4 | cross-cutting R-03（管理者でない利用者向けの区画） | 直した（D-9: A）。README とコメントに決まりを書き、反する見本6か所を直した |
| K4 | cross-cutting R-05（直書きの下限 34） | 直した。`ApiEndpointCounts.MIN_ENDPOINTS` = 67（今の口の数） |
| K4 | cross-cutting R-06（`useLogout` の README） | 直した。一文を足した |
| K4 | cross-cutting R-07（前の Intent の記録の差） | 記録だけ。前の Intent の確定済みの記録は書き換えない。今の境界テストは 15 ファイル（`*BoundaryArchitectureTest.java`）。前の Intent の `code-summary.md` の 11 と 10 の差、`traceability.json` の NFR6.4・BR1.7 が既存のファイルを指す点は、そのまま残す |

## 6. ブランチ ②: 計画との差

1. **計画に無い変更（依頼者の決定 A）**: `dsl.service.ActiveDslModelStore#current()` の TRACE が、DSL のモデル（`DslModel`）を丸ごと文字列にして表示名を出していた。依頼者の決定で ② で直した。`NavigationSecretLeakIT` の除外を外して直す前に落ちることを確かめ（23:11）、`DslModel.toString` を識別の先頭 12 文字・版・スキーマの数・メニューの数だけにし（`ActiveDsl.Present`・`DslReadResult.Valid`・`DslStartupReadResult.Valid` も伏せられる）、`DslModelToStringTest` を足した。直した後は除外なしで通る。
2. 落とした項目の数の置き場を、新しい部品ではなく既存の `UnreadableAppliedRevision` に足した（作り方を変えずに済むため。名前は役目より狭い）。
3. 共有の木の区画の名前のため、`SharedTreeLabels` に必須の `childArea` を足し、使う2か所の文言を足した。区画は `role="group"`。
4. 違いの見出しの説明は、見出しの文言を変えずに表の上の説明の1文（`dsl.diff.scope`）にした。
5. 画面に深さの上限の定数が無いため、`DslStatusPanel.tsx` に `MENU_DEPTH_LIMIT = 5` を置いた。
6. E2E の状態の見本は `support/adminApiRoute.ts` ではなく `135-dsl-admin-accessibility.e2e.ts` の中にあり、そこを直した。
7. 360px の確かめは、20 組の外の1組 `WORST_360_COMBO`（dark・lg・blue）を `support/displayCombos.ts` に足し、060・120 の組のループに加えた。

## 7. ブランチ ②: 確かめの結果

| 確かめ | 結果 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 22:40〜22:55） | 成功。backend 単体 2,115・結合 1,168（失敗 0・SKIPPED 0）、frontend 1,589。backend 行 97.9%・分岐 92.5%、frontend 行 95.36%・分岐 90.38%。`osvScan` は UP-TO-DATE（lockfile は ① から変わらない） |
| `./gradlew e2eTest`（22:56〜23:07） | 232 passed・失敗 0 |
| 再現のテスト（直す前） | `DslSecretLeakIT` 2件・`NavigationSecretLeakIT` 1件が落ちることを確かめた（22:19・22:21） |
| スリープ（`pmset -g log`） | verify・E2E の間に Sleep・DarkWake 無し |
| `verify` の流し直し（依頼者の決定 A の直しの後、23:13〜23:27） | 成功。backend 単体 2,118・結合 1,168（失敗 0・SKIPPED 0）、frontend 1,589。backend 行 97.9%・分岐 92.5%、frontend 行 95.36%・分岐 90.38%。Sleep・DarkWake 無し。E2E は画面に触れないため流し直していない |
