# コード生成の計画（Intent 261108-ci-size-coverage）

## Summary

- Builds: CI の要約に規模・テストの件数・カバレッジを書く2つの Node の台本と、CI の手順（cloc・要約・HTML の報告の保存）
- Touches: `scripts/program-size.mjs`（新規）、`scripts/ci-summary.mjs`（新規）、`.github/workflows/ci.yml`、`docs/verification.md`
- Tests: 新しい自動テストは置かない（依頼者の決定 Q4・F1）。Python 版との一致の確かめ（NFR5）と、手元の報告での要約の確かめで代える

## 前提と入力

- 要件: `inception/requirements-analysis/requirements.md`（FR1〜FR5、NFR1〜NFR5）
- 知識ベース: `aidlc/spaces/default/codekb/mastersmith2/`（K-119〜K-126）
- 単位の分割・設計の段は無い（express、zero-Unit）。作業ブランチは `feature/261108-ci-size-coverage`（`develop` から作り、squash で戻す）。
- 依頼者の決定: `scripts/` の台本に単体テストとライセンスヘッダーの検査を足さない（requirements.md 6節）。
- アプリのソース（`backend/src`・`frontend/src`）と `./gradlew verify` の段は変えない（NFR2）。

## 影響の範囲（検索で洗い出した既存のもの）

- `.github/workflows/ci.yml` の `verify` の job: 手順は「取得 → JDK → Node → Gradle → Gitleaks・OSV-Scanner → `./gradlew verify` → WAR の保存」。`fetch-depth: 0`。どの手順にも `if:` が無い（K-119）。`actions/upload-artifact` は `cf430e030ddbb5b0abf93d22962f4752f3646cd9`（v7.0.2）で固定済み。
- JaCoCo: `jacocoTestReport` の XML と HTML（`backend/build/reports/jacoco/test/`）。下限は `backend/build.gradle.kts` の `jacocoTestCoverageVerification`（行 0.80・分岐 0.70）、`packagesJudgedByTotal`（7 パッケージ、313 行）。
- Vitest: `frontend/vitest.config.ts` の `reporter: ['text', 'html', 'json-summary']`、`reportsDirectory: 'coverage'`（`frontend/coverage/`）。
- JUnit の XML: `backend/build/test-results/test/` と `backend/build/test-results/integrationTest/`。
- ライセンスヘッダーの検査（`frontend/scripts/check-license-header.mjs`）はルートの `scripts/` を見ない。今回も対象を広げない。

## Steps

- [x] **Step 1: 作業ブランチと土台**（FR1・FR4）
  - `develop` から `feature/261108-ci-size-coverage` を作る。
  - 手元の cloc の版（2.10）と、Python の移す元の台本が手元で動くことを確かめる（NFR5 の比べの元）。
- [x] **Step 2: テストの道具の確かめ**（Testing Contract の runner_step）
  - 新しい自動テストは置かない（依頼者の決定）。確かめのコマンド（`unit-test-instructions.md`）が手元で流せることを確かめる: `node scripts/program-size.mjs`・`node scripts/ci-summary.mjs`（報告の置き場を引数で渡す）・Python 版との比べ。
- [x] **Step 3: `scripts/program-size.mjs` を作る**（FR1.1〜FR1.9、NFR3・NFR4）
  - Python の台本の分類（`classify`）・外すもの（`EXCLUDE`）・集計・表の出し方を、Node の組み込み（`node:child_process` の `execFileSync`、`node:fs`、`node:path`、`node:os`）だけで移す。新しい npm の依存は足さない。
  - cloc は `--by-file --json --quiet --list-file=-` に、git が管理するファイルの一覧を標準入力で渡す（移す元と同じ）。
  - 「数えた対象」の欄: 数えた日時、ブランチ（`GITHUB_REF_NAME`、無ければ `git rev-parse --abbrev-ref HEAD`）と先頭のコミット、`origin/main` の先頭（無ければ「—」）、数えたファイルの数、cloc の版。未コミットの件数は出さない。台本と報告に依頼者の手元の置き場のパスを書かない（「分類の決まりは `scripts/program-size.mjs` の冒頭」と書く）。
  - `--base <commit>`: `git worktree add --detach <リポジトリの build/program-size-base> <commit>` で取り出して同じ決まりで数え、大分類と中分類の表に「前の回との差」の列（コードの行・ファイルの数。符号つき）を足す。終わったら `git worktree remove --force` で消す（失敗しても消す）。取り出せないときは差を「—」にし、理由を返す。
  - 関数を `export` し（数える・表を作る・差を作る）、直接流したときは標準出力に Markdown を出す。失敗（git・cloc が無いなど）は理由を標準エラーに出して終了コード 1 にする（`ci-summary.mjs` が受けて欄に理由を書く）。
  - 先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）。コメントは日本語。
- [x] **Step 4: `scripts/program-size.mjs` を確かめる**（NFR5、FR1.8）
  - 同じコミットで Python 版と Node 版を流し、日時・ブランチ・道具の行を除いた表の部分が一致することを `diff` で確かめる。差があれば Node 版を直す。
  - `--base HEAD~1` と、取り出せない値（`0000000000000000000000000000000000000000`・無いコミット）で、差の列と「—」と理由が出ることを確かめる。`build/program-size-base` が残らないことを確かめる。
- [x] **Step 5: `scripts/ci-summary.mjs` を作る**（FR2・FR3・FR4.4・FR4.5、NFR2・NFR3）
  - 要約を「規模 → テストの件数 → カバレッジ」の順に組み立て、`$GITHUB_STEP_SUMMARY`（無ければ標準出力）に書く。
  - 規模: `program-size.mjs` の関数を呼ぶ。比べる元は引数 `--base <sha>`。空・0 が並ぶ値・取り出せないときは差を「—」にし、理由を1行書く。手動の実行とタグは、CI の手順の側で比べる元を渡さない（Step 7）。
  - テストの件数: `backend/build/test-results/test/` と `integrationTest/` の `TEST-*.xml` の `<testsuite>` の属性（tests・failures・errors・skipped）を足し、通った・失敗・飛ばしたの表にする。
  - バックエンドのカバレッジ: `jacocoTestReport.xml` を正規表現で読む（XML の部品を使わないため外部の DTD・実体を読まない）。根の直下の `counter`（LINE・BRANCH）で全体、`<package>` ごとの直下の `counter` でパッケージごとの値。下限（行 80%・分岐 70%）と `packagesJudgedByTotal` は `backend/build.gradle.kts` から読み（読めなければ要約に理由を書く）、各パッケージに下限との差（ポイント）と印を付ける。
  - 画面のカバレッジ: `frontend/coverage/coverage-summary.json` の `total`（lines・branches・functions・statements）と、ファイルごとの値を `features/<機能>`・`app/<名前>`・`shared/<名前>`・それ以外に足し合わせた表。
  - パッケージごと・領域ごとの表は `<details>` で畳む。
  - 報告のファイルが無い欄は「報告なし（./gradlew verify が途中で落ちた見込み）」と書き、`::warning::` を出す。どの失敗でも終了コードは 0 とする（FR4.5）。
  - 載せるのはパス・行数・件数・割合だけ（NFR3）。
  - 先頭に Apache License 2.0 のヘッダー。コメントは日本語。
- [x] **Step 6: `scripts/ci-summary.mjs` を確かめる**（FR2〜FR4.5）
  - 手元の `./gradlew :backend:test :backend:integrationTest :backend:jacocoTestReport` と `frontend` のカバレッジの報告（Build and Test の全件の `verify` の前に、絞って作る。手元にある直近の報告を使ってよい）で要約を出し、全体の値が `jacocoTestReport.xml` と `coverage-summary.json` の値と一致することを確かめる。
  - 報告を置かない場所を渡して、「報告なし」と `::warning::` が出て終了コードが 0 になることを確かめる。
- [x] **Step 7: `.github/workflows/ci.yml` に手順を足す**（FR4.1〜FR4.6、NFR1・NFR2）
  - `./gradlew verify` の後に、次の3つを足す（WAR の保存は今のまま）。
    1. cloc を入れる: `sudo apt-get update && sudo apt-get install -y cloc`（`if: always()`、`continue-on-error: true`）。
    2. 要約を書く: `node scripts/ci-summary.mjs --base "<比べる元>"`（`if: always()`、`continue-on-error: true`）。比べる元は、`github.event_name == 'push'` かつ `github.ref_type == 'branch'` のときだけ `github.event.before` を渡し、それ以外（手動の実行・タグ）は空を渡して理由を出させる。
    3. HTML の報告を保存する: `actions/upload-artifact`（既存と同じ固定）で `backend/build/reports/jacoco/test/html/` と `frontend/coverage/` を1つの成果物（名前にコミットのハッシュ）として 14 日保存（`if: always()`、`if-no-files-found: ignore`）。
  - きっかけ・`release` の job・`./gradlew verify` の手順は変えない。
  - 手順の名前とコメントは日本語。YAML を `node` などで読めることを確かめる。
- [x] **Step 8: `docs/verification.md` に書く**（FR5.1）
  - 手元での規模の数え方（cloc を入れる、`node scripts/program-size.mjs`、`--base <commit>`）。
  - CI の要約の読み方（各欄の出どころ、「—」と「報告なし」の意味、HTML の報告の成果物の取り方と保存の日数）。
  - cloc（GPL-2.0）は CI で道具として動かすだけで WAR に含めない、という採用の理由（`team.md` の Code Style の新しい依存の決まり）。
  - `scripts/` の台本は検査とテストの対象の外であること（依頼者の決定）。
- [x] **Step 9: 記録**
  - `code-summary.md`・`traceability.json` を書く。
  - コードのコミットは、場所ごとに区切って（台本 → CI の手順 → 文書）、積む前に依頼者に尋ねる（`project.md` の Change Control）。

## 要件と手順の対応

| 要件 | 手順 |
|---|---|
| FR1.1〜FR1.9 | Step 3・Step 4 |
| FR2.1〜FR2.5 | Step 5・Step 6 |
| FR3.1 | Step 5・Step 6 |
| FR4.1〜FR4.6 | Step 5〜Step 7 |
| FR5.1 | Step 8 |
| NFR1 | Step 7（測るのは Build and Test の CI の実行） |
| NFR2 | Step 5・Step 7 |
| NFR3 | Step 3・Step 5 |
| NFR4 | Step 3 |
| NFR5 | Step 4 |

## 依頼者の決定との差

- Testing Contract の Minimal（要件ごとの単体テスト）と `team.md` の「テストのない機能は完成とみなさない」に対し、依頼者の決定（Q4: C、F1）で、この Intent の台本には自動テストを置かない。代わりに Step 4・Step 6 の確かめと、Build and Test の CI の1回の実行で確かめる。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "express",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実測し（`verifyQuick` の参考値では代えない）、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:e0c1402fb0fdf87833dc6fc51f1d226c013377c6571059d5dd52c189d1c32b84",
  "contract_sha256": "sha256:01f8494a29d3369f8be302c7f1b75490f53161375859a388b7e681c8e927bd00"
}
```
