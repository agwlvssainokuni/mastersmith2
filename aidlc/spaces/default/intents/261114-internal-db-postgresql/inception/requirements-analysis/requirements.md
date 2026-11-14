# 要件定義: 内部DB を PostgreSQL でも動かせるようにする（Intent 261114-internal-db-postgresql）

## 1. 意図の分析

- 依頼: 内部DB（今は組み込みの H2）を PostgreSQL でも動かせるようにする。内部DB と対象DB（DSL の対象の業務DB）は別扱いのまま変えない。今回は PostgreSQL だけを対象とし、MySQL・MariaDB は後の Intent とする。[desc]
- 目的: アプリ自身のデータ（利用者・認証の状態・招待・DSL のプレビューと適用の履歴・ロールと権限の設定・グループ・監査）の置き場として、組み込みの H2 のほかに PostgreSQL を選べるようにする。今の H2 での利用と配備は何も変えない。[Q1]
- 種類: 既存の機能の拡張（内部DB の置き場の選択肢を足す）。範囲はバックエンドの内部DB の層全体と、ビルド・テスト・手元の配備・文書に及ぶ（`business-overview.md` の K-154、`architecture.md` の「内部DB の作り」）。
- 複雑さ: 標準。接続の切り替えそのものは環境変数で済む形だが（`architecture.md` の K-146）、H2 に頼る所が、移行（K-144）・ネイティブの SQL（K-145）・待ちの上限（K-147）・違反と排他の区分（K-148）・エンティティの写し方（K-149）・部品（K-150）・テスト（K-151）・運用（K-152）に分かれている（`code-structure.md`・`business-overview.md` の所見の一覧）。
- 成功の判定: 空の PostgreSQL を内部DB に指定してアプリが起動し、今の機能がすべて H2 と同じ応答で動くこと。H2 を内部DB とする今の起動・テスト・配備は、結果も時間も変わらないこと。

## 2. 機能要件

### FR1 内部DB の種類の選択

- **FR1.1** 内部DB の接続先を何も設定しないとき、内部DB は今と同じ組み込みの H2（ファイル）で動く。今の H2 の内部DB（前の版で作ったファイル）は、この Intent の版でもそのまま起動し、中身が変わらない。[Q1]
  - Given 前の版で作った H2 の内部DB のファイルがある / When この Intent の版で、接続先の設定を変えずに起動する / Then 起動に成功し、移行の検証が通り、既存の利用者でログインできる。
- **FR1.2** 内部DB の接続先（環境変数 `MASTERSMITH_DB_URL` と利用者・パスワードの環境変数）に PostgreSQL の JDBC の URL を指定したとき、内部DB は PostgreSQL で動く。DB の種類は接続先から決め、ほかの設定を足さずに選べる。[Q1]
  - Given 空の PostgreSQL のデータベースと、そこへの接続先を指定した `.env` / When アプリを起動する / Then 起動に成功し、ヘルスチェックが UP を返す。
- **FR1.3** H2 と PostgreSQL 以外の接続先（MySQL・MariaDB など）を内部DB に指定したときは、起動を止める。止めるときのログには、対応していないことと、対応している種類（H2・PostgreSQL）と、設定の名前を出し、接続先の URL・利用者・パスワードは出さない。[desc]
  - Given 内部DB の接続先に MySQL の URL を指定 / When 起動する / Then 起動に失敗し、ログに設定の名前と対応する種類が出て、URL と資格情報は出ない。
- **FR1.4** 内部DB の接続先と資格情報は環境変数（`.env`）だけから受け取る。画面・API・DSL からは受け取らない。（`project.md` の Forbidden、2026-11-14 の Practices Discovery の決定）

### FR2 内部DB の表の作成と移行

- **FR2.1** PostgreSQL の空のデータベースに対して、起動時の移行で、H2 と同じ表・列・制約・索引を作る。制約と索引の名前は H2 と同じにする（違反の区分が名前で照らすため。`architecture.md` の K-148）。[desc]
  - Given 空の PostgreSQL / When 起動する / Then 移行の V1〜V14 に当たるすべての表ができ、移行の履歴に記録される。
- **FR2.2** H2 の適用済みの移行（V1〜V14）は書き換えない。H2 の内部DB での移行の検証（`validate-on-migrate`）は、この Intent の版でも通る。PostgreSQL の移行は、H2 の移行と別に置く。[desc]（`code-structure.md` の K-144）
- **FR2.3** H2 と PostgreSQL の移行は、同じ版の番号で同じ表の形を作る。後の Intent で表の形を変えるときは、両方の移行を同じ版の番号で足す。
- **FR2.4** 移行が PostgreSQL で失敗したときは起動を止め、失敗した移行の版の番号をログに出す。資格情報は出さない。
- **FR2.5** PostgreSQL の内部DB を空から始めたとき、初期管理者は今の H2 の初回の起動と同じ決まり（設定が有れば1回だけ作る。2回目以降の起動で重ねて作らない）で作る。[Q2]

### FR3 PostgreSQL での振る舞いを H2 と同じに保つ

- **FR3.1** 内部DB を使うすべての機能（ログイン・トークン・パスワードの変更・利用者の管理・招待・ロールと権限・作業ロール・グループ・メニュー・DSL の読み込みと適用・表示の設定・監査）は、PostgreSQL のときも H2 のときと同じ状態コード・`code`・応答の項目を返す。
- **FR3.2** 違反の区分を H2 と同じに保つ。名前・メールアドレスの重なり（一意の制約・主キー）、親の無い子の追加、参照のある行の削除（ロール・利用者・グループの削除で、参照が残っている場合）は、PostgreSQL のときも H2 のときと同じ応答（状態コードと `code`）になる。下調べで、PostgreSQL の `ON DELETE RESTRICT` の外部キーでは今の区分の部品が想定外の失敗に分けることが分かっている（SQLState 23001）。この差を無くす。[desc]（`architecture.md` の K-148）
  - Given 利用者に割り当てのあるロール / When そのロールを削除する / Then H2 のときと同じ状態コードと `code` で拒否され、ロールは残る。
- **FR3.3** 待ちの上限を H2 と同じに保つ。行の排他の待ちは 3 秒、書き込みの待ち・確定前の同じ一意の鍵への追加の待ちは約 2 秒で切れ、H2 のときと同じ応答（排他の失敗の区分）になる。PostgreSQL で上限なく待ち続ける経路を残さない。[desc]（`architecture.md` の K-147）
  - Given 別のトランザクションが利用者の行を書き込み中 / When 管理者がその利用者を止める操作をする / Then 約 2 秒で H2 のときと同じ失敗の応答が返り、待ち続けない。
- **FR3.4** トランザクションの中で SQL が失敗したとき、PostgreSQL でも書きかけの変更を残さず、失敗の監査は今と同じく別のトランザクションで記録される。[desc]（下調べの4節）
- **FR3.5** H2 にしか通らない書き方の SQL（`FINAL TABLE (UPDATE ...)` の1か所、`MERGE INTO ... KEY` の2か所、`MERGE INTO ... USING (VALUES ...)` の1か所、`CAST(? AS ...)` を含む `INSERT ... SELECT`）を使う機能は、PostgreSQL でも同じ結果になる。H2 での結果は変えない。[desc]（`business-overview.md` の K-145）
- **FR3.6** 大きなバイナリとハッシュの値を、PostgreSQL でも H2 と同じに保存し読み出す。DSL の本文は今の上限（10MB）ちょうどまで保存・読み出しができ、トークンのハッシュ（32 バイト）は1バイトも変わらずに照らせる。エンティティの写し方と表の列の型は、起動時のエンティティと表の照合（`ddl-auto: validate`）が通る形にする。[desc]（`code-structure.md` の K-149）
- **FR3.7** 時刻（`TIMESTAMP WITH TIME ZONE` の列）は、PostgreSQL でも書いた時点と読み出した時点が同じになる（時差の設定に左右されない）。有効期限・ロックの解除など時刻で決まる判定は、H2 のときと同じ結果になる。
- **FR3.8** 利用者の検索（部分一致・大文字と小文字を区別しない）は、PostgreSQL でも H2 と同じ利用者を返し、検索の値の `%`・`_`・`\` は一致の条件として働かない。
- **FR3.9** ヘルスチェックは、内部DB が PostgreSQL のときも、届けば UP、届かなければ DOWN を返す。時間の上限は今と同じ。
- **FR3.10** 内部DB の PostgreSQL への接続に失敗したとき（起動時・動作中）、アプリのログに原因（例外のクラスの名前・SQLState）が出る。接続先の URL・利用者・パスワードは出さない。今の対象DB の PostgreSQL のドライバーのログを抑える設定が、内部DB の失敗の原因まで隠さないようにする。（`architecture.md` の K-146）

### FR4 手元の配備と運用

- **FR4.1** 手元の compose に、内部DB 用の PostgreSQL を、見本の対象DB とは別のサービス・別のボリューム・別の資格情報で置く。既定では起動せず、profile などで選んだときだけ起動する。[Q4]（2026-11-14 の Practices Discovery の決定）
- **FR4.2** 内部DB 用の PostgreSQL のイメージは、見本の対象DB の PostgreSQL と同じ版とダイジェストに固定し、固定先を `.github/dependabot.yml` の固定先の一覧に足す。（2026-11-14 の Practices Discovery の決定）
- **FR4.3** 手元の配備の既定の内部DB は H2 のまま変えない。[Q1]（2026-11-14 の Practices Discovery の決定）
- **FR4.4** PostgreSQL の内部DB のバックアップと戻し（`pg_dump` などの道具を使う）の手順を文書に書く。H2 だけの運用（ファイルの詰め直し `DEFRAG_ALWAYS`・`docker/hikari-pool.sh` の compact・`perf/h2-shell.sh`・ボリュームの `tar` の戻し・`-Dh2.compactThreads=1`）は、H2 のときだけのものと文書に明記する。（`business-overview.md` の K-152）
- **FR4.5** 内部DB を PostgreSQL で動かす手順（接続先の設定・compose の選び方・対応する版）を README と `docs/internal-db.md` に書く。対応する版は確かめた版（今は 18 系）だけと書く。[Q5]

### FR5 テスト

- **FR5.1** PostgreSQL に固有の振る舞いを確かめる結合テストを、H2 の結合テストとは別のバリエーションとして作る。少なくとも、FR2.1・FR2.5・FR3.2・FR3.3・FR3.4・FR3.5・FR3.6・FR3.7・FR3.8・FR3.9 を、版を固定したコンテナの実際の PostgreSQL で確かめる。（`project.md` の Mandated、`team.md` の Testing Posture）
- **FR5.2** PostgreSQL の結合テストは、Gradle の明示の指定を付けたときだけ流れる。通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では流れない。CI では、手動の実行で明示したときに流せる。（`team.md` の Testing Posture）
- **FR5.3** 明示して流したときにコンテナの実行環境が無いと、警告を出して PostgreSQL の結合テストを飛ばす（黙って飛ばさない）。明示して CI で流したときは飛ばさない。（`team.md` の Way of Working）
- **FR5.4** H2 の結合テストの全件は残し、件数を減らさない。H2 の結合テストの結果は、この Intent の前と同じ（全件が通る）。

## 3. 非機能要件

- **NFR1 性能**: 性能・資源の目標（応答時間・メモリなど）は H2 の内部DB のときの目標のまま変えない。PostgreSQL のときの値は、測ったら基準の値として記録するだけで、合否には使わない。測るかどうかと測り方は NFR 要件の段で決める。[Q6]
- **NFR2 セキュリティ**: 内部DB の接続情報（接続先・利用者・パスワード・資格情報を含む JDBC の URL）を、ログ・監査ログ・トレースの属性・エラー応答に含めない（`project.md` の Forbidden）。違反の文に入る重なった値（メールアドレスなど。PostgreSQL では `Key (email)=(...)` の形）は、今と同じくログ・応答に出さない（下調べの2節、`project.md` の Code Style）。JDBC の例外のメッセージをそのままエラー応答に含めない。
- **NFR3 対応の範囲**: 内部DB として対応するのは、組み込みの H2（今の版）と PostgreSQL の確かめた版（今は 18 系。イメージは対象DB と同じ `postgres:18.6` のダイジェスト）だけとする。[Q5]
- **NFR4 信頼性**: PostgreSQL で上限なく待ち続ける経路を残さない（FR3.3）。起動時の移行は、接続ごとに設定する待ちの上限によって途中で切れない形にする。
- **NFR5 検査の時間**: 通常の `./gradlew verify`・`./gradlew verifyQuick`・CI の時間は、PostgreSQL の結合テストを足しても増やさない（流れないため）。CI の検査の job は `team.md` の線（約 42 分）の内に収める。
- **NFR6 カバレッジ**: PostgreSQL のときだけ通るコード（DB の種類ごとの書き分け・区分など）を足しても、通常の `./gradlew verify`（PostgreSQL の結合テストを流さない）だけで、バックエンドの全体とすべてのパッケージの下限（行 80%・分岐 70%）を満たす。下限を満たすために計測の除外を増やさない。（`team.md` の Testing Posture）
- **NFR7 インスタンス**: PostgreSQL のときも、アプリのインスタンスは1つの前提のままとする。[Q3]
- **NFR8 依存**: 足す依存（Flyway の PostgreSQL の部品など）は、採用の前にライセンスを確かめ、lockfile で版を固定し、依存関係の脆弱性検査（OSV-Scanner）の対象に入れる。推移依存で既存の部品の版を引き上げないかを依存の木で確かめる。（`team.md` の静的解析とセキュリティ検査、`project.md` の Corrections）
- **NFR9 構造**: 既存の ArchUnit の境界テストを緩めない。DB の種類ごとの差は repository の層（と移行の置き場）に閉じ、service の層は DB の種類で分岐しない。（`team.md` の Code Style）

## 4. 制約

- 内部DB と対象DB は別扱いのまま変えない。対象DB の接続・プール・テスト・振る舞いは変えない。[desc]
- 今回は PostgreSQL だけ。MySQL・MariaDB は後の Intent。[desc]
- 配備先は開発者の PC 上のコンテナだけ（`team.md` の Deployment）。
- DB スキーマの変更は前進のみとし、1つ前の版のアプリが動く後方互換を保つ（`team.md` の Deployment）。H2 の適用済みの移行は書き換えない。
- 使う道具の版: Spring Boot 4.1.2・Hibernate ORM 7.4 系・Flyway 12.4.0・PostgreSQL の JDBC ドライバー 42.7.14（`technology-stack.md`・`dependencies.md`）。

## 5. 前提（確かめていないこと・既定の読み）

- アプリは PostgreSQL に直接つなぐ。接続をまとめる中継（PgBouncer の transaction モードなど）は挟まない（接続ごとの設定が効く前提のため）。（下調べの 5.3 節）
- 手元の PostgreSQL への接続は暗号化（TLS）を求めない。必要なら接続先の URL のオプションで指定できる。理由: 配備先が開発者の PC 上のコンテナだけのため。
- PostgreSQL の分離レベルは既定（READ COMMITTED）で、H2 と同じ。排他の後に数える設計（最後の管理者を無くさない判定など）は、PostgreSQL の結合テストで確かめる（下調べの 5.3 節）。
- PostgreSQL の内部DB は、アプリ専用のデータベースと利用者を使い、ほかのアプリと共有しない。
- 下調べ（`aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`、調べた時点 `8eec0ba4`）の後に、関わるファイルの変更は無い（`business-overview.md` の K-153）。

## 6. 範囲の外

- MySQL・MariaDB を内部DB にすること（後の Intent）。[desc]
- H2 から PostgreSQL へのデータの移し替え（道具・手順とも）。PostgreSQL の内部DB は空から始まる。[Q2]
- 既定の内部DB を PostgreSQL に変えること。[Q1]
- アプリを複数のインスタンスで動かすこと。[Q3]
- 確かめた版（18 系）以外の PostgreSQL への対応の約束。[Q5]
- PostgreSQL の内部DB で E2E を流すこと（E2E は H2 だけ。2026-11-14 の Practices Discovery の決定）。
- PostgreSQL のときの性能の目標の合否の判定。[Q6]
- 対象DB の変更。クラウドなどの配備先。

## 7. 未決の点（後の段で決める）

- 移行の置き場の分け方（DB の種類ごとの置き場、Flyway の `locations` の切り替えなど）と、DB の種類の見分け方の実装（ドメイン設計・機能設計）。
- `ON DELETE RESTRICT` の差を無くす方法（PostgreSQL の外部キーを指定なしにするか、区分の部品で 23001 を扱うか）（機能設計・NFR 設計）。
- 接続ごとの待ちの上限（`lock_timeout`）の渡し方と、起動時の移行が途中で切れない形（NFR 設計）。下調べの 5.3 節のとおり、`lock_timeout` は接続のすべての文に効く。
- DSL の本文の列とエンティティの写し方（`@Lob byte[]` と `bytea`）、招待中のメールアドレスの一意（V8 の生成列）の PostgreSQL での形（機能設計。捨ての試しで確かめる）。
- PostgreSQL の結合テストの明示の指定の名前と、CI の手動の実行の入力の形（設計）。
- PostgreSQL の結合テストの数と範囲（FR5.1 を満たす最小の組）、テストの部品の H2 に頼る書き方（`INFORMATION_SCHEMA.SESSIONS`・Java のトリガー・`DATEADD` など）の書き分け（`code-quality-assessment.md` の K-151）。
- Flyway の PostgreSQL の部品のライセンスと、Spring Boot の BOM が版を管理するか（NFR 要件）。
- PostgreSQL のときの性能を測るかどうかと測り方（NFR 要件）。

## Sources

- Initial description: `aidlc engine workspace project-description` の出力（`<record>/project-description.json`）[desc]
- 質問への答え: `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements-analysis-questions.md`（Q1〜Q6、すべて A）
- Practices Discovery の決定: `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/practices-discovery/team-practices.md`・`discovered-rules.md`（承認済み。`team.md`・`project.md` に反映済み）
- コードの知識ベース（2026-11-14 の Reverse Engineering で更新）: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見の一覧 K-144〜K-154）・`architecture.md`（内部DB の作り K-146〜K-148、K-154）・`code-structure.md`（K-144・K-149）・`code-quality-assessment.md`（K-151〜K-153）・`technology-stack.md`・`dependencies.md`（K-150）
- 下調べ: `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`
- 読んだ知識の置き場（`team.md` の Knowledge Pointers）: `aidlc/spaces/default/knowledge/aidlc-shared/` の `checklist-auth-audit.md`・`checklist-business-data.md`・`checklist-dsl.md`・`checklist-mail.md`・`checklist-nav-menu.md`・`checklist-reference.md`・`checklist-roles.md`・`checklist-timezone.md`・`ops-notes.md`・`study-internal-db-postgresql.md`
- 決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/inception.md`
