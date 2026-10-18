**Collaborator:** aidlc-devsecops-agent

## Contribution

セキュリティ（リンタ・書式、静的・動的な検査、秘密情報と依存の検査、供給の連鎖の守り）の観点で、リードの下書き（`team-practices.md`・`discovered-rules.md`・`evidence.md` の P1〜P14）を独立に見た。読み取りだけで確かめたもの: `.github/dependabot.yml`・`.github/workflows/ci.yml`、`build.gradle.kts` の `gitleaksScan`・`osvScan`、`backend/build.gradle.kts` の `spotbugsGate`、`backend/config/spotbugs-exclude.xml`、`.gitleaks.toml`、`config/npm-build-tools.txt`、`backend/src/main/java/cherry/mastersmith/targetdb/`（`TargetDataSourceConfig`・`SqlIdentifiers`・`*SchemaQueries`）、`common/observability/UrlQueryStrippingObservationFilter.java`、`gh api` の読み取り（リポジトリの安全の設定）。

### 1. 今の守りの確かめ（変える候補は無い）

- CI の供給の連鎖の守りは保たれている。`ci.yml` の actions はすべてコミットの SHA で固定、Gitleaks 8.30.1・OSV-Scanner 2.6.0 は版と SHA-256 で固定、既定の `permissions` は `contents: read` で、`contents: write` はタグのときのリリースの1ジョブだけ。`dependabot.yml` に `github-actions` の項がある。
- GitHub の設定は公開のリポジトリで、secret scanning と push protection が有効（`team.md` のとおり）。Dependabot security updates は無効、`develop` の保護は無い（プルリクエストを使わない運用のため、今の基準と食い違わない）。
- `osvScan` の対象の lockfile は `backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` の3つで、基準どおり。
- `spotbugs-exclude.xml` の除外は今 `PREDICTABLE_RANDOM` の1件だけで、`SQL_` の除外は1件も無い。今の対象DB の問い合わせは固定の文（`*SchemaQueries` の定数）で、`SqlIdentifiers` は「今は使わない」入り口のまま（K-51）。業務データの問い合わせが、このリポジトリで初めて識別子を組み込む SQL になる。
- 対象DB の接続は `TargetDataSourceConfig` で `setReadOnly(true)` にしており、見本のアカウントも `SELECT` だけ（K-49）。読み取りは接続の印とアカウントの権限の二重の守りになっている。

### 2. リードの論点への意見

- **P1（E2E の時点）**: 推奨に同意。今回は権限の判定が中心のため、判定に触れるブランチを統合の前に流す形は守りの上でも要る。ただし「権限の判定に触れる」の読みを広く取り、`SecurityRuleContributor`・API の分類（`ApiAccessArchitectureTest` の対象）・`role` の解決の口・業務データの API のどれかに手が入るブランチを含めると書き足すのがよい。
- **P2（E2E の対象DB）**: 推奨に同意し、2点を足す。(1) E2E が使う対象DB の資格情報は、`project.md` の学び（`webServer.env` の値は json の報告に残る）どおりプロセスの環境変数で渡し、報告の json に接続情報が残らないことを確かめる。(2) E2E の対象DB のアカウントも読み取りの側は `SELECT` だけにし、書き込みを範囲に入れるなら書き込みの側は P5 と同じ最小の権限にする。見本の行の値は公開のリポジトリに入るため、テストデータの決まり（予約のドメイン・実在しそうな氏名を置かない）を見本の対象DB の初期化の SQL にも当てる。
- **P4（必須のテストの一覧）**: 推奨に同意し、次の3つの項目を一覧に足すことを推す（下の S2）。
- **P5（書き込みの接続とアカウント）**: 推奨に同意。足す点: 書き込みのアカウントの権限は、DSL が対象にするスキーマ（データベース）に限り、`*.*` やすべてのスキーマへの付与をしない。読み取りの側は今の二重の守り（`setReadOnly(true)` と `SELECT` だけのアカウント）を崩さず、業務データの読み取りも読み取りの接続を通す。書き込みの接続には `setReadOnly(true)` を付けない代わりに、書き込みの問い合わせを1か所の部品に閉じる（P6 と同じ部品）。
- **P6（SQL の組み立てと `SQL_` の除外）**: 方向に同意するが、除外の置き場の書き方に異議（Positions の OBJECT）。FindSecBugs の `SQL_INJECTION_*` は、組み立てた文字列を `prepareStatement`・`JdbcTemplate` などへ渡す呼び出しの所（受け口）で出る。引用の部品（`SqlIdentifiers`）で識別子を囲んでも、指摘はその文を実行するクラスに出るため、「除外は `SqlIdentifiers` に限る」と書くと、実行するクラスを除外できないか、決まりを外れて広く除外することになる。推す書き方: 業務データの SQL を「組み立てて実行する」部品を1つ（DB の種類ごとなら種類ごとに1つ）に閉じ、除外はそのクラスの名前・メソッドの名前・パターン名の3つで絞り（パッケージや `SQL_*` の全体では外さない）、理由と、識別子が DSL の名前の一覧を通ったものだけであることの根拠（`SqlIdentifiers.quoteKnown` を通すこと）を書く。除外を足す・広げることは、既存の ArchUnit の境界テストを緩めるときと同じく、コード生成の計画に明記して依頼者の承認を得る。並べ替えの向きは列挙で受け、文字列のまま SQL に入れない。SQL を組み立てる外部の部品（jOOQ など）は足さない形を先に検討し、足すなら採用の前のライセンスと依存の木（推移依存で既存の版を引き上げないか）を確かめる（今の基準）。
- **P7（行の値の漏えい）**: 方向に同意するが、Forbidden の文言のうち「主キーの値をトレースの属性に含めない」は、今の仕組みと食い違いうる（Positions の OBJECT）。`UrlQueryStrippingObservationFilter` は URL の問い合わせ（`?` の後）だけを落とし、`url.path`・`http.target` の道の部分は残す。詳細の API の道に主キーの値を置く形（例: `/api/data/{schema}/{table}/{pk}`）にすると、主キーの値がトレースの属性にそのまま残る。チームの進め方として、(a) 主キーの値を道に置かない（本文か問い合わせで渡す）か、(b) 業務データの API の `url.path`・`http.target` を道の型（ルートの雛形）に置き換える仕組みを足すか、のどちらかを設計の段で決め、外部エクスポートを有効にした結合テスト（既存の `OtlpLogExportIT` と同じ形）で確かめる、と書き足すことを推す。あわせて、対象DB の例外のメッセージにも行の値が入る（PostgreSQL の一意の違反の `Key (id)=(…)`、MySQL・MariaDB の `Duplicate entry '…'`、H2 の `MVStoreException` と同じ型の漏えい）ため、応答だけでなくアプリのログにも JDBC の例外のメッセージを出さず、SQLState・業者のエラーの番号・例外のクラスの名前だけを出す、と書き足す（今の Forbidden は「そのままエラー応答に含めない」でログを名指ししていない）。
- **P8（置き場と境界テスト）**: 推奨に同意。`TargetDbBoundaryArchitectureTest` が書き込み・`JdbcTemplate`・`@Transactional` を止めているのは、対象DB を壊さない守りでもあるため、緩める案を選ぶなら、緩める理由と代わりの守り（P5 の書き込みのアカウントと P6 の1か所の部品）を計画に並べて書くことを足すとよい。
- **P9（axe-core）**: 推奨に同意。axe-core は開発時の依存で、`osvScan` では警告にとどまり（`config/npm-build-tools.txt` に無い）、安全の関門には影響しない。`dependabot.yml` の `ignore` から外すときは、同じファイルのコメント（見送る理由と外す時期）も消し、今の基準の書き方を崩さない。`@axe-core/playwright` と `vitest-axe` が別の版の axe-core を持つと片方だけが新しい規則で動くため、上げた後に lockfile で axe-core の版が1つにそろっているかを確かめる。
- **P10（make-you-chic-ui への依頼と固定先の更新）**: 推奨に同意し、固定先 `b0a1e79` への更新に供給の連鎖の確かめを足す（下の S1）。
- **P11（不安定なテストの確かめ）**: 推奨に同意。安全の観点で足すことは無い。
- **P12（物差しを変えるとき）**: 推奨に同意。
- **P13（縮める側の戻し）**: 推奨に同意し、1点を足す。戻しに使う内部DB の複写にはパスワードのハッシュ値と監査の行が入るため、リポジトリの外（ホームの下、権限 700）に置き、リポジトリの中に置かない・コミットしない、と手順に書く（Gitleaks は H2 のファイルの中のハッシュ値を見つけられない。`project.md` の学びの置き場とそろえる）。`admin_flag` を消すことは認可の形の変更のため、戻したときの権限のずれの確かめ（今の基準の「広げてから縮める」の行）も縮める側に当てる。
- **P14（古い記述）**: 推奨に同意。

### 3. 足す論点

- **S1 サブモジュールの固定先を上げるときの供給の連鎖の確かめ**
  - 推奨: make-you-chic-ui（今回は `a9309c4` → `b0a1e79`）の固定先を上げる前に、(1) 新しいコミットが上流の公開のブランチ（`main`）から辿れること（宙に浮いたコミットでないこと）、(2) `vendor/make-you-chic-ui/package-lock.json` の前後の差（増えた・変わったパッケージ、`hasInstallScript` の印の付いた項目）を読み取りで確かめ、`vendorInstall`（`npm ci`、`--ignore-scripts` を付けない。`project.md` の学び）を流す前に記録する。インストールのスクリプトを持つパッケージが新しく増えたときは、取り込む前に依頼者に諮る。固定先の前後のハッシュの記録（`project.md` の Mandated）と同じ記録に書く。java-mustache-processor の固定先を上げるときも (1) と推移依存の確かめ（今の基準）を当てる。
  - 根拠: vendor の `npm ci` だけは `ignore-scripts` の守りの外で動き、悪意のあるパッケージ（`MAL-`）の判定は入れて動かした後の `osvScan` になる。今の基準は固定先の更新を「承認を得た専用のコミット」とするが、承認の前に何を確かめるかが書かれていない。

- **S2 業務データの必須のテストの一覧（P4）に足す項目**
  - 推奨:
    - 画面に出す値の扱い: 業務データの値は信頼できない入力として扱う。値に `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。N 階層のメニューの「信頼できない入力」の項目と同じ扱い（`react/no-danger` は oxlint で止めているが、値を `href` に入れる形はリンタで止まらない）。
    - 検索の値のワイルドカード: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が、そのまま一致の条件として働かない（エスケープして渡す）こと。
    - 取り出す行の上限: ページの大きさの上限（P4 の「上限と時間切れ」）に加えて、JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。対象DB の大きな表で一覧を開いてヒープが尽きる形（`project.md` の大きな入力のヒープの学び）を防ぐ。
  - 根拠: 業務データは利用者（または対象DB の別の書き手）が入れた値で、今の基準の一覧には値を画面に出すときの決まりが無い。

- **S3 業務データの SQL の静的な守りを ArchUnit でも固定するか**
  - 推奨: 業務データの機能の境界テストに、「`java.sql.Connection#prepareStatement`・`createStatement` を呼んでよいのは P6 の1か所の部品だけ」を書く。SpotBugs の `SQL_` の関門（受け口で止める）と、ArchUnit（呼んでよい場所を止める）の二重にする。
  - 根拠: SpotBugs の除外は一度入れると、そのクラスの中で後から足した組み立ての誤りも見逃す。呼んでよい場所を構造で絞れば、除外の範囲が広がらない。置き場と書き方は設計の段で決める。

### 4. 動的な検査について

- 今の基準（OWASP ZAP などの動的な検査とコンテナイメージの検査は採用しない）を変える候補は出さない。業務データの API の注入・IDOR は、P4 の一覧と S2 の結合テストで、3種類の対象DB に対して決定的に確かめる方が、手元のコンテナだけの配備先では費用に見合う。配備先が決まったときに見直す論点として残す。

## Positions

- AGREE: P1 権限の判定に触れるブランチを統合の前に E2E で確かめるのは守りの上でも要る（「触れる」の読みを広く書き足す）。
- AGREE: P2 見本の対象DB を1種類で前提にする形は妥当（資格情報はプロセスの環境変数で渡し、見本の行にもテストデータの決まりを当てる）。
- AGREE: P4 業務データの必須のテストの一覧と、discovered-rules の Mandated の2行（主キーの無いテーブルの拒否、見せないカラムを応答に含めない）を固い制約にする（S2 の3項目を足す）。
- AGREE: P5 書き込みの接続と資格情報を分け、DDL を与えない最小の権限にする（付与は DSL の対象のスキーマに限る）。
- OBJECT: P6 「`SQL_` の除外は `SqlIdentifiers` など1か所の部品に限る」は、FindSecBugs の指摘が SQL を実行する受け口に出るため成り立たず、除外は組み立てて実行する1か所のクラスにクラス・メソッド・パターン名で絞り、計画で承認を得る形に書き直すべき。
- OBJECT: P7 Forbidden の「主キーの値をトレースの属性に含めない」は、`UrlQueryStrippingObservationFilter` が道の部分を残すため主キーを道に置く設計と食い違い、道に置かないか道の型に置き換えるかを決める行と、JDBC の例外のメッセージをログにも出さない行を足すべき。
- AGREE: P8 新しい機能のパッケージに境界テストを足し、既存の境界テストは緩めない形を先に検討する。
- AGREE: P9 規則を外して axe の違反を通さない（`ignore` のコメントも消し、axe-core の版が1つにそろうかを確かめる）。
- AGREE: P10 依頼の文面を記録に置き上流のソースで確かめる流れ（S1 の供給の連鎖の確かめを足す）。
- AGREE: P11・P12・P14 の推奨のとおり。
- AGREE: P13 縮める側の戻しの手順（内部DB の複写はリポジトリの外に置きコミットしないと書き足す）。
