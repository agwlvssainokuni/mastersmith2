# Code Generation の結果（260925-storage-memory-fixes）

承認済みの計画（`code-generation-plan.md`）と単体テストの手順書（`unit-test-instructions.md`）に沿って生成した結果の記録である。作業は短命のブランチ `fix/260925-storage-memory-fixes`（`develop` の `9773cb9` から作成）で行い、コミットはしていない（コミットは依頼者の承認の後に C1〜C5 に分けて行う）。数字はすべて実測である。

## 変えたファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | `spring.datasource.hikari` に `register-mbeans: true` と `allow-pool-suspension: true` を追加し、JMX を外に公開しないこと・詰め直しの手順・`DEFRAG_ALWAYS=TRUE` が要ることをコメントに書いた | FR1.1・FR1.2 |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/TestHikariMbeansEnvironmentPostProcessor.java`（新規） | テストの既定でプールの MBean の登録を無効にする（D8） | FR1.1 |
| `backend/src/test/resources/META-INF/spring.factories` | 上の補助を登録 | FR1.1 |
| `backend/src/test/java/cherry/mastersmith/config/HikariJmxSettingsTest.java`（新規） | 設定の単体テスト（4件） | FR1.1 |
| `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java`（新規） | 不具合を再現する結合テスト（3件） | FR1.1・FR1.2・FR1.4・FR1.5・FR1.8・FR1.11 |
| `Dockerfile` | `-XX:MaxRAMPercentage=75.0` を `50.0` にし、理由をコメントに書いた（M1） | FR2.3 |
| `docker/check-container-limits.sh` | 既定の最大ヒープの割合の確かめを 50% に合わせた（`DEFAULT_HEAP_PERCENT`） | FR2.3 |
| `docker/hikari-pool.sh`（新規、実行権限つき） | 詰め直しの道具の入口（`status`・`compact`・`resume`、PC の上のロック、失敗・中断のときの念のための再開） | FR1.1・FR1.10・NFR2 |
| `docker/jmx/HikariPoolControl.java`（新規） | 依存なしの Java の1ファイル。アプリの JVM に attach し、HikariCP の Pool の MBean の標準の操作だけを呼ぶ | FR1.1・FR1.10・NFR2 |
| `backend/build.gradle.kts` | すべての `Test` のタスクに `systemProperty("java.net.preferIPv4Stack", "true")` を追加（D7） | FR3.2 |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/LoopbackPortCollisionTest.java`（新規） | 番号の重なりの単体テスト（3件） | FR3.2 |
| `perf/dsl-timing.sh` | 説明の「要件の条件は 1g」を 2g に直した。`--compact`（`--storage` と組み合わせる）を追加 | FR4.1・FR5 の準備 |
| `perf/README.md` | 表に `--compact` の行を追加し、見出しを直した | FR5 の準備 |
| `README.md` | 「内部DBのファイルの詰め直し（アプリを止めずに）」の節を追加。既知の制約（U4-STORAGE-RUN）を書き直した。JVM の説明の 75% を 50% に直し、`MASTERSMITH_DB_URL` の説明に道具を書き足した | FR1.9・NFR1・FR2.3 |
| `.env.example` | `MASTERSMITH_JAVA_OPTIONS` の説明の 75% を 50% に、例を 40.0 に直した | FR2.3 |

アプリの Java の本番のコード（`backend/src/main/java`）は変えていない。表の形・Flyway の移行・H2 の接続先も変えていない（FR1.8）。

## 手順ごとの結果

| Step | 結果 |
|---|---|
| Step 1 | 設定を追加した。D3 は W1 のため `throwIfSuspended` は足していない |
| Step 2 | 既存の道具（JUnit 5・Spring Boot Test・AssertJ・Awaitility 4.3.0・組み込みの H2）で足り、新しい依存は足していない。この変更のテストのコマンドは `unit-test-instructions.md` のとおり（colima の PC では `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）。MBean の登録を無効にする置き場は、テストの `EnvironmentPostProcessor` にした（計画との差の 1） |
| Step 3 | 該当なし（表の形・移行・接続先は変えない） |
| Step 4 | 単体テスト 4件・結合テスト 3件がすべて通った。設定を足す前の状態では結合テストが失敗することを確かめた（下の「不具合の再現」） |
| Step 5・Step 6 | 該当なし（DB アクセスのコードは変えない） |
| Step 7・Step 8 | 該当なし（D2 は M1 だけ。M2・M3 は行わない） |
| Step 9〜Step 12 | 該当なし（HTTP の API と画面は作らない） |
| Step 13 | M1・道具・`--compact`・FR3 の直し・FR3 の切り分け・FR4 を行った。確かめの結果は下の各節 |
| Step 14 | README・`perf/README.md`・`.env.example`・この文書・`traceability.json`・`source-manifest.json` を書いた |
| Step 15 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が通った。E2E も通った（下の「確かめた結果」） |

計画のチェックボックスのうち、選ばれなかった条件つきの項目（W2 の `throwIfSuspended`、M2、M3、M2・M3 のテスト）は、行っていないため `[ ]` のままにした。

## 要件との差（計画の表を写したもの）

依頼者の決定 Q2・Q3 により、承認済みの要件と次の差がある。要件の文書は書き換えていない。

| 要件 | 承認済みの要件 | この段での扱い | 根拠 |
|---|---|---|---|
| FR1.1 | 管理者向けの API に、すべての接続を閉じて詰め直す操作を足す | アプリのコードは足さない。HikariCP の標準の MBean（`com.zaxxer.hikari:type=Pool (mastersmith-db)`）の操作（一時停止 → 接続の破棄 → 0 本を待つ → 再開）を JMX で呼ぶ。最後の接続が閉じた時に、接続先の `DEFRAG_ALWAYS=TRUE` で H2 が詰め直す | Q2: A・Q3 |
| FR1.3 | 入口は `/api/admin/**`。未認証 401・管理者でない 403・管理者 200 をサーバー側のテストで確かめる | HTTP の入口は作らない。401・403・200 のテストも作らない。操作できるのは、この PC で Docker を使える人だけ | Q2: A |
| FR1.5 | 詰め直しの間の要求は待たせ、接続の待ちの上限（5 秒）を超えたら今と同じ失敗 | 一時停止の間の要求は、再開まで上限なしで待たされ、再開の後に成功する。5 秒の上限は効かない。道具が一時停止の長さに上限を置く（D3: W1・D5: A） | HikariCP 7.0.2 の作り |
| FR1.6 | 詰め直しの操作を監査ログに残す | 監査ログにもアプリのログにも出さない。前と後の大きさと時間は道具の出力で見る | Q3 |
| FR1.7 | 詰め直しの最中のほかの監査の記録の失敗は、今の監査の決まりに従う | 変えない。ただし一時停止の間は失敗ではなく待ちになる | Q3・FR1.5 の差 |
| FR1.10 | 詰め直しの操作を同時に複数受け付けない | アプリでは制御できないため、道具が同時に1つだけ動く（PC の上のロック）。借りている接続があれば 0 本になるまで待つ（上限 10 秒） | Q2・Q3 |
| NFR1 | 詰め直しの間もプロセスは止めない。かかる時間を測り README に書く | 変えない。一時停止が約 30 秒を超えると unhealthy になることも README に書いた | 測定の記録 3.4 節 |
| NFR3 | 詰め直しの操作は管理者だけ。サーバー側で検査する | サーバー側の管理者の検査は無い。JMX を外に公開せず、この PC の Docker を使える人だけが操作できる | Q2: A |

## 計画との差

| 番号 | 計画 | 実際 | 理由 |
|---|---|---|---|
| 1 | Step 2: MBean の登録を無効にする置き場は `TestDatabase` の `registry.add` か、テストの設定のファイル | テストの `EnvironmentPostProcessor`（`TestHikariMbeansEnvironmentPostProcessor`、`spring.factories` で登録）にした。既定で `spring.datasource.hikari.register-mbeans=false` を最優先で入れ、`mastersmith.test-fixture.disable-hikari-mbeans=false` で切れる | `TestDatabase.register` を使わずに Spring を起動するテストのクラスが 15 あり、`registry.add` では漏れるため。既存の署名鍵の補助と同じ仕組み |
| 2 | Step 4: 約 2MB の本文を 12 回とプレビュー1件（履歴の上限は既定の 20） | 2MiB の本文を 24 回とプレビュー1件、履歴の上限 3（残る本文は 4件・8MiB） | 12 回・上限 20 ではほとんどの本文が残り、伸びが残る本文との差として見えにくかった（1回目は 16.8MB で、上限の2倍という判定に届かなかった）。上限 3 にして、消した本文の場所が残ることが分かる形にした |
| 3 | Step 4: 詰め直しの後の上限は「D6 の考え方をこのテストの本文に当てはめた値」 | 残る本文の 1.2 倍に 4MiB を足した値（約 13.6MiB）とした。伸びた大きさは、この上限の2倍を超えることを確かめる | D6（約 210MB に約 40MiB、2割ほど）の割合に、ほかの表・索引・移行の記録の分を足した |
| 4 | Step 4: Spring の文脈の作り方の指定なし | `SpringApplicationBuilder` でこのクラスだけの文脈を作り、クラスの終わりに閉じる（`DataSourcePoolIT` と同じ形） | テストの文脈のキャッシュに、プールの名前の違う文脈を残さないため |
| 5 | Step 13: `LoopbackPortCollisionTest` は IPv4 の全アドレスで番号を取った状態で、既定の待ち受けが同じ番号を取れないこと | colima の転送の役を IPv4 だけのソケット（`StandardProtocolFamily.INET`）で開く形にした。あわせて、テストの JVM が IPv4 を優先していることと、既定の待ち受けが IPv4 のアドレスになることの2件を足し、3件にした | 最初は転送の役を Java の既定のソケットで開いたため、直す前の設定でもこの確かめが通ってしまった（Java の既定は IPv4 のアドレスで待ち受けても IPv6 と兼用のソケットで、colima の転送とは形が違う）。IPv4 だけのソケットにすると、直す前の設定でこの PC で失敗した |
| 6 | Step 13: 道具の詳細 | 出力の時刻を Asia/Tokyo にした（`-e TZ=Asia/Tokyo`）。終わりの値を 0・1・2・3 にそろえた（中断や想定の外の値は、念のための再開の後に 1）。使い方の誤り（2）では念のための再開をしない。念のための再開にはプールの名前だけを渡す | 確かめの中で、使い方の誤りのときに念のための再開も同じ誤りで失敗して 3 を返すことと、中断のときに 130 を返すことが分かったため直した |
| 7 | Step 13: FR4 は 33 行の説明、既定の値は 55 行 | `--compact` の説明を先頭に足したため、説明は 36 行、既定の値は 58 行になった | 行の番号がずれただけ。`perf/README.md` の 2g の記述は変えていない |
| 8 | Step 14: 変えるファイルに `.env.example` は無い | `.env.example` の 75% の説明も直した | 既定の割合の説明が食い違うため |
| 9 | Step 13: FR3 の切り分け (b) の記録 | 1回目の記録は `lsof` の条件の書き方の誤り（`-a` が無く、条件が「または」で結ばれた）で、ssh 以外（テストのアプリ自身）の待ち受けまで数えてしまい、使えなかった。`-a` を付けて2回ともやり直した。使えなかった記録は `build/perf-results/storage-memory-fix/fr3/invalid-sampling/` に分けて残した（コミットしない） | 記録の取り方の誤り |

`unit-test-instructions.md` のコマンドは、書いてあるとおりに流した（差は無い）。

## 不具合の再現（直す前の失敗）

- 内部DB の伸び（FR1.11）: `H2CompactionByPoolSuspensionIT` を、`application.yaml` の変更を一時的に戻した状態で流すと、3件のうち最初の確かめ（プールの MBean が登録される）で失敗した（`Expecting value to be true but was false`）。変更を戻すと3件とも通った。テストは本番の設定の値をそのまま使うため、設定が消えると失敗する。
- 伸びと縮み（同じ結合テストの実測、3回流して同じ傾向）: 2MiB の本文を 24 回投入・適用しプレビューを1件置くと、ファイルは 46.7〜48.8MB に伸び（残る本文は 8.0MiB）、`CHECKPOINT SYNC` の後も縮まなかった。プールの MBean で一時停止 → 破棄 → 0 本 → 再開を行うと 8.4MB に縮み、残るすべての本文の SHA-256 が記録の識別と一致した。
- `AccessTokenApiIT` の直し（FR3.2）: `backend/build.gradle.kts` の `preferIPv4Stack` を一時的に消した状態で `LoopbackPortCollisionTest` を流すと、3件すべてが失敗した（番号を重ねて取れてしまう・IPv4 を優先していない・既定の待ち受けが `[::]`）。戻すと3件とも通った。

## 確かめた結果

### テストの件数とカバレッジ（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、16:29〜16:34）

| 項目 | 結果 |
|---|---|
| 終わりの値 | 0（すべての検査が通った） |
| バックエンドの単体テスト | 102 クラス・733 件、失敗 0・誤り 0・飛ばし 0 |
| バックエンドの結合テスト | 75 クラス・385 件、失敗 0・誤り 0・飛ばし 0（対象DB のテストも飛ばされていない） |
| バックエンドのカバレッジ（JaCoCo の合計） | 行 98.1%（3,941 ／ 4,018）、分岐 93.8%（1,369 ／ 1,459） |
| フロントエンドのテスト | 316 件すべて成功 |
| フロントエンドのカバレッジ | 行 97.96%・分岐 93.76%・文 97.85% |
| SpotBugs | 止める指摘なし（既存の priority 2 の警告 `MS_PKGPROTECT` が1件） |
| そのほか | フォーマット・リンタ・ライセンスヘッダー・ビルド・カバレッジの下限・Gitleaks・依存関係の脆弱性検査の各段が通った |

カバレッジの下限（行 80%・分岐 70%）と除外は変えていない。新しい main のパッケージは作っていないため、パッケージごとの下限の対象も増えていない（NFR4）。

### E2E（`./gradlew e2eTest`）

6件すべて成功（1.1 分）。

### この変更のテスト（手順書のコマンド）

| コマンド | 結果 |
|---|---|
| `HikariJmxSettingsTest`・`H2DefragOnCloseTest` | 7件成功 |
| `H2CompactionByPoolSuspensionIT`・`DataSourcePoolIT` | 6件成功 |
| `LoopbackPortCollisionTest` | 3件成功 |
| `MASTERSMITH_IMAGE_TAG=storage-memory-fix ./docker/check-container-limits.sh` | すべて期待どおり（最大ヒープが上限 512m の 50%）。比べとして配備のイメージ `mastersmith:local`（75%）に流すと、既定の割合の2件が期待と違うと出た |
| `grep -n 'MASTERSMITH_CONTAINER_MEMORY' perf/dsl-timing.sh` | 説明（36 行）が 2g で、既定の値（58 行）の `2g` と一致 |

`DslManageRepositoryIT`・`DslAdminApiIT`・`AccessTokenApiIT` などの既存の結合テストは、上の `verify` の中で通った（NFR6・FR1.8）。

### 道具の確かめ（使い捨ての環境）

イメージ `mastersmith:storage-memory-fix`（この変更の WAR）で、`KEEP=1 MASTERSMITH_IMAGE_TAG=storage-memory-fix caffeinate -i ./perf/dsl-timing.sh --storage --compact postgres` を流した（16:04〜16:10）。配備したアプリ（`mastersmith-app-1`）は止めず、道具も流していない。終わった後、使い捨ての環境を `down -v` で消し、一時ディレクトリも消した。

| 確かめ | 結果 |
|---|---|
| 10MB の DSL の投入と適用を 21 回とプレビュー1件の後の `compact` | 270.4MiB → 15.2MiB。接続は破棄から 6 ms で 0 本、ファイルが落ち着くまで 3,576 ms、一時停止から再開まで 3,589 ms、道具全体で 5.26 秒。終わりの値 0 |
| 詰め直しの前後のデータ | 適用中の DSL・プレビューの DSL の SHA-256、previewId、履歴が一致（`compact_data_intact=yes`）。直後の健全性は healthy |
| その後の止める・起動し直す（既存の `--storage` の流れ） | 止めるのに 0.874 秒（exit 143）、起動から healthy まで 8.7 秒、データ一致、履歴 20 版を戻してすべての SHA-256 が一致（20/20）。OOMKilled は false |
| `status`・`resume` だけ | どちらも 0 で終わった |
| 同時に2つの `compact` | 2つ目はロックで断られ、2 で終わった |
| 一時停止の間のログイン | 一時停止から約 3 秒後に送ったログインは 6.5 秒待って 200（再開の直後に成功） |
| 中断（処理のグループに SIGINT、Ctrl-C と同じ） | JVM の終わりの処理が再開し、シェルの念のための再開も成功した。直した後の終わりの値は 1。ロックは消えた。直後のログインは 0.29 秒で 200 |
| 落ち着くまでの上限で打ち切り（`--settle-wait 2 --stable 10`） | 2.1 秒で再開を優先し、1 で終わった |
| 使い方の誤り（`--zero-wait 0`）・存在しないコンテナ・知らない副コマンド | どれも 2 で終わった |
| 出力 | 接続の本数・大きさ・時間だけで、秘密情報は含まない（NFR2） |

生の結果は `build/perf-results/storage-memory-fix/tool-check/`（コミットしない）にある。

### FR3 の切り分けの記録（FR3.1）

| 試し | 結果 |
|---|---|
| (a) `AccessTokenApiIT` をクラスだけで 10 回（`--rerun`、直した後の設定） | 10 回とも 6件成功 |
| (b) `./gradlew :backend:integrationTest --rerun` を2回、colima の `ssh` の待ち受けの番号（`lsof -a -c ssh`）を 2 秒ごとに記録し、テストのアプリの番号（「Tomcat started on port」）と比べた | 2回とも 385件成功（207 秒・206 秒）。番号の重なりは無かった。colima の転送は `*:33115`〜`*:33136`（VM の Linux の一時的な番号の範囲で、順に増える）、テストのアプリは 49743〜53754（macOS の一時的な番号の範囲） |

読み取れること:

- 候補 (b)（colima の転送とテストのアプリの番号の重なり）: 仕組みは計画の前の確かめ（測定の記録 4 節）と `LoopbackPortCollisionTest` で再現できたが、今回の2回の実行では重ならなかった。重なるには、VM の側の番号が 49152 以上まで進んでいる必要がある。Docker が公開の番号を順に振るとすると、colima を長く動かし続けて多くのコンテナを起動した後に起こりうる（仮説。確かめていない）。依頼者の決定 D7 のとおり、この仕組みをテストの JVM の IPv4 の優先で防いだ。
- 候補 (a)（PC の負荷）: 否定も支持もできていない。
- 候補 (c)（1つの JVM に多数の Spring の文脈がたまる）: 結合テスト全体を2回流しても失敗しなかった。支持する結果は無い。
- 実際の1回目の失敗がどの候補によるものかは、確かめられない（報告は上書きされている）。

生の結果は `build/perf-results/storage-memory-fix/fr3/`（コミットしない）にある。

### 配備したアプリ

`mastersmith-app-1` と見本の対象DB には触れていない。作業の終わりに healthy であることを確かめた。

## Build and Test に引き継ぐこと

- FR2.4・FR5: 直した後のイメージで `dslMixed` を流し、D1 の目標（`anon` の最大が 1,536MiB 以下、かつ `memory.events` の `max` が 0。`checks` がすべて成功し `OOMKilled` が false）を判定する。`refresh` の場面も流す。
- FR1.4・NFR1: `STORAGE_ROUNDS=40` の `perf/dsl-timing.sh --storage --compact` で、詰め直しの直後の大きさを D6（250MiB）で判定し、圧縮の効かない本文でもかかる時間を測る。README の目安を測った値で確かめる。
- 詰め直しの最中にログインを送り、再開の後に成功することを確かめる（今回の道具の確かめでは 6.5 秒待って 200 だった）。

## 気になる点

- 道具の「借りている接続が 10 秒の内に返らないと、詰め直さずに再開する」道は、実際のコンテナでは起こせず確かめていない（重い DSL の操作は内部DB の接続を長く持たないため）。借りている接続が破棄の後も使え、返した時に閉じることは結合テストで確かめた。
- `startLocalManagementAgent` は、アプリの JVM の中に、コンテナの中のループバックだけで待ち受ける JMX の口を開き、アプリが止まるまで残す。外（PC・ネットワーク）には公開されない。
- 詰め直しの操作は監査ログにもアプリのログにも残らない（Q3）。
- 最大ヒープを 50% にしたため、ヒープが足りずに完全な GC が増える場面がありうる。Build and Test の `dslMixed` と `refresh` で確かめる。
- 計画の前の測定で使ったイメージ `mastersmith:storage-memory-base` と、今回のイメージ `mastersmith:storage-memory-fix` が残っている（不要なら消してよい）。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/requirements-analysis/requirements.md`
- 生の結果（コミットしない）: `build/perf-results/storage-memory-fix/`（`verify.log`・`e2e.log`・`tool-check/`・`fr3/`）
