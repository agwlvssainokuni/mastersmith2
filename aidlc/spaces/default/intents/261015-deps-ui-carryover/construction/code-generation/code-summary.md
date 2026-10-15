# コード生成のまとめ（261015-deps-ui-carryover）

> この文書は、ブランチごとに書き足した。ブランチ ①（依存の更新、Step 1〜11）・②（画面・コードの小さな直し、Step 13〜29）・③（k6 の台本と性能の準備、Step 31〜43）と、Step 45 の全体のまとめ（12節）を書いている。途中の判断の詳しい記録は `generation-notes.md`。

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

## 8. ブランチ ③: 配備済みの DSL の最も長い行（Step 32、D-5: A）

| 項目 | 値 |
|---|---|
| 了承 | 依頼者の了承（「止めてよい」）を得て行った |
| 止めた時間 | 2026-10-15 14:54:02 UTC に停止 → 14:54:07 に起動の命令（5 秒）→ 14:54:14 に `/actuator/health` 200（停止から 12 秒） |
| 測り方 | 内部DB のボリュームを `~/.mastersmith-backup/`（権限 700）へ複写し、H2 の JDBC で `ACCESS_MODE_DATA=r` で開き、各行の `yaml_bytes` の最も長い行の長さ（コードポイント）だけを出した。本文は表示していない。測った後に複写と作業用のディレクトリを消した |
| `dsl_applied_revisions` | 6 行。各行の最も長い行はどれも 58、最大 58 |
| `dsl_previews` | 0 行 |
| 判断 | 上限 65,536 を大きく下回り、配備の起動で読めなくなるおそれは無い。配備の前にもう一度測る（計画 6節の 8） |

## 9. ブランチ ③: 1行の長さの上限と BUSY・k6・手順

- **1行の長さの上限（FR9.2）**: 値 65,536 コードポイント（D-2: A。`DslFormat.MAX_LINE_LENGTH`、権限の YAML も同じ値）。`SafeYamlParser` が UTF-8 の解読の後、読む前に確かめ、超えた最初の行で大きさの種類の誤り（D-3: A。DSL は `SIZE_LIMIT`・文言の鍵 `dsl.limit.line`・位置はその行と 65,537 列、権限の YAML は `TOO_LARGE` → 413 `PAYLOAD_TOO_LARGE`）にする。誤りに本文は入らない。
- **境界のテスト**: 単体（`SafeYamlParserTest`・`SafeYamlParserPropertyTest`・`DefaultDslReaderTest`・`SafeYamlReaderTest`・`RoleTransferReaderTest`・`RoleTransferServiceTest`・`DslLifecycleTest`）と結合（`DslAdminApiIT` の 422 Problem Details、`RoleTransferLimitsApiIT` の 413 Problem Details）で、ちょうどは受け付け、超えると拒否、部品の例外の文と本文が応答に出ないことを確かめた。
- **既定の DSL の生成（D-4: A）**: 表示名の `ja` にするコメントを 4,096 コードポイントで切る（`DslTreeBuilder`）。最悪の逃がしの書き出しでも行は上限の内で、読み直せる（`DslTreeBuilderTest`・`DslYamlWriterTest`）。権限の書き出しも名前の上限の長さで上限の内（`RoleTransferWriterTest`）。
- **大きさの境界を1行で作っていたテストの直し**: `LargeRoleTransferYaml`（埋め草のコメントを 1,000 バイトの行に分けた。負荷の試験の準備も同じ部品を使う）と `RoleTransferReaderTest` の `flowSequence`（要素 1,000 個ごとに改行）。
- **BUSY の結び付き（FR10）**: `RoleBusyLogTraceIT`（`ROLE_BUSY`）と `GroupBusyLogIT`（`GROUP_BUSY`）は前から結び付きを確かめていた。足りなかったロールの割り当ての道の `GROUP_BUSY` を `RoleBusyLogTraceIT` に1件足し、通った（応答の `traceId` の WARN がちょうど1行、`lockKind` `GROUP_ROW`）。FR10.3 は Met の見込み（2つの code に結び付きの結合テストがあり通る）。
- **k6 の台本（FR7.1）**: 割り当て済み（409 `ROLE_NO_CHANGE`）も受け入れる `roleAssignEnsured` を、`roleAdminRead`・`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead` の setup で使い、`workRoleRead` の確かめを自分の2つのロールの有無にした。`k6 inspect --include-system-env-vars` で4つの場面が読めた。
- **測りの手順（FR7.2・FR8・FR9.1・FR9.4）**: `perf/README.md` の「性能の測り直し」の節（(a) 合否の回 5 回、(b) 10 MiB の DSL と行を分けた悪い形の 5 MiB の権限の YAML のヒープと `dslMixed` の `memory.peak`、(c) ロールの3場面の順と単独）。流すのは Build and Test。

## 10. ブランチ ③: 計画との差

1. **`myPermissionsTree` の setup に作業ロールの切り替えを足した**（Step 40）: 前の場面が保存した作業ロールが残ると木が悪い側のロールで開かないため、setup の最後に利用者ごとに作業ロールを悪い側のロールへ切り替える（使い捨ての環境の監査に `WORK_ROLE_SWITCHED` が残る）。FR7.1 の「前の場面の状態に頼らない」に含まれると読んだ小さな足し。
2. **`RoleTransferWriterTest` は 256 文字ではなく 128 コードポイントの名前で確かめた**（Step 37）: 対象の名前の検証の上限は 128 コードポイントで、256 文字の名前は保存できない。256 文字を最長の逃がしで書くと SnakeYAML の単純なキーの上限（1,024）で読めない（1行の長さの上限と関係のない既存の制約）。
3. **計画 4.1節に無かったテストの直し**: `RoleTransferReaderTest` の `flowSequence`（展開後の節の境界を約 100 万文字の1行で作っていた）。1回目の単体テストの流しで行の上限で落ちて分かり、要素 1,000 個ごとに改行する形にした。
4. **文言の行の埋め方**: `dsl.limit.line` は埋める値が上限だけのため、`DslErrorMessages.message()` でこの鍵のときだけ `{0}` に誤りの行を埋める特別な扱いを足した。
5. **権限の YAML の行の上限の拒否は 413**（D-3: A の帰結）: 本文が 5 MiB の内でも 413 `PAYLOAD_TOO_LARGE` になる。README に書いた。
6. **DSL の行の上限の拒否の監査**: 受け付けなかった投入の理由は `SIZE_LIMIT` で、本文の大きさで断ったとき（識別なし）と違い、識別（`dsl_hash`）が入る（既存の道のまま）。
7. **行の区切り**: 計画どおり `\n`・`\r` だけで区切り、YAML 1.1 の改行（U+0085・U+2028・U+2029）は行の中の文字として数える（厳しい側。誤りの行の番号が SnakeYAML の位置とずれうる）。
8. **perf/README の台本は流していない**: (b) の悪い形を作る Python だけスクラッチで流して 5,242,880 バイト・最も長い行 2,008 文字を確かめた。`heap-run.sh`・`role-pool-5.sh`・`role-three.sh` は Build and Test で初めて流す。

## 11. ブランチ ③: 確かめの結果

| 確かめ | 結果 |
|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 15:14:53〜15:29:43 UTC） | 成功。backend 単体 2,133・結合 1,171（失敗 0・SKIPPED 0）、frontend 1,589。backend 行 98.0%・分岐 92.6%、frontend 行 95.36%・分岐 90.38%。手を入れたパッケージ: `dsl.parse` 98.4・89.2、`dsl.domain` 100.0・96.1、`dsl.service` 97.1・93.5、`dslmanage.service` 97.9・97.0、`dslmanage.generate` 99.4・100.0、`role.service` 96.9・91.1。`osvScan` は UP-TO-DATE（lockfile は変わらない） |
| スリープ（`pmset -g log`） | verify の間に Sleep・DarkWake 無し |
| E2E | 画面のコードに触れていないため流していない（計画 1節） |
| k6 | `k6 inspect --include-system-env-vars` で `workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead`・`roleAdminRead` が読めた |

## 12. ①〜③ の全体

- **変えたファイル**: `cf6d5de`（① の前の `develop`）から作業ツリーまでで、`aidlc/` の外の 118 パス（`source-manifest.json`。消した `useSlowNotice` の4ファイルと、`shared/slow-notice` へ移した2ファイルの元の道を含む）。
- **境界テスト**: 新しい依存の向きは生んでいない（role → dsl.domain は前から許された向き）。既存の境界テストは緩めていない。足した境界テストは無い。
- **カバレッジ**: 一覧（`packagesJudgedByTotal`）の 7 パッケージには、①〜③ のどこでも手を入れていない。下限・除外は変えていない。
- **FR12.1 の一覧の結果**: 5節のとおり（K2・K3・K4 の 11 行のうち 10 行を直し、K4 R-07 は記録だけ）。範囲の外へ出した指摘は無い。
- **Build and Test に引き継ぐこと**: 計画 6節の 1〜9 のとおり。とくに FR1.4（3つの統合の後の CI を試行ごとに確かめ、プルリクエストを閉じる）、FR7.2・FR8・FR9.1・FR9.3・FR9.4（`perf/README.md` の「性能の測り直し」）、FR9.2 の配備の前の測り直し（D-5: A）。① で見送った axe-core 4.14.0 の WCAG 2.5.3 の違反は後の Intent に回す（依頼者の決定）。
- **追跡**: `traceability.json` は要件の 44 件（OK 28・Deferred 13・N/A 3）。

## 13. ブランチ ④: Dependabot の知らせ 4 件（計画に無い追加、依頼者の決定）

| PR | 依存 | 版 | 種類 |
|---|---|---|---|
| 53 | `prettier` | 3.9.9 → 3.9.10 | 開発時 |
| 52 | `vitest` | 5.0.3 → 5.0.4 | 開発時 |
| 51 | `@vitest/coverage-v8` | 5.0.3 → 5.0.4 | 開発時 |
| 50 | `@fontsource/noto-sans-jp` | 5.3.0 → 5.3.1 | 実行時（画面の書体） |

- 変えたファイルは `frontend/package.json`・`frontend/package-lock.json` だけ（推移依存は `@vitest/mocker`・`@vitest/spy` 5.0.4、`obug` 3.0.0）。書式の直しは無い。見送った更新は無い。
- 確かめ: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 15:35〜15:49 UTC）は、前からある不安定なテスト `InvitedPersonAuthenticationIT` の1件（招待の ID を乱数の 1,000 の枠に書き換えて主キーが重なった）で失敗した。ほかは frontend 1,589・backend 単体 2,133・結合 1,170 が通り、SKIPPED 0。カバレッジの検証は流れていない。`./gradlew osvScan` は失敗の条件 0・警告 18。スリープ無し。E2E は Intent の最後（依頼者の決定）。詳しくは `generation-notes.md` のブランチ ④。
- 統合は、不安定なテストの扱い（`team.md` の決まりでは直すまで統合しない）を決めてからにする。`@fontsource/noto-sans-jp` は配備の後のスモークテストで文字の表示を確かめる。
- **計画に無い変更（依頼者の決定 A）**: verify で落ちた `InvitedPersonAuthenticationIT`（招待の ID を乱数の 1,000 の枠に書き換えて主キーが重なる）を ④ で直した。再現の結合テスト `invitation/testsupport/InvitedPersonIdsIT.java` で、直す前に同じ `DuplicateKeyException` で落ちることを確かめ、ID の選び方を手伝い `InvitedPersonIds`（下限・招待の ID の最大＋1・利用者の ID の最大＋1 のうち最も大きい値）に直した。テストだけの変更で、`src/main` は変えていない。同じ原因の形はほかに無い。2つのクラスを単独で 10 回流して通った。
- 直した後の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（16:16〜16:30 UTC）: backend 単体 2,133・結合 1,173（失敗 0・SKIPPED 0）、`frontendTest` 1,589 は通ったが、`frontendCoverage` で `BusinessNavigationProvider.test.tsx` の1件が落ちた（ログアウトの後の開閉の記録の消去を、描画の後の効果を待たずに確かめている。単独 15 回・全体 3 回では再現しない）。直しの案（`waitFor` で待つ）は範囲の外のため諮る。続けて流した `./gradlew verify`（clean なし）は通った（frontend 1,589、カバレッジの下限・Gitleaks・SpotBugs・OSV-Scanner 失敗の条件 0・警告 18）。カバレッジは backend 行 98.0%・分岐 92.6%、frontend 行 95.36%・分岐 90.38%。
- **計画に無い変更（依頼者の決定 A）その2**: `BusinessNavigationProvider.test.tsx` の 203 行を `waitFor` で待って確かめる形にした（テストだけ、時間の上限は変えない）。近いテスト（`LogoutPage.test.tsx`・`ShellLayout.test.tsx`・`navExpansion.test.ts`）に同じ原因の形は無い。単独で 10 回（カバレッジ付き）通った。
- **④ の最後の確かめ**: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 16:59〜17:14 UTC）は成功。backend 単体 2,133・結合 1,173（失敗 0・SKIPPED 0）、frontend 1,589。backend 行 98.0%・分岐 92.6%、frontend 行 95.36%・分岐 90.36%。Gitleaks 漏えい無し、OSV-Scanner 失敗の条件 0・警告 18。スリープ無し。E2E は Intent の最後。
