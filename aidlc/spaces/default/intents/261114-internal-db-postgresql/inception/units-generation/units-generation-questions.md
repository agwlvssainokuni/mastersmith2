# Units Generation の質問（Intent 261114-internal-db-postgresql）

Domain Design の部品（`inception/domain-design/components.md`）を、作る単位（Unit）にまとめます。この段は、何が何に頼るか（依存の図）だけを決め、作る順は Delivery Planning が決めます。

## 決まっていること（質問にしない）

- **配備の形**: アプリは1つの実行可能 WAR（`team.md`）。どの単位も別々に配備せず、同じ WAR か、リポジトリの設定・文書・テストに入る（埋め込み）。
- **ドメイン設計の決定**: 種類の判断は `InternalDbPlatform` の1か所（ADR-001）、PostgreSQL 用の移行は別の置き場に一式（ADR-002）、H2 にしか通らない SQL は両方に通る書き方に直し DB の種類で分岐しない（ADR-003）、外部キーは NO ACTION（ADR-004）、PostgreSQL の結合テストは新しく作り H2 の結合テストは変えない（ADR-005）。これらに反するまとめ方はしない。
- **作り方の前提**: `team.md` のとおり、作業は同じ作業フォルダの短命のブランチで行い、worktree を使わない。依存の図の上で独立した単位は「並行できる」と記録するだけにし、1つずつ作る前提にする（前の Intent と同じ扱い）。
- **単位の名前**: `u{n}-{説明}` の形（小文字・数字・ハイフン）。単位ごとに種類（`service`・`spec`・`ui`・`packaging`・`library`）を付ける。テストだけの単位のように当てはまる種類が無いときは種類を付けない（設計の段の成果物は全部の組になる）。
- **要件の割り当て**: User Stories を飛ばしたため、要件の FR1.1〜FR5.5（29件）を単位に対応づける。文書の変更（FR4.4・FR4.5）は Domain Design で部品を持たず、この段で単位に割り当てる。
- **変えない見込みの部品**（`AuthPersistence`・`InvitationPersistence`・`PersistenceFailureClassification`）は単位の中身を持たず、確かめは PostgreSQL の結合テストと機能設計の捨ての試しで行う。

## Q1. [設計] 単位の粒度とまとめ方

**推奨: A。** 理由: 確かめ方の違い（普段の `verify` の H2 で確かめる書き直し、明示して流す PostgreSQL の結合テスト、文書と compose）で分けると、各単位の合格の条件がはっきりします。PostgreSQL の移行は、同じ単位の中で実際の PostgreSQL で確かめないと、壊れていても後の単位まで気づけないため、結合テストの土台（明示の指定・コンテナの起動・移行と起動の確かめ）を u1 に入れます（Q2）。

A. 4単位にする（下の案）
B. 3単位にする: A の u3（PostgreSQL の振る舞いの結合テスト）を作らず、各テストを確かめる中身の単位（u1・u2）に入れる。単位は少ないが、u2 が PostgreSQL の結合テストの土台（u1）に頼るようになり、機能ごとの代表の流れ（FR3.1）の持ち主がはっきりしない
C. 5単位にする: A の u1 を「種類の判断と設定」と「PostgreSQL の移行の一式」に分ける。単位は小さくなるが、移行は種類の判断が置き場を選ばないと流れず、両方を確かめるには結局両方が要る
X. Other (please specify)

[Answer]: A

### A の案（4単位）

| 単位 | 種類 | 持つ部品 | 要件（FR） |
|---|---|---|---|
| u1-internal-db-platform | service | InternalDbPlatform・InternalDbMigration（PostgreSQL 用の一式・版の番号の突き合わせの単体テスト・Flyway の PostgreSQL の部品の依存）・InternalDbPostgresTestSuite の土台（明示の指定・コンテナの起動・移行と起動の確かめ） | FR1.1〜FR1.4・FR2.1〜FR2.5・FR3.3（接続の設定）・FR3.9・FR3.10・FR5.2・FR5.3・FR5.5 |
| u2-portable-sql | service | UserPersistence・RolePersistence・DslManagePersistence（H2 にしか通らない SQL の書き直し、大きなバイナリの写し方） | FR3.5・FR3.6・FR3.8 |
| u3-postgres-behaviour-tests | （種類なし） | InternalDbPostgresTestSuite の振る舞いの確かめ（違反の区分・待ちの上限・失敗の後のトランザクション・時刻・大きなバイナリ・検索・機能ごとの代表の API の流れ）と、AuthPersistence・InvitationPersistence の確かめ | FR3.1〜FR3.4・FR3.6〜FR3.8・FR5.1・FR5.4 |
| u4-local-runtime-docs | packaging | LocalContainerRuntime（compose の profile・`.github/dependabot.yml` の固定先）・文書（README・`docs/internal-db.md`・バックアップと戻しの手順・H2 だけの運用の明記） | FR4.1〜FR4.5 |

依存の見込み: u3 は u1（土台と PostgreSQL の起動）と u2（書き直した SQL）に頼る。u4 は u1（接続先の設定の決まりと対応する種類）に頼る。u1 と u2 は互いに頼らない（u2 の書き直しは H2 の結合テストの全件で確かめられる）。

## Q2. [品質] PostgreSQL の結合テストの土台（明示の指定・コンテナの起動）をどの単位に置くか

Q1 で A か C を選んだときに効きます（B では土台は u1 に入ります）。

**推奨: A。** 理由: PostgreSQL 用の移行の一式は普段の `verify` では流れないため、u1 の中で実際の PostgreSQL への移行と起動（FR2.1・FR2.4・FR2.5・NFR4）を確かめないと、u1 の合格を移行の正しさで判定できません。土台を u1 に置けば、u1 を終えた時点で PostgreSQL で起動できることが確かめられます。

A. 土台と、移行・起動・初期管理者・ヘルスチェックの PostgreSQL の結合テストを u1 に置き、振る舞いの結合テストを u3 に置く
B. PostgreSQL の結合テストは土台も含めてすべて u3 に置く（u1 は単体テストと H2 の結合テストだけで確かめ、PostgreSQL での移行の確かめは u3 まで待つ）
X. Other (please specify)

[Answer]: A
