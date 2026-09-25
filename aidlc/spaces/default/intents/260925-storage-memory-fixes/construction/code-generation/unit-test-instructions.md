# 単体テストの手順（260925-storage-memory-fixes）

Test Strategy は Minimal（要件ごとに1つ程度のテストと、部品ごとに正常の場合の1件）で、scope `bugfix` の下限として不具合を再現するテスト（最も狭く再現できる層。今回は結合テスト）を足す。Testing Contract の methodology は `test-after` で、`code-generation-plan.md` の Step の順に、層ごとに実装を書いてからその層のテストを書いて実行する。

この変更ではアプリの Java のコードを足さない（`code-generation-plan.md` の D2 で M2・M3 を選んだ時だけ既存のコードを変える）。そのため、テストは主に設定と HikariCP の標準の操作を確かめるものになる。

## テストの道具と設定

- バックエンドの既存の道具だけを使う: JUnit 5・Spring Boot Test・AssertJ・Awaitility 4.3.0（`backend/gradle.lockfile`）・組み込みの H2 2.4.240・HikariCP 7.0.2。新しい依存は足さない。
- 単体テストは `XxxTest`（`./gradlew :backend:test`）、Spring や DB を起動する結合テストは `XxxIT`（`./gradlew :backend:integrationTest`）。テストの JVM の最大ヒープは 1g（`backend/build.gradle.kts`）。
- テストの説明（`@DisplayName`）は英語で書く。コメントは日本語。ファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。
- 内部DB（組み込みの H2）を使うテストは、コンテナではなく組み込みの H2 のファイルで行う（team.md）。今回の結合テストは Testcontainers を使わない。ただし `./gradlew verify` 全体は対象DB のテストを含むため、colima が動いていることを前提にする。
- colima の PC では、どのコマンドの前にも README の2つの環境変数をシェルに渡す（渡さないと対象DB のテストが SKIPPED になる。project.md）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- テストの実行の道具が動くことは、計画の前に既存のクラスで確かめた（2026-09-25）: `./gradlew :backend:test --tests 'cherry.mastersmith.config.H2DefragOnCloseTest' --rerun`（3 件成功）、`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.DataSourcePoolIT' --rerun`（3 件成功）。

## この変更のテスト（要件との対応）

| テスト | 種類 | 確かめること | 要件 | 条件 |
|---|---|---|---|---|
| `backend/src/test/java/cherry/mastersmith/config/HikariJmxSettingsTest.java` | 単体 | `application.yaml` で `spring.datasource.hikari.register-mbeans` と `allow-pool-suspension` が true、内部DB の接続先の既定に `DEFRAG_ALWAYS=TRUE` がある | FR1.1 | 常に |
| `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 結合（不具合の再現） | 投入と適用でファイルが伸び、何もしなければ縮まない。HikariCP の Pool の MBean の標準の操作（一時停止 → 破棄 → 0 本 → 再開）の後に縮む。本文の SHA-256 が一致する。再開の後に内部DB を使える。一時停止の間の処理は D3 のとおり（W1 は再開まで待って成功、W2 はすぐ失敗）。一時停止の前に借りた接続は返した時に閉じる | FR1.1・FR1.2・FR1.4・FR1.5（差あり）・FR1.8・FR1.11 | 常に |
| `docker/check-container-limits.sh`（既存の確かめの台本） | 設定の確かめ | 最大ヒープの割合が、D1・D2 で決めた値（50% または 37.5%）になる。`MASTERSMITH_JAVA_OPTIONS` の上書きが効く | FR2.3 | 常に |
| 既存の `DslContent` のテスト（無ければ `backend/src/test/java/cherry/mastersmith/dslmanage/domain/DslContentTest.java`） | 単体 | 本文のバイト列・`equals`・`toString` が変わらない | FR2.3 | D2 が M2 の時 |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/LoopbackPortCollisionTest.java` | 単体 | IPv4 の全アドレスで番号を取った状態で、テストの JVM の既定の待ち受けが同じ番号を取れない（`BindException`） | FR3.2 | D7 で直す時 |
| `perf/dsl-timing.sh` 33 行 | 文書の確かめ | 説明が 2g で、既定の値（55 行）と一致する | FR4.1 | 常に |

既存のテストで、変更の後も通ることを確かめるもの（NFR6・FR2.3・FR1.8）: `config/H2DefragOnCloseTest`・`config/DataSourcePoolIT`・`dslmanage/repository/DslManageRepositoryIT`・`dslmanage/web/DslAdminApiIT`・`auth/web/AccessTokenApiIT`、D2 で M2・M3 を選んだ時は `cherry.mastersmith.dsl` と `cherry.mastersmith.dslmanage` のすべてのテスト（DSL の信頼できない入力のテストを含む）。

## この変更のテストだけを流すコマンド

プロジェクトのルートで流す。どれもテストのクラスかパッケージを指定しており、プロジェクト全体は流さない。数を報告する時は `--rerun` を付けて実際に流した結果を使う。

```bash
# FR1 の設定（単体）
./gradlew :backend:test --rerun \
  --tests 'cherry.mastersmith.config.HikariJmxSettingsTest' \
  --tests 'cherry.mastersmith.config.H2DefragOnCloseTest'

# FR1 の再現（結合）と、接続のプールの既存の確かめ
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.config.H2CompactionByPoolSuspensionIT' \
  --tests 'cherry.mastersmith.config.DataSourcePoolIT'

# FR1.8 の既存の確かめ（DSL の保存の道と本文の一致）
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.dslmanage.repository.DslManageRepositoryIT' \
  --tests 'cherry.mastersmith.dslmanage.web.DslAdminApiIT'

# FR2（M1）の確かめ。先に別のタグでイメージを作る（配備のタグ local は上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:storage-memory-fix .
MASTERSMITH_IMAGE_TAG=storage-memory-fix ./docker/check-container-limits.sh

# FR2（D2 が M2・M3 の時だけ）: DSL の読み込みと管理のテスト（信頼できない入力のテストを含む）
./gradlew :backend:test --rerun --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'
./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'

# FR3（D7 で直す時）: 重なりの単体テストと、AccessTokenApiIT のクラスだけの繰り返し
./gradlew :backend:test --rerun --tests 'cherry.mastersmith.common.testsupport.LoopbackPortCollisionTest'
for i in $(seq 1 10); do ./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.auth.web.AccessTokenApiIT' -q || echo "run $i failed"; done

# FR4
grep -n 'MASTERSMITH_CONTAINER_MEMORY' perf/dsl-timing.sh
```

- 結果は `backend/build/test-results/test/` と `backend/build/test-results/integrationTest/` の `TEST-<クラス名>.xml` の `tests`・`failures`・`errors`・`skipped` で確かめる。
- 統合の前には、これとは別に `./gradlew verify`（全体。統合の前の関門）を流す。件数とカバレッジを報告する時は `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（project.md）。

## カバレッジの目標

- 下限は、バックエンド・フロントエンドとも、行 80% 以上・分岐 70% 以上（team.md）。下回ると `./gradlew verify` が失敗する。
- バックエンドのパッケージごとの下限（行 80%・分岐 70%）は、新しく作るパッケージだけに当たる（既存の 22 パッケージは一覧で外し、全体で判定）。この変更は新しい main のパッケージを作らない（M2・M3 も既存の `dslmanage.domain`・`dsl.parse` の中）ため、パッケージごとの下限の対象は増えない。
- 設定の変更（`application.yaml`・`Dockerfile`・`backend/build.gradle.kts`）はカバレッジの計測の対象外だが、上の表のテストで確かめる。
- カバレッジの計測から外すものは増やさない（team.md）。

## モックと差し替えの扱い

- モックは使わない。HikariCP・H2・JMX（プラットフォームの MBean サーバー）は本物を使う。HikariCP の Pool の MBean は、`ManagementFactory.getPlatformMBeanServer()` と `JMX.newMXBeanProxy` で `com.zaxxer.hikari:type=Pool (<プールの名前>)` の代理を取り、運用の手順と同じ標準の操作（`suspendPool`・`softEvictConnections`・`resumePool`）だけを呼ぶ。
- 結合テストのプールの名前は、そのクラスだけの名前にする。同じ JVM の中にキャッシュされたほかの Spring の文脈が同じ名前で MBean を登録すると、HikariCP が ERROR を出すため、テストの既定では MBean の登録を無効にし（置き場は計画の Step 2 で決める）、再現の結合テストだけが有効にする。
- 一時停止の間の待ちの確かめは、実時間の `sleep` に頼らない。別のスレッドの処理が終わったかを `CountDownLatch`・`Future` で確かめ、待つところは Awaitility の上限つきの待ちにする（team.md: テストで `sleep` や実時刻に依存しない）。
- テストが途中で失敗しても、`@AfterEach` で必ず `resumePool` を呼ぶ（プールが止まったままほかのテストに影響しないように）。
- `LoopbackPortCollisionTest` は、テストの中で開いた `ServerSocket` だけを使い、コンテナや colima に頼らない（CI の Linux でも同じ結果になる）。

## テストデータの扱い

- 内部DB は、テストのクラスごとに一時ディレクトリ（`@TempDir` や既存の `TestDatabase` の作り）にファイルの H2 を置く。接続先に `;DEFRAG_ALWAYS=TRUE` を付ける（今のテストの既定の接続先には付いていないため、再現の結合テストで上書きする）。
- DSL の本文は、固定の種の乱数の約 2MB（圧縮の効かない、悪い側の条件）を 12 回とプレビュー1件にする。10MB にしないのは、テストの JVM の最大ヒープ 1g と実行の時間を抑えるため。種を固定し、失敗した時に同じ本文で再現できるようにする。
- 本文の識別は SHA-256 で、記録の `dsl_hash` と比べる。
- 利用者のパスワード・トークンなどの秘密情報は、テストの中で作る仮の値だけを使い、ログに出さない。
- 実行の順に依存させない。各テストは自分で本文を入れ、自分の一時ディレクトリを使う。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract と手順、D1〜D9）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md`（前提の確かめ）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Testing Posture）
