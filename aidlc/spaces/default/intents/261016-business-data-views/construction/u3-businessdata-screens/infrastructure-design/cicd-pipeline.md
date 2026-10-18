# 配送の流れ — U3 u3-businessdata-screens

U3 は CI（`.github/workflows/ci.yml`）と `./gradlew verify` の段を変えない。変わるのは、verify と CI の外にある E2E（`./gradlew e2eTest`）の前提と順で、見本の PG の起動と、実行ごとの専用のスキーマと一時のロールの準備・片付けが加わる。

答え: `infrastructure-design-questions.md` の Q1〜Q7（すべて A）と、まとめの確認（Looks correct）。

## 1. CI（GitHub Actions）

| 項目 | 扱い |
|---|---|
| きっかけ・段 | 変えない。`develop` への push で `./gradlew verify` を流す（`ci.yml` 84行） |
| E2E | 意図して CI の外に置く。代わりの実行の場は、統合の前の手元・B7・Build and Test・リリースの前（project.md の学び） |
| 見本の PG | CI には置かない（E2E が CI に無いため） |

## 2. 1コマンドの検査（`./gradlew verify`）

| 段 | U3 で入るもの | 関門 |
|---|---|---|
| フォーマット・リンタ・ライセンスヘッダー | `frontend/src/features/tables/`・`src/shared/` の新しいファイル、`businessDataSchema.sql`、上乗せの compose のファイル | 1件でも違反なら止める |
| ビルド（型の検査） | 3つの画面を別々の `lazy` にする。`check-bundle-size.mjs` の入口の上限 | 上限を超えたら止める |
| 単体テスト | 画面の部品のテスト（Vitest・Testing Library・vitest-axe）、純粋な関数の fast-check。U4 の Q2: A で広げる `e2e/support/**/*.test.ts` に U3 の補助の単体のテストも置ける（置くかはコード生成で決める） | すべて通る |
| カバレッジ | frontend の全体で行 80%・分岐 70%。除外を増やさない（U3-NFR7.12） | 下回ったら止める |
| 安全の検査 | Gitleaks・OSV-Scanner。U3 は新しい npm の依存を足さない | High 以上で止める |

## 3. E2E（`./gradlew e2eTest`、verify と CI の外）

### 3.1 前提

起動しておくもの（`e2eTest` は起動・停止しない）:

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
./gradlew e2eTest            # ファイルを選ぶなら -Pe2e.files=e2e/180-business-data-flow.e2e.ts
```

### 3.2 `e2eTest` の順

| 順 | 中身 | 失敗したとき |
|---|---|---|
| 1 | Mailpit の API に届くかを確かめる（今のまま） | 起動の手順を示して止める |
| 2 | 見本の PG を確かめる: (a) `docker compose exec -T targetdb-postgres pg_isready`、(b) `127.0.0.1:${E2E_TARGETDB_PORT:-15432}` に TCP で届くか、(c) PUBLIC の TEMPORARY が取り上げてあるか | (a) は profile での起動、(b) は上乗せでの起動、(c) は REVOKE の手順を示して止める（Q6: A） |
| 3 | 前の残りを消す: 接頭辞 `e2e\_biz\_%` のスキーマと `mastersmith\_e2e\_%` のロール | 止める（件数をログに出す） |
| 4 | 実行の印を作り、スキーマ・表・架空の行（21 件以上）・2つのロール・付与を作る（Q4: A・Q5: A） | 止める。片付けは 6 で動く |
| 5 | 値を `Exec` の environment で渡して `npx playwright test` を流す（`-Pe2e.files` があればそのファイルだけ。Q7: A） | E2E の失敗。6 は動く |
| 6 | `finalizedBy` の片付けのタスク: 接続を切る → `DROP OWNED BY` → `DROP ROLE` → `DROP SCHEMA … CASCADE`、消した件数を出す | 失敗を知らせる。次の実行の 3 が拾う |

管理者は 2〜4・6 のすべてで `docker compose exec -T targetdb-postgres psql -U target_admin -d business` を使い、コンテナの中のローカルのソケットから入る（Q3: A）。管理者のパスワードは読まない。

準備の SQL の形（説明のための断片。値は標準入力の `\set` で渡す）:

```sql
CREATE SCHEMA :"schema";
-- ここに businessDataSchema.sql の中身を続けて標準入力に流す（表1つと架空の行 21 件以上）
CREATE ROLE :"reader" LOGIN PASSWORD :'reader_pw' VALID UNTIL :'until';
CREATE ROLE :"writer" LOGIN PASSWORD :'writer_pw' VALID UNTIL :'until';
GRANT USAGE ON SCHEMA :"schema" TO :"reader", :"writer";
GRANT SELECT ON ALL TABLES IN SCHEMA :"schema" TO :"reader";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA :"schema" TO :"writer";
```

確かめ（2 の (c)）は、作った書き込みのロールについても `has_database_privilege(<書き込みのロール>,'business','TEMP')` が偽であることを見る。

### 3.3 Playwright の設定

| 項目 | 扱い |
|---|---|
| 値の渡し方 | `webServer.env` に置かない。WAR はプロセスの環境変数を引き継ぐ（今の署名鍵と同じ形） |
| `SECRET_ENV_NAMES` | `MASTERSMITH_TARGET_DB_USERNAME`・`_PASSWORD`、`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD` を足す。管理者の値は足さない（プロセスに無い） |
| `SecretKind` | 業務データの値の種類（例 `businessValue`）を足す。180 の値は値のファイルに書き、185 の見本の目印の値は `SECRET_FIXED_VALUES` に足す |
| 接続先の値 | `127.0.0.1:15432` の文字列を探すかはコード生成で決める |
| 直接の `npx` | 対象DB の環境変数が無ければ、設定の読み込みで「`./gradlew e2eTest` から流す」旨の誤りにして止める（黙って飛ばさない。Q7: A） |

### 3.4 ファイルごとの扱い

| ファイル | 扱い | 本数 |
|---|---|---|
| `040-dsl-admin.e2e.ts` | 照合の期待を `dsl-warning-group-SCHEMA_MISMATCH` に替え、21行の説明を直す（Q2: A） | 数えない（書き換え） |
| `150-role-permission-flow.e2e.ts` | 変えない見込み（適用は警告で止まらない） | — |
| `170-menu-navigation-flow.e2e.ts` | 型の付いた見本で応答を差し替える（NFR 要件 Q2: A） | 数えない（書き換え） |
| `180-business-data-flow.e2e.ts` | 本物の見本の PG の実行のスキーマで、一覧 → 作成 → 詳細 → 更新 → 削除を流す。DSL は `E2E_BIZ_SCHEMA` で組んで API で適用する。利用者とロールは流れの中で作る。見本の形を本物の応答と `shapeCompare` で比べる | 1本（この Intent の代表の流れ） |
| `185-business-data-accessibility.e2e.ts` | 差し替えで、20 組＋200% の2件。測りの1件を含む | 数えない |

## 4. 時間の記録（U3-NFR7.8）

- E2E の全体・180・185 の時間を、B5・B6・Build and Test で `caffeinate -dims` を付けて測り、`pmset -g log` で眠りを確かめる。
- ファイルごとの時間は `test-results/e2e-results.json` の duration から集める。準備と片付けの所要も記録する。
- 基準（232 件・10分35秒）の 1.5 倍（約 15分53秒）を超えたら、185 の組み立てを依頼者に諮る。上限で落とさない。
- `./gradlew verify` と CI の時間を、B5・B6 の後と Build and Test で記録する（team.md）。

## 5. 統合・配備・戻し方・秘密

| 項目 | 扱い |
|---|---|
| 統合 | B5・B6 は squash で `develop` へ。どちらも `frontend/src` に触れるため、統合の前に E2E の全件を流す（team.md） |
| push | 依頼者が行う |
| 配備 | 画面は WAR に入る。配備の手順は Deployment Pipeline が前の Intent の手順を正として差だけを書く |
| 戻し | イメージだけ。U3 は内部DB の移行を足さない。上乗せ無しで見本の PG を作り直せば、ポートは閉じる |
| 見本の PG の TEMPORARY | B6 の前に手で1回、`REVOKE TEMPORARY ON DATABASE business FROM PUBLIC` を exec で流す（U2 の README の手順と同じ。Q6: A） |
| 秘密 | 一時のロールのパスワードは Gradle の中で乱数で作り、Gradle の外へは環境変数だけで渡す。ファイルに書かない。ログに出さない |

## 6. README で直す・足すもの

- E2E の節: 見本の PG を上乗せで起動する手順、`.env.targetdb` が要ること（見本の PG の起動のため）、実行のスキーマと一時のロールを作って消すこと、`-Pe2e.files` での流し方、片付けの件数の見方。
- E2E の表の 040 の行の説明と、180・185 の行。
- TEMPORARY の取り上げの手順（U2 の手順と1つにまとめる）。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（project.md の決まり）。

1. **Q3: A と承認の場の決定 U3 R-01 の文面との差**: 決定は管理者の資格情報をプロセスの環境変数で渡し、`SECRET_ENV_NAMES` に足すとした。この単位では、管理者は `docker compose exec` でコンテナの中のローカルのソケットから入り、管理者のパスワードをプロセスに入れない。`SECRET_ENV_NAMES` に足すのは一時の2つのロールの値だけになる。
2. **040 の書き換え**: 040 の 21行の前提（対象DB の接続先が無い）を、`SCHEMA_MISMATCH` の期待に替える（3.4）。未設定の経路は、サーバー側の結合テストと 135 の差し替えが受け持つ。
3. **U1 の「見本の対象DB は変えない」を上乗せで満たす**: `compose.yaml` は変えず、E2E のときだけ上乗せのファイルでポートを開ける（3.1）。
4. **U2 の R-01 にこの単位で答える**: U2 の `cicd-pipeline.md` 5.1 の Q5: A が前提にしていた、(a) 届く道（Q1: A）、(b) 管理者の資格情報（Q3: A）、(c) 040 との両立（Q2: A）を、この文書の 3節で答える。

## 後の段に回すこと

- **B5 の計画**
  - 上乗せのファイルと準備・片付けのタスクを入れる Bolt の決定（040 の書き換えと同じ Bolt にする。B5 の統合の前の E2E の全件から見本の PG が前提になる）
  - 170 の書き換え・185・測りの部品、bundle の前と後の記録
  - L4・L8・L9 の変更を使っている既存のテストと E2E の洗い出し（project.md の学び）
- **B6 の計画**
  - 180、`businessDataSchema.sql`、`SECRET_ENV_NAMES` と `SecretKind` の追加、`-Pe2e.files`、直接の `npx` で止める確かめ、README
  - B6 の前に手で行う TEMPORARY の取り上げ
- **B6 で確かめること**
  - 外からの接続が scram になり、誤ったパスワードで拒まれること
  - 片付けの後に `e2e_biz_*` と `mastersmith_e2e_*` が 0 件になること（E2E を途中で失敗させた回も含める）
  - 040・150・170・180 が通ること
  - E2E の全体・180・185 の時間

## U1・U2・U4 とそろえる点

- **U1**: 読み取りの環境変数の名前をそのまま使う。B2 の統合の前の E2E はまだ見本の PG を要らない（準備のタスクが入る Bolt から要る）。
- **U2**: 一時のロールは U2 の Q5: A の形。書き込みの環境変数の名前は U2 の Q1: A。B4 の統合の前の E2E も、準備のタスクが入る前なら今の前提のまま。TEMPORARY の取り上げは U2 の台本と同じ SQL。
- **U4**: B7 と Build and Test の E2E の全件の手順に、3.1 の見本の PG の起動を足す。axe-core の更新の統合の前の E2E（U4 の Q1: A）も同じ手順になる。トップバーの passes の確かめの置き場は、180・185 と番号を重ねない。

## 未確認の点

- **外からの接続が scram になること**: 見本の PG の中の `pg_hba.conf` は `127.0.0.1/32` を `trust` にしている。公開の番号から来る接続は `scram-sha-256` に当たる見込みだが、確かめていない。B6 で確かめ、`trust` に当たれば上乗せをやめて Q1 を諮り直す。
- **`DROP ROLE` が止まるか**: 一時のロールに接続が残っているときの `DROP ROLE` の振る舞いは確かめていない。片付けの先頭で `pg_terminate_backend` を流す。
- **185 の時間**: ファイルごとの基準の値が無く、185 の 22 件の時間は見積もれていない。B5・B6 の実測で判断する。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、まとめの確認: Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u3-businessdata-screens/nfr-requirements/security-requirements.md`（NFR4.25・NFR7.13）・`performance-requirements.md`（NFR7.8）
  - `construction/u3-businessdata-screens/nfr-design/security-design.md`（8節・9節・後の段に回すこと）・`performance-design.md`（6節）・`logical-components.md`（L10）
  - `construction/nfr-requirements/memory.md`（決定 U3 R-01）
  - `construction/u2-businessdata-write/infrastructure-design/cicd-pipeline.md`（5.1・10節）
  - `.aidlc-reviews/infrastructure-design/units/u2-businessdata-write/4bcccff4724084fb/1.json`（R-01）
  - `construction/u4-a11y-carryover/infrastructure-design/cicd-pipeline.md`（3節・4節・6節・10節）
  - `inception/delivery-planning/bolt-plan.md`（B5・B6）
- 既にあるもの（読むだけ）: `.github/workflows/ci.yml`（84行）、`build.gradle.kts`（418〜456行）、`frontend/playwright.config.ts`（41〜62行・98〜122行）、`frontend/e2e/040-dsl-admin.e2e.ts`（21行・146行）、`frontend/e2e/support/secretValues.ts`（36行）、`frontend/node_modules/playwright/lib/runner/index.js`（6321〜6327行。webServer が globalSetup より先に起動する）、`docker/targetdb/postgres/02-reader-account.sh`、`README.md`（83〜95行）

## Assumptions & Open Questions

- 前提: Playwright 1.63.0 では webServer が globalSetup より先に起動するため、一時のロールは Playwright の前（Gradle）で作る。Playwright を上げるときは順を確かめ直す。
- 前提: `docker` の CLI が colima の文脈で動く（verify の Testcontainers と同じ前提）。
- 未確認: 「未確認の点」の3つ。
- 諮る点は無い。E2E の全体が基準の 1.5 倍を超えたときと、外からの接続が `trust` に当たったときだけ、その時点で諮る。
