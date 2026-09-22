**Collaborator:** aidlc-developer-agent

## Contribution

支援レビュー（開発者の観点: 命名、層の境界、エラー処理、ファイル・パッケージ構成、コード規約）。
新規プロジェクトのためアプリのコードはなく、根拠は依頼者自身のデザインシステム
`vendor/make-you-chic-ui`（読み取りのみ）と Ideation の成果物に限られる。以下の提案はすべて
【要確認】扱いであり、面談で人間が決めるまで確定ではない。

### 1. 追加の根拠（make-you-chic-ui から読み取れる依頼者のコード慣習）

主担当の E12・E13 を補う。いずれも `vendor/make-you-chic-ui` を直接読んで確認した。

| # | 対象 | 分かったこと |
|---|---|---|
| D1 | `packages/make-you-chic-ui/src/components/` | 部品ごとにディレクトリを切り、`Button/Button.tsx`・`Button/Button.css`・`Button/Button.test.tsx`・`Button/index.ts`（再エクスポート）の4点を置く。フックは `useXxx.ts`、コンテキストは `XxxContext.tsx`、画面を持たない純粋なロジックは `tableLogic.ts` のように分けて `tableLogic.test.ts` で単体テストする |
| D2 | `packages/make-you-chic-ui/src/index.ts` | 公開 API は単一の入口（barrel）から名前付きで再エクスポートする。`export default` はリポジトリ内に1件もない |
| D3 | `Button.tsx`、`LoginPage.tsx` | Props は `XxxProps` という interface。選択肢は `enum` ではなく文字列リテラルの union（`'primary' \| 'secondary'`）。`enum` の使用は0件。画面は `function LoginPage(): React.JSX.Element` のように関数宣言＋明示的な戻り値型、イベント処理は `handleXxx` |
| D4 | `tsconfig.base.json`、`.oxlintrc.json` | `strict`、`noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch` を有効化。`typescript/no-explicit-any` は error、未使用引数は `_` 始まりのみ許可 |
| D5 | 各 `*.css`、`.stylelintrc.json` | CSS は部品と同じ場所に素の CSS で置く（CSS-in-JS なし）。クラス名は接頭辞付き（ライブラリは `mycui-`、サンプルアプリは `sample-`）で、アプリ側は `sample-login-page__card` のような BEM 風。部品の CSS は色の基本トークンを直接参照せず、意味づけしたトークンだけを使う（stylelint の個別ルールで強制） |
| D6 | コメントと文言 | ソースコード中のコメント・JSDoc は英語、画面の文言（「ログイン」「パスワード」）とコミットメッセージ・ドキュメントは日本語 |
| D7 | エラー処理 | 誤った使い方（Provider の外での呼び出し、未知のアイコン名）は例外を投げず `console.warn('[make-you-chic-ui] ...')` で警告して安全な既定値で続行する（fail-soft）。`localStorage` の例外は捕捉して既定値に戻し、その振る舞いをテストで確認している |
| D8 | `packages/sample-app/` | 画面は `pages/XxxPage.tsx`、再利用する画面パターンは `screen-patterns/Xxx/`。ルーティングは `react-router`（v8 系）で、ログイン画面を AppShell の外に置く「レイアウトルート」方式（project.md の DECIDED と一致） |
| D9 | `docs/integration-guide.md` | 利用側は `file:vendor/make-you-chic-ui/packages/make-you-chic-ui` で参照する。Vite ではReact の二重読み込みを防ぐため `resolve.dedupe` の設定が必須と明記されている |
| D10 | `git log`（make-you-chic-ui） | ライセンスヘッダーを `/**` から `/*` に揃える修正（`54d0407`）がある。ヘッダーを文書コメント（Javadoc/JSDoc）と区別する意図と読める |
| D11 | リポジトリのルート | `.editorconfig`・`.gitattributes` はこのリポジトリにも make-you-chic-ui にもない。文字コード・改行コードを機械的に揃える仕組みはまだない |

### 2. リポジトリ内の配置（主担当の P19 に対する具体案）

- 【提案】ルート直下を `backend/`（Java）と `frontend/`（React + TypeScript）に分け、それぞれに
  ビルド設定・フォーマッタ・リンタの設定を置く。ルートには共通の `.editorconfig`（UTF-8、LF、
  末尾改行、インデント幅）と `.gitattributes`（`* text=auto eol=lf`）を置き、主担当の
  「UTF-8・LF」の提案を機械的に守れる形にする（D11）。
- 【注意】フロントエンドをルート直下に置いて `prettier --write .` や `oxlint .` を実行すると、
  サブモジュール `vendor/make-you-chic-ui` まで書き換え・検査の対象になる。discovered-rules.md の
  候補F3（サブモジュールを直接変更しない）を採用するなら、`frontend/` に分けるか、各ツールの
  除外設定に `vendor/` を必ず入れる。
- 【提案】フロントエンドの Prettier・oxlint・ESLint・Stylelint・tsconfig の設定は、
  make-you-chic-ui の設定ファイルを参照（extends）せず、同じ値を `frontend/` 側に複製する。
  サブモジュールの更新でこちらの規約が意図せず変わるのを避けるため。

### 3. フロントエンド（React + TypeScript）の規約案

主担当の Code Style（P16）への追加。make-you-chic-ui の慣習（D1〜D8）に揃えることを基本とする。

- 命名: 部品・画面は PascalCase のファイル名（`LoginPage.tsx`）、フックは `useXxx.ts`、
  Props は `XxxProps`、選択肢は `enum` を使わず文字列リテラルの union（D3）。
- エクスポート: 名前付きエクスポートのみ（`export default` を使わない）（D2）。
- 型: tsconfig の `strict` 系設定を make-you-chic-ui と同じにし、`any` を禁止する（D4）。
- 配置（【要確認】2案）:
  - 案A（機能別・推奨）: `src/app/`（ルーティング、Provider の組み立て）、`src/pages/`（画面）、
    `src/features/auth/`（ログイン・トークン更新・ログアウトの API 呼び出し、認証状態のフックとコンテキスト）、
    `src/api/`（HTTP クライアントの共通部分）、`src/components/`（アプリ固有の部品）。後続Intent
    （F〜K）で機能が増えても `features/<機能名>/` を足すだけで済む。
  - 案B（種類別）: `components/`・`hooks/`・`services/` のように種類で分ける。小規模なうちは単純だが、
    機能が増えると関連ファイルが散らばる。
- テスト: 対象と同じ場所に `*.test.ts(x)`（主担当の提案どおり）。画面を持たないロジック
  （トークン更新の判定など）は D1 の `tableLogic.ts` と同様に分離して単体テストする。
- CSS: 部品と同じ場所に素の CSS。アプリ用のクラス接頭辞（例: `ms-`）と BEM 風の命名を決める（D5）。
- エラー処理（【要確認】）:
  - API 呼び出しは `src/api/` の共通クライアント1か所に集め、HTTP エラーを型付きのエラー
    （例: `ApiError`、サーバーの `code` を保持）に変換する。401 時のトークン更新と1回だけの再試行もここで扱う。
  - 画面は捕捉したエラーを make-you-chic-ui の `Alert` / `Toast` で利用者に示す。`catch` して何もしない
    コードは書かない（construction.md の「黙って失敗しない」に対応）。
  - make-you-chic-ui の fail-soft（D7）は部品ライブラリの誤用に対する方針であり、認証・通信の失敗には
    適用しない（認証の失敗は必ず利用者に示すかログイン画面へ戻す）。

### 4. バックエンド（Java / Spring Boot）の規約案

依頼文のとおり Java / Spring Boot（JWT、SLF4J/logback、Actuator）を前提とする。IDE の設定は JDK 25（E8）。

#### 4.1 ビルドツール（前提となる決定）
- 【要確認】Gradle（Kotlin DSL）か Maven か。フォーマッタ・静的検査・カバレッジのプラグインの
  書き方がこれで決まるため、遅くとも最初の Bolt（土台）より前に決める必要がある。本ステージで
  決めないなら、どのステージで決めるかを明記しておく。

#### 4.2 パッケージ構成（主担当の P19 に対する具体案）
- 【要確認】ルートパッケージ名。依頼者の他の Java 資産に既定の名前がなければ、候補は
  GitHub アカウントを逆ドメイン化した `io.github.agwlvssainokuni.mastersmith`、または依頼者の好む短い名前。
  リポジトリから推測できないため、面談で必ず聞く。
- 【要確認】分け方（2案）:
  - 案A（機能別＋機能内で層を分ける・推奨）: `…mastersmith.auth`（ログイン、トークン、ロック）、
    `…mastersmith.user`（ユーザー、初期管理者の作成）、`…mastersmith.audit`（監査ログの記録）、
    `…mastersmith.common`（例外、エラー応答、ログの共通部品）、`…mastersmith.config`（Spring の設定）。
    各機能の中を `web`（コントローラー、要求・応答 DTO）/ `service`（業務処理・トランザクション）/
    `domain`（エンティティ、値）/ `repository`（DB アクセス）に分ける。後続Intent（D〜K）が
    機能パッケージを足していく形になり、Intent 単位の変更範囲が見えやすい。
  - 案B（層別）: `controller`・`service`・`repository`・`entity` を最上位に置く。Spring の入門例に多く
    単純だが、機能が増えると1つのパッケージが肥大化し、機能間の依存が見えにくくなる。
- テストは Maven/Gradle の標準どおり `src/test/java` に同じパッケージ構成で置く（フロントエンドの
  「同じ場所に置く」とは異なることを team-practices.md に明記しておくと混乱しない）。

#### 4.3 層の境界（【要確認】）
- コントローラーは HTTP の受け渡し（入力検証 `@Valid`、DTO と業務オブジェクトの変換、状態コード）だけを行い、
  リポジトリを直接呼ばない。
- トランザクション境界（`@Transactional`）はサービス層にだけ置く。
- エンティティを API の応答として直接返さない。要求・応答は Java の `record` の DTO（`XxxRequest` / `XxxResponse`）にする。
  パスワードハッシュなどの項目が誤って応答に載る事故を構造的に防げる。
- 依存性の注入はコンストラクター注入のみとし、フィールドへの `@Autowired` は使わない。
- 監査ログ（内部DBへの記録、S9）とアプリのログ（構造化ログ、S10）は別の仕組みとして分ける。
  監査記録を logback の出力で代用しない（S9 の「漏れなく DB に記録」をテストで確認できる形にするため）。
- 【要確認】境界をテストで機械的に守らせるか（ArchUnit などの構造検査、または Spring Modulith）。
  1名体制で人手のレビューが限られるため、案A を採る場合は有効な歯止めになる。

#### 4.4 命名（【要確認】）
- クラス: `XxxController`、`XxxService`、`XxxRepository`、例外は `XxxException`、定数は UPPER_SNAKE_CASE。
- テストクラス: 単体テストは `XxxTest`、DB や Spring を起動する結合テストは `XxxIT`（または `XxxIntegrationTest`）
  とし、ビルドで分けて実行できるようにする。
- REST の URL は `/api/` 始まり、小文字のケバブケース（例: `/api/auth/login`、`/api/auth/refresh`）。
  JSON の項目名は camelCase。DB のテーブル名・列名は snake_case。

#### 4.5 エラー処理（【要確認】）
- 例外は非検査例外の独自の階層（例: 業務エラーの基底クラス）にまとめ、`@RestControllerAdvice` の1か所で
  HTTP 応答へ変換する。個々のコントローラーで try-catch して応答を組み立てない。
- 【要確認】エラー応答の形式（2案）:
  - 案A（推奨）: RFC 9457 Problem Details（Spring の `ProblemDetail`）に、画面側が分岐に使う安定した `code` 項目を足す。
    Spring Boot の標準機能で扱え、独自形式を設計・保守する必要がない。
  - 案B: 独自のエラー応答形式（`{ code, message, details }` など）。自由度は高いが、フレームワーク標準の
    エラー（404 や検証エラー）との形式を揃える手間がかかる。
- 認証の失敗（パスワード誤り・存在しないユーザー）は同じ応答・同じメッセージにする（ユーザーの存在を
  推測させない）。ロック中は別扱いにするかを決める。401（未認証・トークン無効）と 403（管理者でない）を区別する。
- 例外のログは境界（`@RestControllerAdvice`）で1回だけ出す。想定内の業務エラー（4xx）は WARN 以下で
  スタックトレースなし、想定外（5xx）は ERROR でスタックトレース付き。画面への応答には内部の例外メッセージや
  スタックトレースを載せない。
- 例外を捕捉して何もしない（握りつぶす）コードは書かない。捕捉するなら変換して再送出するか、理由をコメントに書く。
- 検索系の戻り値は `null` ではなく `Optional` を使う。【要確認】null 許容の注釈（JSpecify の `@Nullable` /
  `@NullMarked`）を採用するか。

#### 4.6 ログ（【要確認】）
- ロガーは SLF4J。宣言を `private static final Logger log = LoggerFactory.getLogger(Xxx.class)` と手書きするか、
  Lombok（`@Slf4j` など）を使うか。Lombok の採否はログ以外（getter、コンストラクター）にも影響するため、
  採用するかどうかを1つの決定として扱う。`record` とコンストラクター注入を使えば Lombok なしでも記述量は抑えられる。
- 構造化ログの項目は、メッセージの文字列連結ではなくキーと値（SLF4J の `addKeyValue` や MDC）で渡す。
  トレースID の埋め込みは S10 の設計で決める。
- パスワード・トークン・リフレッシュトークンはログ・監査ログ・例外メッセージに出さない
  （discovered-rules.md の候補F1。エラー処理と直結するため、開発者としても採用を支持する）。

#### 4.7 フォーマッタと静的検査（主担当の P17 への補足）
- 【要確認】フォーマッタは Spotless で実行する前提で、次から選ぶ。
  - palantir-java-format: インデント4、1行120文字。google-java-format より行の折り返しが読みやすい。
  - google-java-format: インデント2、1行100文字。フロントエンドの Prettier（インデント2、100文字）と見た目が揃う。
  - IntelliJ の既定スタイル: インデント4、1行120文字。依頼者の IDE（E8）と一致するが、CI で同じ整形を再現しにくい。
- Spotless はライセンスヘッダーの検査・挿入（`licenseHeader`）もできる。project.md の Mandated
  （Apache License 2.0 ヘッダー）を Java では CI で機械的に確認できる。TypeScript・CSS 側でも同じ確認を
  入れるか（リンタのプラグインや簡単な検査スクリプト）を決める。
- 静的検査の候補: Checkstyle（命名・書式）、Error Prone（よくある誤りの検出、NullAway と組み合わせて null 検査）。
  フォーマッタで扱う項目を Checkstyle で重ねて検査しない（org.md の「リンタで扱う項目は重ねて指摘しない」）。
  セキュリティ向けの静的解析（SpotBugs + FindSecBugs 等）は devsecops の担当範囲として扱う。
- `javac` の警告（`-Xlint:all`）をエラー扱いにするかを決める。

#### 4.8 ライセンスヘッダーの記法（主担当の P18 への根拠の追加）
- `/* ... */` に揃える提案を支持する。make-you-chic-ui で `/**` から `/*` へ直した履歴（D10）に加え、
  Java では `/**` で始まるヘッダーは文書コメント（Javadoc）として扱われ、JDK 23 以降の `javac` は
  どこにも付かない文書コメントを `-Xlint:dangling-doc-comments` で警告する。JDK 25 を使う本プロジェクトでは
  `/*` が技術的にも正しい。Spotless の `licenseHeader` の既定も `/*` 形式である。

### 5. コメントの言語（主担当の P20 への根拠の追加）
- make-you-chic-ui ではコード中のコメント・JSDoc は英語、画面の文言・コミットメッセージ・ドキュメントは
  日本語で書き分けている（D6）。同じ書き分けを初稿の既定案とし、面談で確認する。
  代替案は「コメントも日本語」。Javadoc を日本語にする場合、生成時の文字コード指定（UTF-8）を忘れないこと。

### 6. 土台（Walking Skeleton）への追加提案
- 主担当の完了条件「ビルドとテストが1コマンドで通る」に、フォーマット検査・リンタ・ライセンスヘッダー検査を
  含める。最初の Bolt で規約を検査する仕組みを入れておけば、以後の Bolt が規約から外れない。
- フロントエンドの土台では、Vite の `resolve.dedupe`（D9）を設定し、make-you-chic-ui の部品が1つ以上
  表示されることを確認する（React の二重読み込みは実際に起きた問題として記録されている）。
- バックエンドの土台では、4.5 の共通エラー応答（`@RestControllerAdvice`）を最初から入れておく。後から
  入れると、それまでの各コントローラーのエラー処理を書き直すことになる。

### 7. 面談で決める必要がある論点（開発者の観点・主担当の P16〜P20 への追加）

| # | 論点 | 選択肢（推奨） |
|---|---|---|
| DQ1 | リポジトリ内の配置 | `backend/` と `frontend/` に分ける（推奨）／その他 |
| DQ2 | バックエンドのビルドツールと、決める時期 | Gradle（Kotlin DSL）／Maven。本ステージで決めないなら決めるステージを明記 |
| DQ3 | Java のルートパッケージ名 | `io.github.agwlvssainokuni.mastersmith`／依頼者指定の名前 |
| DQ4 | Java のパッケージの分け方 | 機能別＋機能内で層分け（推奨）／層別 |
| DQ5 | 層の境界の決まり（4.3）と、それを ArchUnit 等で機械的に検査するか | 4.3 を採用し検査する（推奨）／採用のみ／採用しない |
| DQ6 | エラー応答の形式 | RFC 9457 Problem Details＋`code`（推奨）／独自形式 |
| DQ7 | Lombok の採否 | 使わない（`record`＋コンストラクター注入）／使う |
| DQ8 | null の扱い | `Optional`＋JSpecify 注釈／`Optional` のみ |
| DQ9 | フロントエンドの配置 | 機能別（推奨）／種類別 |
| DQ10 | アプリ用 CSS クラスの接頭辞 | 例: `ms-`／依頼者指定 |
| DQ11 | ライセンスヘッダーの検査を CI に入れるか | Java は Spotless、TS/CSS は検査スクリプト等で入れる（推奨）／入れない |
| DQ12 | `.editorconfig`・`.gitattributes` をルートに置くか | 置く（推奨）／置かない |

## Positions

- AGREE: 言語ごとの慣習的な命名に従う（Code Style の1項目め） — org.md の既定と make-you-chic-ui の実態（D3）の両方に合う。
- AGREE: フロントエンドの規約を make-you-chic-ui と同じ設定にする提案（P16） — 依頼者自身の直近の慣習であり、同じ部品を使う画面側との一貫性が高い。ただし設定は `frontend/` 側に複製し、名前付きエクスポート・`enum` 不使用・tsconfig の strict 系設定（D2〜D4）も含めるべき。
- AGREE: ライセンスヘッダーを `/* ... */` に揃える提案（P18） — make-you-chic-ui の修正履歴に加え、JDK 23 以降の `dangling-doc-comments` 警告という Java 上の技術的根拠がある。
- AGREE: 文字コード UTF-8・改行 LF — 妥当。ただし `.editorconfig`・`.gitattributes` で機械的に守れる形にすべき（D11）。
- AGREE: discovered-rules.md の Mandated / Forbidden を面談で明言されるまで空にしておく扱い — 新規プロジェクトで観察から「必ず守る」制約は導けない。
- AGREE: 候補F1（秘密情報をハードコードせず、ログ・監査ログに出さない） — エラー処理・ログの規約と直結し、開発者としても採用を推す。
- AGREE: 候補F3（サブモジュールを直接変更しない） — 採用する場合は、フォーマッタ・リンタの除外設定に `vendor/` を入れることを合わせて決める必要がある。
- OBJECT: Code Style 節に層の境界とエラー処理の規約が欠けている — 認証・監査の基盤を作る本Intentで最初に固めるべき内容であり、後続Intent（D〜K）がすべて従う型になる。4.3・4.5 を論点として追加すべき。
- OBJECT: バックエンドのビルドツール（Gradle／Maven）が論点に入っていない — フォーマッタ・静的検査・カバレッジの設定方法がこれに依存し、最初の Bolt より前に決める必要がある。本ステージで決めないなら決めるステージを明記すべき。
- OBJECT: テストファイルの配置が「対象と同じ場所の `*.test.tsx`」のみで Java 側の記述がない — Java は `src/test/java` に置くのが標準であり、両者が異なることを明記しないと混乱する。
- OBJECT: P19（パッケージ名・構成）が例示のみで選択肢がない — ルートパッケージ名は推測できず、分け方は後続Intentの構造を左右するため、DQ3・DQ4 の形で具体的な選択肢を示して聞くべき。
