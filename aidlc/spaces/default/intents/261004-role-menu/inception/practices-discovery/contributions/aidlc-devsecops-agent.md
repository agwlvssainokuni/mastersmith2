**Collaborator:** aidlc-devsecops-agent

## Contribution

セキュリティ担当（Step 3 の独立した確かめ）。リードの下書き（`team-practices.md`・`discovered-rules.md`・`evidence.md`）を、今のコード・設定と突き合わせた。gradlew・docker・npm・gh・git の書き込みは行っていない。`.env` などの秘密情報は開いていない。

### 1. 確かめたもの

| 種類 | 確かめたもの | 結果 |
|---|---|---|
| SAST | `backend/build.gradle.kts` の `spotbugs`・`spotbugsGate`（Effort MAX、reportLevel LOW、priority 1・`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` で失敗）、`backend/config/spotbugs-exclude.xml`（除外は `LoginAttemptStateRepository` の `PREDICTABLE_RANDOM` 1件だけ、理由つき） | team.md の Code Style と一致 |
| 秘密情報 | `.pre-commit-config.yaml`（Gitleaks）、ルートの `build.gradle.kts` の `gitleaksScan`（履歴全体・`--redact`・`.gitleaks.toml`）、`.github/workflows/ci.yml` | 一致 |
| 依存関係 | ルートの `build.gradle.kts` の `osvScan`（`backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`、`MAL-` は重大度によらず失敗、成果物を作る道具は `config/npm-build-tools.txt`）、`.github/dependabot.yml` | 一致 |
| CI のサプライチェーン | `ci.yml` の Actions はコミットのハッシュで固定、既定の `permissions: contents: read`、リリースのジョブだけ `contents: write` | 一致 |
| 画面のリンタ | `frontend/.oxlintrc.json`（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url`）、`frontend/eslint.config.js`（`no-implied-eval`）、`frontend/.npmrc`（`ignore-scripts=true`） | 一致。ただし下の G1 |
| 認可の仕組み | `config/SecurityConfig.java`、`common/security/ApiDefaultAccess.java`、`access/web/AdminAuthorizationManager.java`・`AdminSecurityContributor.java`、`access/domain/AdminPaths.java`、`access/testsupport/PublicApiTestRules.java` | 下の 2節 |
| N 階層のメニューの元 | `backend/src/main/resources/dsl/dsl-schema-v1.json` の `menuItem`（`label`・`icon`・`table`・`items`）、`frontend/src/app/navigation/navigationItems.ts`・`routing/decideRoute.ts` | 下の 2節 |

### 2. 今回の Intent で効く事実（コードで確かめたこと）

- F1. `/api/**` の既定は「ログインだけ」で、`/api/admin` と `/api/admin/**` だけが管理者の決まり（`SecurityConfig` の `requestMatchers("/api/**").authenticated()`、`AdminSecurityContributor`）。権限で守る API を `/api/admin/**` の外に足すと、決まりを書き忘れたときにログイン中の誰でも呼べる（既定が開いている形）。今は全部が `/api/admin/**` の下なので問題が表に出ていない。
- F2. 管理者の判定は、要求ごとに内部DBから作る主体 `AuthenticatedUser` の `admin()` だけを見る（`AdminAuthorizationManager`。トークンの中身・画面の値は使わない）。印を外した直後の次の要求で 403 になるのはこの形だから。権限をトークンに入れる設計にすると、アクセストークンの失効の仕組みを持たない決定（`project.md` の Decided）と組み合わさり、外した権限が有効期限まで残る。
- F3. 画面の出し分けは、ログインの応答 `CurrentUserResponse` の `admin` の真偽値で行っている（`navigationItems.ts` の `loginState.admin`、`decideRoute.ts`）。サーバー側の判定とは別で、画面だけの出し分けである（team.md の「認可」の行の考えどおり）。
- F4. N 階層のメニューの定義は DSL（利用者が投入する信頼できない入力）の中にあり、項目は `label`（ja・en の文字列）・`icon`（任意の文字列）・`table`（任意の文字列）を持つ。URL そのものは持たないが、`table` の名前から画面の道を組み立てるなら、`/`・`..`・`?`・`#`・`%` などを含む名前で道が変わりうる。`icon` は許す名前の一覧と照らしていない（K-37）。メニューの深さの上限は DSL 全体の入れ子の上限だけ。
- F5. `PublicApiTestRules`（order 250）はテストの中だけで `/api/**` を公開にする。新しい権限の決まりの order が 250 より大きいと、この仕組みを入れたテストで決まりが緩んで見えうる（本番には無い）。権限の決まりの order は既存の割り当て（機能ごとに 100 台、x00・x50 はテスト用）に従う必要がある。

### 3. 下書きに無い候補（面談で確かめる案）

#### G1. make-you-chic-ui の依存を入れるときにもパッケージのスクリプトを動かさない（サプライチェーン）

- 根拠: ルートの `build.gradle.kts` の `vendorInstall` は `vendor/make-you-chic-ui` で `npm ci --no-audit --no-fund` を実行し、`--ignore-scripts` を付けていない。`frontend/.npmrc` の `ignore-scripts=true` は `frontend/` の中でだけ効き、`vendor/make-you-chic-ui` には `.npmrc` が無い（中身はこのリポジトリから変えられない）。そのため、手元の `./gradlew verify` と CI で、make-you-chic-ui の依存のインストールのスクリプトが動く。team.md の Code Style の「npm でパッケージを入れるときは、パッケージのスクリプトを動かさない」の趣旨から外れている。今回は P5 のとおり make-you-chic-ui の固定先を上げる見込みが高く、新しい依存が入りうる。
- 推奨: 決まりの文言を「このリポジトリが行う npm のインストール（`frontend` と `vendor/make-you-chic-ui` の両方）でスクリプトを動かさない」に広げ、`vendorInstall` に `--ignore-scripts` を付ける（vendor の中身は変えない）。make-you-chic-ui のビルドがスクリプトに頼っていないかは、付けた Bolt で `./gradlew verify` が通ることで確かめる（今は確かめていない）。通らないときは、要るスクリプトだけを個別に動かす形を依頼者に諮る。
- 選択肢の例: A. 上の推奨（決まりを広げ、固定先を上げる Bolt か最初の Bolt で直す）／B. 固定先を上げるときだけ upstream の差分を読んで確かめ、設定は変えない／C. Other。

#### G2. 権限で守る API の網羅のテスト（既定が開いている形への手当て）

- 根拠: F1。認可の決まりを書き忘れた API は、テストを書いた API の 401／403／200 では見つからない（書き忘れた API にはテストも無いことが多い）。
- 推奨: Testing Posture の認可の項目に「アプリが持つすべての API（Spring MVC の対応表から列挙）が、公開・ログインだけ・管理の権限（または権限の名前）のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる」を足す。分類の一覧はテストの側に置き、足した API を一覧に足し忘れると落ちる形にする。仕組みの置き場（`SecurityRuleContributor` を広げるか `@PreAuthorize` か）は設計で決める。
- 選択肢の例: A. 足す（Testing Posture）／B. この Intent の要件・設計だけで扱う／C. 足さない。

#### G3. 役割の管理の操作で、対象を取り違える・組み込みの役割を壊す要求の拒否（IDOR・昇格）

- 根拠: P1 の「権限の昇格の防止」は自分の権限の範囲を扱うが、識別子を差し替えた要求（他人の割り当て・存在しない役割・組み込みの役割の ID）を明示していない。
- 推奨: P1 の一覧に次を足す。「役割・割り当ての ID を差し替えた要求で、存在しない対象は 404（または決めた code）で拒否し状態が変わらないこと」「組み込みの役割（管理の役割など、設計で決める）の削除・権限の取り上げは、最後の管理者の保護と同じく拒否され状態が変わらないこと」「役割の作成・変更の本文に、許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと（一括代入）」。★組み込みの役割の有無と範囲
- 選択肢の例: A. P1 に含めて足す／B. 要件で扱う。

#### G4. DSL のメニューの項目を信頼できない入力として確かめるテスト

- 根拠: F4。team.md の Code Style は DSL を信頼できない入力として扱うと決めているが、Testing Posture の DSL のテストは大きさ・深さ・別名・タグ・重複キー・`$ref`・拒否の応答で、表示や道の組み立てに使う値の確かめが無い。
- 推奨: P4（メニューの画面のテスト）に次を足す。「表示名に `<`・`>`・`&`・`"`・`'`・`<script>` を含めても文字として出る（HTML として描かない。`react/no-danger` の決まりを守る）」「`icon` は許す名前の一覧と照らし、一覧の外は拒否か既定のアイコンにする（★どちらにするか）」「`table` の名前に `/`・`..`・`?`・`#`・`%`・空白を含めても、画面の道がアプリの中の決めた形から外れない（エンコードする。外部の URL・`javascript:` の道を作らない）」「メニュー専用の深さの上限ちょうどは受け付け、超えると拒否（P4 の (3) と同じ）」。性質ベースのテスト（fast-check）を当てやすい純粋な関数にする。
- 選択肢の例: A. P4 に含めて足す／B. 要件・設計で扱う。

#### G5. 権限の判定の材料（サーバー側の正の置き場）

- 根拠: F2。今の「要求ごとに内部DBから読む」形は、印を外した直後の次の要求で拒否するという既存の必須のテストの前提になっている。権限をトークンに入れるかは設計の論点（リードの 5節）だが、P1 の「割り当ての変更の反映（次の要求で切り替わる）」と、トークンに入れる設計は両立しない。
- 推奨: P8 の固い制約の候補に「NEVER 権限の判定に、画面から送られた値やトークンの中の権限を使わない（要求ごとにサーバー側の正の情報から判定する）」を加えるか、少なくとも P1 の「次の要求で切り替わる」を固い前提として面談で確かめ、設計の選択肢から「トークンに入れて有効期限まで残す」を外す。★性能（要求ごとの読み取り）は NFR で見積もる。
- 選択肢の例: A. Forbidden として足す／B. P1 のテストの前提として確かめるだけ／C. 設計に任せる。

### 4. リードの候補へのセキュリティからの補足

- P1: 賛成。上の G2・G3 を加えたい。「停止中の利用者は権限があっても通らない」は、既存の Mandated（状態の判定を3つの入口すべてで行う）と重なるため、テストの側に残し固い制約には足さなくてよい。監査は、拒否した操作（403・昇格の拒否・組み込みの役割の保護の拒否）を残すかを★として明示したい（既存の「管理の操作の監査」の★と同じ扱い）。
- P2: 下の Positions のとおり、読み替えを Build and Test まで待つことには反対。あわせて、team.md の「要求の改ざん」の行が例に挙げる `/api/me` は今のコードに無い（K-35。ログイン中の利用者はログイン・更新の応答 `CurrentUserResponse` で返す）。一般化するなら「管理の API の外のすべての API（ログイン・トークンの更新・表示の設定の保存など）」と書くのがよい。
- P3: 賛成。役割の作成・割り当てを E2E で行うときも、自分で作った役割と利用者だけを対象にし、組み込みの役割と初期管理者の割り当ては変えない、と明示したい（E2E は内部DB を共有するため、組み込みの役割を変えると後のファイルの権限が変わる）。
- P4: 賛成。G4 を加えたい。
- P5: 賛成。固定先を上げる Bolt では、OSV-Scanner の対象の `vendor/make-you-chic-ui/package-lock.json` の差（新しく入る依存）を記録し、G1 の手当てを先に入れておくのが安全。
- P6: 賛成。`access` の境界テストは、今回の認可の変更で依存が増えたときに見える化する役に立つ。
- P7: 賛成。セキュリティの面では、前の版へ戻したときに `admin_flag` が新しい版の役割の割り当てと食い違うと、戻した直後に管理の権限が意図せず付く・外れる（権限のずれ）。設計で `admin_flag` を同期して持ち続けるか読まないだけにするかを決めるとき、「戻した版での管理の権限が、戻す直前の割り当てと一致するか」を Deployment Pipeline の戻しの確かめに入れることを推す。
- P8: 賛成（A）。言い回しの案: 「ALWAYS 役割・権限による判定は、API ごとにサーバー側で行い、メニュー・画面の出し分けを代わりにしない」「ALWAYS 役割・権限の割り当てと役割そのものの変更は、操作した人・対象・変えた中身・結果を監査に残す（拒否した操作を含めるかは要件で決める）」「NEVER 操作する人が持たない権限を、自分や他人に与える操作を受け付けない」。G5 の Forbidden も候補に加えたい。既存の Forbidden（最後の有効な管理者）を書き換えず読み替えを学びとして残す点にも賛成。
- P9: 賛成（A）。イメージのダイジェストでの固定はサプライチェーンの守りで、Dependabot の見ない固定先を揃える手順が team.md に無いと、上げ忘れで古い版（既知の脆弱性を持つ版）が残りうる。

### 5. 要件・設計に回す論点（セキュリティの面から、面談の候補にしない）

- 権限の粒度と名前、権限の決まりの置き場（`SecurityRuleContributor` の order の割り当て。F5 のとおり、テスト用の 250 との前後に注意）、403 の理由の値。
- メニューを返す API が、権限の無い項目（テーブルの名前を含む）を返すか。返さない形にするか（存在の漏えいをどこまで許すか）。
- 組み込みの役割の有無と、最後の管理者の保護の役割での数え方。
- 権限を要求ごとに読む形の性能（NFR）。
- 役割の管理の機能の監査の種類の名前（列の長さ `VARCHAR(32)`、K-38）。
- 新しく足す依存（画面の木の部品など）があれば、採用の前のライセンスの確認、OSV-Scanner、成果物を作る道具なら `config/npm-build-tools.txt` への追加（既存の決まりどおり）。

### 6. 残る不確かさ

- G1 で `--ignore-scripts` を付けたときに make-you-chic-ui のビルドが通るかは確かめていない（npm を実行していないため）。
- リードの 6節の Dependabot の残りのブランチ6つの重大度は確かめていない。High 以上なら team.md の決まりどおり次の Bolt の前に取り込む（進め方の変更の候補ではない）。OSV-Scanner は `./gradlew verify` を流したときに High 以上で止めるため、最後の守りはある。
- GitHub の既定のブランチ・secret scanning・push protection の設定は、ネットワークを使わない約束のため確かめていない。

## Positions

- OBJECT: P2 の推奨（読み替えを Build and Test まで待つ）には反対で、B（この段で文言を一般化する。例: 「管理者フラグなし（403）」→「その操作の権限を持たない（403）」）を推す。テストを書くのはコード生成で、Build and Test より前のため、待つとコード生成の Testing Contract が真偽値1つの前提の文言のままになる。一般化した文言は今の真偽値の形でも成り立つ。
- AGREE: P1・P3・P4・P5・P6・P7・P8・P9 に賛成する。ただし P1 に G2・G3、P4 に G4、P8 に G5 を足したい。
- OBJECT: 下書きの基準が「npm のスクリプトを動かさない」を `frontend` だけで満たしているとみなしている点には異を唱える。`vendorInstall` の `npm ci` には `--ignore-scripts` が無く、決まりの趣旨から外れている（G1）。
