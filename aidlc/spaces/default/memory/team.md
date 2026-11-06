# Team-Level Rules

> This team's affirmed practices and corrections. Loaded after `org.md` as
> strict-additive guidance; contradictions with broader policy are rejected.
> Populated by the practices-discovery affirmation gate. Edit at the gate,
> not directly.

## Way of Working

- 私たちは依頼者1名とAIで開発する。レビューと承認は依頼者が行う。
- 日々の統合先は `develop` とする。Construction の Bolt ごとの作業は、同じ作業フォルダの中で `develop` から作る短命のブランチ（`feature/<intent>-<bolt>`・`fix/<intent>` など）で行い、`develop` へ戻す（worktree は使わない）。統合を終えたブランチは、統合の後に消す。
- `main` はリリース版の置き場とする。リリースのときに、依頼者の承認を得て `develop` から `main` へ **fast-forward** で取り込む。長期間生きる機能ブランチやリリースブランチは作らない。
- Bolt の作業ブランチは `develop` へ **squash マージ** で戻す。原則として 1 Bolt が `develop` の1コミットになる。ただし、複数の単位を持つ Bolt では、コード生成の計画の承認で単位ごとの squash（1単位が1コミット）を選んでよい。細かいコミット履歴は作業ブランチと監査ログに残る。統合コミットのメッセージは日本語で、Bolt（または単位）の内容が分かる件名にする。
- サブモジュール（`vendor/make-you-chic-ui`・`vendor/java-mustache-processor`）の固定先の更新を含む変更に限り、固定先の更新を専用のコミットとして残すため、squash ではなく短命のブランチから `develop` へ fast-forward で統合してよい。それ以外の変更は squash で統合する。どちらの形でも、統合の前に下の1コマンドの検査を通す。
- サブモジュールの固定先を上げる前に、新しいコミットが上流の `main` から辿れること（宙に浮いたコミットでないこと）と、`vendor/make-you-chic-ui/package-lock.json` の前後の差（増えた・変わったパッケージと、`hasInstallScript` の付いた項目が増えていないか）を読み取りで確かめ、固定先の前後のコミットハッシュと同じ記録に書く。`hasInstallScript` の付いた項目が増えたときは、取り込む前に依頼者に諮る（vendor の `npm ci` は `ignore-scripts` の守りの外で動くため）。
- make-you-chic-ui に無い部品（入れ子のサイドバーなど）が要るときは、設計の段で足りない点を一覧にし、make-you-chic-ui のリポジトリ側への追加を先に依頼する。取り込みは、その部品を使う Bolt の前に固定先の更新の専用のコミットで行い、統合は上の fast-forward の例外を使う。依頼が間に合わないときに frontend の側で自前で作る形へ切り替えるかは、その時点で依頼者に諮る。
- make-you-chic-ui への依頼は、依頼ごとに文面を Intent の記録（`<record>/make-you-chic-ui-request-<n>.md`）に置く。業務データの一覧・詳細のように表や入力の部品を多く使う画面では、足りない点（表の並べ替え・ページ送りの英語の文言・行の操作・Topbar の利用者のメニューの名前など）を画面イメージの段（Refined Mockups）で一覧にし、機能設計の承認の前に依頼を出す。上流の対応は、報告ではなく上流のソース（読み取りだけの `git show` など）で確かめてから固定先を上げる。
- プルリクエストは使わない。統合の前に、CI と同じ検査（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）をローカルで **1コマンド** で実行し、すべて通ってから統合する。これが統合前の関門であり、同じ検査をプッシュ前に自動実行するフック（pre-push）を置いてもよい。
- 画面からの一連の操作のテスト（E2E、`./gradlew e2eTest`）は、ブランチごとの統合の前には流さず（統合は `./gradlew verify` で行う）、Intent の最後（Build and Test）とリリースの前に全体を1回流す（2026-11-04 に依頼者の決定で、変えたパスでブランチごとに決める前の決まりを廃止）。
- Dependabot が作る更新のプルリクエスト（向き先は GitHub の既定のブランチの `develop`）は、依存の更新の知らせとして扱い、GitHub の画面でマージしない。手元で版と lockfile をまとめて更新し、1コマンドの検査（`./gradlew verify`）を通してから `develop` に統合し、プルリクエストは閉じる。重大度 High 以上の知らせは、次の Bolt に入る前に取り込む。
- 依存関係の脆弱性の関門は、1コマンドの検査の中の OSV-Scanner とする（GitHub の Dependabot alerts は使っていない）。OSV-Scanner は `./gradlew verify` と CI を流したときにだけ働き、新しい脆弱性が公表された時点では知らせない。そのため、Dependabot の知らせを見送る依存（`.github/dependabot.yml` の `ignore`）の脆弱性には、次の `./gradlew verify` と CI の OSV-Scanner で気づく。更新を見送る依存を `ignore` に足すときは、見送る理由と外す時期を同じファイルのコメントに書く。
- 統合前の1コマンドの検査は、コンテナの実行環境（colima）が動いていることを前提にする。コンテナの実行環境が無いときは、警告を出してコンテナで動かす対象DB のテストだけを飛ばす（黙って飛ばさない）。ただし、飛ばした状態では統合しない。警告が出たらコンテナの実行環境を起動して検査をやり直し、対象DB のテストも通ってから統合する。飛ばしてよいのは開発中の途中の実行だけとし、CI では必ず実行する。
- CI は統合後の再確認として動かす。CI が失敗したら、次の Bolt に進む前に、Testing Posture の「不安定なテストと CI の失敗」の決まりに従って扱う（原因を直すか、決まりの範囲で再実行して進めるか、依頼者の決定で持ち越すか）。
- `origin` への `git push` は依頼者自身が行う。AI はプッシュしない。
- GitHub のリポジトリは公開（public）である。公開されて困る情報（秘密情報・個人情報）をコミットしない前提で運用する。

- make-you-chic-ui への依頼の対象に、部品だけでなくアイコン（`Icon` の登録の名前）も含める。画面で使うアイコンのうち make-you-chic-ui に無いものは、画面イメージの段（Refined Mockups）で一覧にし、依頼の文面を `<record>/make-you-chic-ui-request-<n>.md` に置く。上流の対応を待つ間は、そのアイコンに頼らない単位を先に進めてよい。待ちが長引いて frontend の側で自前の SVG に代えるかは、その時点で依頼者に諮る。

## Walking Skeleton

- 私たちは walking skeleton の特別な手順を行わない（スコープファイルが `skeleton: on` を宣言していても、この方針が優先する）。最初の Bolt も他の Bolt と同じ流れで進め、最初の Bolt だけを単独で実行して承認を待つ、という特別な関門は設けない。
- ただし、アプリの骨格がまだ存在しない状態から始めるときは、最初の Bolt に次の一式を必ず含める。
  - バックエンドの起動、内部DBへの接続、ヘルスチェックの応答
  - 決めた形式での構造化ログの出力
  - make-you-chic-ui を組み込んだフロントエンドのビルドと、ログイン画面の枠の表示（Vite の `resolve.dedupe` による React の二重読み込み対策を含む）
  - フォーマット・リンタ・ライセンスヘッダー・秘密情報の検出を含む検査とテストを、1コマンドで実行できること
  - カバレッジの計測と、下限を下回ったら失敗する検証
  - DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）と、ヘルスチェックの起動確認テスト
  - 共通のエラー応答の仕組み（Code Style の `@RestControllerAdvice` と Problem Details）
  - 同じ検査が CI（GitHub Actions）上でも通ること
- 骨格が既にある場合は、この一式が維持されていることを前提に、各 Bolt を通常どおり進める。

## Testing Posture

- **Methodology**: test-after
- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。
- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。
- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。
- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。
- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。
- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。
- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。
- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。
- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。
- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。
- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。
- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。
- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。
- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。
- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。
- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。
- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。
- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。
- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。
  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。
  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。
  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。
  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。
  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。
- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。
- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。
- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。
- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。

- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) <!-- cid:260922-auth-audit-base:nfr-requirements:bb1f2f3e98692eeea477da5f229ae3fff34cb064a2feea6ef405a3b7c61b99b7 -->

- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。

## Knowledge Pointers

- 機能・段に当たる決まりの一部は、文を変えずに `aidlc/spaces/default/knowledge/aidlc-shared/` の下のファイルへ移した（2026-11-07）。下の「読む時」に当たる Intent・段では、要件定義・設計の段とコード生成の計画を始める前に、当たるファイルを**必ず読み**、読んだファイルをその段の成果物の `## Sources` に書く。担当に任せる段では、担当が始める前にこの置き場をすべて読む。
- 認証・認可・監査・利用者の管理（招待・停止・管理の権限・ロック）に触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（認証・認可・監査・利用者の管理の機能）
- 役割・権限・作業ロール・グループに触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-roles.md`（役割・権限の機能）
- 業務データの画面・API・SQL・対象DB の読み書きに触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-business-data.md`（業務データ（一覧・詳細・作成・更新・削除）の機能）
- 他テーブル参照・参照ピッカーに触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-reference.md`（他テーブル参照（REFERENCE・LOOKUP）の機能）
- 日時・時刻の変換・表示・入力・表示のタイムゾーンに触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-timezone.md`（日時のタイムゾーンの変換の機能）
- メニュー・ナビゲーションの木の画面に触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-nav-menu.md`（N 階層のメニュー（ナビゲーションの木）の画面）
- メールの送信・テンプレート・SMTP に触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-mail.md`（利用者へメールを送る機能）
- DSL（YAML）の投入・読み込み・生成に触れる Intent: `aidlc/spaces/default/knowledge/aidlc-shared/checklist-dsl.md`（利用者が投入する DSL（YAML）を読み込む機能）
- Build and Test の負荷の試験と、Deployment Pipeline 以降の段（配備・環境・監視・障害対応・性能の確かめ・振り返り）: `aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`（配備・運用・負荷の試験・監視の細かい学び）

## Change Control

## Deployment

- 当面の配備先は、開発者のPC上のコンテナでの起動・確認だけとする。クラウドなどの配備先は後で決める。配備先が決まったら、org.md の既定（統合時に検証環境へ自動配備、本番環境は手動承認のうえ配備）に沿って配備の流れを作る。本番配備の承認者は依頼者とする。
- 配備先が決まるまでは、実在の宛先・外部の SMTP へメールを送らない。手元でメールを見るときは、メールを受けて画面で見せるだけの受け手を compose の profile で見たいときだけ起動する。SMTP の接続先と資格情報は環境変数（`.env`）だけから受け取り、接続先の設定が無ければ送らない（固い制約は `project.md` の Mandated・Forbidden）。
- CI は GitHub Actions に置く。`develop` への統合後（プッシュ時）に、ローカルの1コマンド検査と同じ検査を実行する。実行順はフォーマット検査 → リンタ → ライセンスヘッダー検査 → ビルド（型検査を含む）→ 単体テスト → 結合テスト（DB コンテナ使用）→ カバレッジ下限の検証 → セキュリティ検査で、1つでも失敗したら全体を失敗とする。
- CI ではサブモジュールを固定先のコミットで取得し、依存関係は lockfile どおりに入れる（`npm ci` など）。`vendor/make-you-chic-ui` のテストはこのリポジトリの CI の対象外とするが、同梱版でフロントエンドのビルドが通ることは CI で確かめる。
- ビルドの成果物は、フロントエンドのビルド結果（`dist`）を同梱した実行可能 WAR とする。
- 成果物の版はコミットのハッシュで識別する。リリースは `develop` から `main` への fast-forward の取り込みで行い、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う。`v*` のタグは、プッシュすると CI が公開の GitHub のリリースを作り WAR を添付するため、配備先が決まるまで `main` に付けない。タグと公開のリリースの扱いは、配備先が決まったときに改めて決める。
- 部品表（SBOM）の生成は、配備先が決まってから入れる。
- 配備のたびに起動確認（ヘルスチェック）と最低限の動作確認（スモークテスト）を行い、通るまで配備完了とはみなさない。
- すべての配備に戻し方（直前の版の成果物での再配備）を用意する。DB スキーマの変更は前進のみとし、1つ前の版のアプリが動く後方互換を保つ。
- 権限などのデータの形を変えるときは、広げてから縮める二段にする。新しい形（表・列）を足し、前の版のアプリが使う列は消さずに残して、消すのは後の Intent とする（例: Intent 261004-role-menu で役割の表を足しても `users.admin_flag` は残す。列の扱いは設計で決める）。行を移す移行には、移行の後も前の版が使う列が一貫して読めることを確かめる結合テストを書き、前の版へ戻したときの権限のずれ（戻した版での権限が、戻す直前の割り当てと一致するか）を戻しの確かめに入れる。
- 広げてから縮める二段の「縮める」側（例: `users.admin_flag` を消す）を行う Intent では、列を消す移行は1つ前の版のアプリへ戻せなくなるため、(1) 縮める前の版がその列を読まないことを先に配備して確かめ、(2) 列を消す移行を別の配備に分け、(3) 戻しは配備の前の内部DB の複写を戻す形とし、失うもの（その後のデータと監査の行）を手順に書く。内部DB の複写はリポジトリの外に置き、コミットしない。
- コンテナのイメージは版のタグと複数アーキテクチャの index のダイジェストで固定し、上げるときは Dependabot が見ない固定先も同じ版とダイジェストに揃える。固定先の一覧は `.github/dependabot.yml` に書く。
- 構造化ログの外部エクスポートは既定で無効とし、配備の設定で環境ごとに有効化を切り替える。

- フロントエンドの依存関係の脆弱性は、実行時に使うもの（dependencies、推移依存を含む）は重大度 High 以上で統合を止める。開発時にだけ使うもの（devDependencies）は警告にとどめる。ただし、悪意のあるパッケージ（OSV の MAL- で始まるもの）と、成果物を作る道具（Vite・Rollup・esbuild・TypeScript など）の High 以上は止める。対象の道具の一覧は config/npm-build-tools.txt に置く。 (learned 2026-09-23) <!-- cid:260922-auth-audit-base:code-generation:28ae755ce1652f5d951107b80eaa36eae9ddbf6708f8f0f5b2fcbd0a6f540185 -->

- コンテナ（Mailpit・otel-lgtm など）を入れ替えた後は、毎回 `docker inspect` でコンテナのイメージの版とダイジェスト（`RepoDigests`）が `compose.yaml` の記述と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す。止まっているコンテナは作り直されず、古い版が残ることがあるため。

- 実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）を変えるときは、4か所を同じ値にそろえ、変えたことを配備の記録に書く（`-Dh2.compactThreads=1` と同じ扱い）。

## Code Style

### リポジトリ構成とビルド

- リポジトリのルート直下に `backend/`（Java）と `frontend/`（React + TypeScript）を分けて置く。
- バックエンドのビルドは Gradle（Kotlin DSL）とする。ビルドの成果物は実行可能 WAR で、フロントエンドのビルド結果（`dist`）を WAR に同梱する。
- 開発時はフロントエンドの開発サーバーのプロキシを通してバックエンドの API を呼ぶ。ビルドした成果物では、画面（SPA）は配信元と同じサーバーの API を呼ぶ。同じオリジンのため、CORS の設定は置かない。
- 文字コードは UTF-8、改行コードは LF とし、ルートの `.editorconfig` と `.gitattributes` で統一する。
- フォーマッタ・リンタ・静的検査は `vendor/` を対象から外す。

### 言語と命名

- コード中のコメント（Javadoc・JSDoc を含む）、コミットメッセージ、ドキュメントは日本語で書く。画面の文言は日本語と英語の両方を用意し、既定を日本語とする。テストの説明文は英語（Testing Posture を参照）。
- 命名は言語の慣習に従う。Java はクラスが PascalCase、メソッドと変数が camelCase、定数が UPPER_SNAKE_CASE。TypeScript は camelCase、React の部品・画面は PascalCase のファイル名、フックは `useXxx`、Props は `XxxProps`。
- ソースファイルの先頭の Apache License 2.0 ヘッダーは `/* ... */` の形式で書く（`/** ... */` は使わない）。ヘッダーの有無は CI で検査する（Java は Spotless の `licenseHeader`、TypeScript・CSS は検査スクリプト等）。
- 自作の SVG（アプリのアイコン・favicon など）は、先頭に HTML と同じ `<!-- ... -->` の形の Apache License 2.0 ヘッダーを置き、`frontend/scripts/check-license-header.mjs` の対象の拡張子に `.svg` を足す。favicon の `.ico`・`.png` などのバイナリはヘッダーを置けないため検査の対象の外とし、元の SVG から作る手順を README か記録に書く。SVG には `<script>`・イベントの属性（`onload` など）・外部の参照（外部の URL の `href`・読み込み）を入れない（固い制約は `project.md` の Forbidden）。

### バックエンド（Java）

- ルートパッケージは `cherry.mastersmith` とする。
- パッケージは機能ごとに分け（例: `auth`・`user`・`audit`、共通部品は `common`）、各機能の中を画面入出力（`web`）・業務処理（`service`）・ドメイン（`domain`）・DB アクセス（`repository`）の層に分ける。
- Spring の設定は、全体に効くものを `config` に置き、1つの機能だけに効く設定（`@Configuration`・`XxxProperties`）はその機能のパッケージの中に置く。
- 層の境界を次のとおり決め、ArchUnit などの構造検査の道具でテストとして自動確認する。
  - 画面入出力の層は HTTP の受け渡し（入力検証、DTO と業務オブジェクトの変換、状態コード）だけを行い、DB アクセスの層を直接呼ばない。
  - トランザクションの境界は業務処理の層にだけ置く。
  - DB のエンティティを API の応答として直接返さない。要求・応答は `record` の DTO（`XxxRequest` / `XxxResponse`）にする。
  - 依存性の注入はコンストラクター注入のみとする。
- Lombok は使わない。`record` とコンストラクターでの受け渡しで代える。ロガーは SLF4J の `LoggerFactory.getLogger` で宣言する。
- エラー応答は RFC 9457 Problem Details（Spring の `ProblemDetail`）に、画面が分岐に使う安定した `code` 項目を足した形式とする。例外は `@RestControllerAdvice` の1か所でまとめて応答に変換し、個々のコントローラーで応答を組み立てない。応答に内部の例外メッセージやスタックトレースを載せない。
- 例外のログは変換する境界で1回だけ出す。想定内の業務エラー（4xx）は WARN 以下でスタックトレースなし、想定外（5xx）は ERROR でスタックトレース付き。例外を捕捉して何もしないコードは書かない。
- 構造化ログの項目は文字列の連結ではなく、キーと値（SLF4J のキー・値 API や MDC）で渡す。監査ログ（内部DBへの記録）とアプリのログは別の仕組みとし、監査記録をログ出力で代用しない。
- 4つの層に当たらない用途名の下位パッケージ（`dsl/parse`・`dslmanage/generate` など）を機能の中に置いてよい。機能の間の依存の向きは ArchUnit の境界テストで固定し、逆向きの知らせは Spring の出来事で行う。
- 機能ごとに、問題の種類（エラーの code）の定数を `<機能>.domain.XxxProblemTypes` に、起動時に集める一覧 `XxxProblemTypeCatalog` を `<機能>.service` に置く。1つの code に1つの状態コードを固定し、code は一度決めたら変えない。入力の誤り（`VALIDATION_FAILED`）・未認証（`AUTHENTICATION_REQUIRED`）などの共通の code は既存の定義を使い回し、機能の側で重ねて定義しない。
- 新しく作る機能では、業務処理の層は想定内の失敗を結果の型（sealed interface の record）で返し、画面入出力の層が `switch` で場合を尽くして `BusinessException` に変える。想定外の失敗だけを例外のまま投げる。例外で失敗を返している既存の機能（`auth` など）は、このために直さない。
- メールアドレス・氏名・トークン・URL など、個人に関する値や秘密を持つ record は `toString` を上書きして値を伏せる（`TraceAspect` が各層の引数と戻り値を TRACE のログに文字列で出すため）。認証の主体は引数に出さず、要求の文脈から読む。個人に関する値・秘密を扱う機能は、TRACE を有効にした `XxxSecretLeakIT` で、ログに値が出ないことを確かめる。
- 新しく作る機能は、自分のパッケージのテストに `<機能>BoundaryArchitectureTest` を置き、どの機能に依存してよいか（依存される側を含む）を書く。既存の `ArchitectureTest` と他の機能の境界テストは書き換えず、足す。既存の機能の本体のソース（`src/main`）に手を入れる Bolt で、その機能の境界テストが無ければ足す（今の依存をそのまま書く。「手を入れる」の読みは Testing Posture の `packagesJudgedByTotal` の行と同じ）。
- テストの手伝い（テスト用の部品・時計・待ち合わせなど）は、`src/test/java` の `<機能>/testsupport` に置く。
- 既存の ArchUnit の境界テスト（全体の層の決まりと、機能ごとの境界の検査）を緩める・消すときは、コード生成の計画に明記して依頼者の承認を得る。
- フォーマッタは palantir-java-format（インデント4、1行120文字）を Spotless で実行する。

### フロントエンド（React + TypeScript）

- make-you-chic-ui と同じ値の Prettier（セミコロンなし、シングルクォート、末尾カンマ all、1行100文字、インデント2）、oxlint（correctness を error、react / jsx-a11y / typescript 系のルール）、ESLint（react-hooks の推奨ルールと、`export default`・`enum` の禁止、`no-implied-eval`）、Stylelint（stylelint-config-standard）、tsconfig（`strict` 系の設定、`any` の禁止）を使う。設定は make-you-chic-ui を参照せず、`frontend/` 側に複製する。
- 上の設定に、セキュリティ系のルール（画面にHTMLを直接埋め込む `react/no-danger`、`no-eval`・`no-new-func`・`no-script-url` など。oxlint が受け持つ）を追加する。
- エクスポートは名前付きのみとし、`export default` を使わない。`enum` は使わず、文字列リテラルの union で表す。どちらも ESLint で強制する。
- 機能は `src/features/<featureId>/` に置き、`registration.ts` で登録する。機能どうしは直接 import し合わず、複数の機能で共有するものは `src/shared/` へ移す。機能どうしの import は ESLint の `no-restricted-imports` で止める。テストのファイルはこの規則の対象の外とする。
- CSS は部品と同じ場所に素の CSS で置く。

### 静的解析とセキュリティ検査

- 最初の Bolt から次を入れる。
  - 秘密情報の検出（Gitleaks）。コミット前（pre-commit フック）と CI の両方で実行する。あわせて `.gitignore` に `.env`・`.env.*`（`.env.example` を除く）・`*.pem`・`*.key`・`*.p12`・`*.jks` などを追加する。
  - Java の静的解析（SpotBugs ＋ FindSecBugs）をビルドに組み込む。
  - 依存関係の脆弱性検査と更新通知（Dependabot ＋ OSV-Scanner 等）。検査対象には `vendor/make-you-chic-ui` の lockfile を含める。
- pre-commit フックで、秘密情報の検出とフォーマット検査をコミットの直前に自動実行する。
- GitHub の secret scanning と push protection を有効のままにする（Gitleaks の pre-commit・`verify`・CI に加える守り）。
- Gitleaks の誤検知の除外は、秘密情報でないことを確かめたものに限り、`.gitleaks.toml` に理由を書き、対象のパスと値の形の両方で絞る。
- 検査の指摘で統合を止める基準は、重大度 High 以上とする。それ未満は警告として扱う。
- Java の静的解析（SpotBugs ＋ FindSecBugs）では、上の基準（確からしさが最も高い priority 1 の指摘で止める）に加えて、SQL インジェクション系の指摘（パターン名が `SQL_` で始まるもの）は priority にかかわらず統合を止める。誤検知は、除外の設定（`backend/config/spotbugs-exclude.xml`）に理由を書いて外す。
- 同じく、予測できる乱数（`PREDICTABLE_RANDOM`）とメールのヘッダーへの差し込み（`SMTP_HEADER_INJECTION`）の指摘も、priority にかかわらず統合を止める。
- CodeQL などのより広い静的解析、起動したアプリへの動的検査（OWASP ZAP）、コンテナイメージの検査は採用しない。
- 新しい依存を足すときは、採用の前にライセンスを確かめる。Apache License 2.0 と異なるライセンスのものは、採用の理由を設計の記録（ADR など）に残す。
- npm でパッケージを入れるときは、パッケージのスクリプトを動かさない（`frontend/.npmrc` に `ignore-scripts=true`）。悪意のあるパッケージ（OSV の `MAL-`）の判定が、入れてビルドとテストで動かした後の OSV-Scanner になるのを補うため。
- Gradle の依存の取得元は Maven Central だけに固定する（`settings.gradle.kts` の `RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。Maven Central に無い自前の Mustache エンジン java-mustache-processor（Apache License 2.0）は、Git サブモジュールとして `vendor/java-mustache-processor` に取り込み、Gradle の composite build で組む。サブモジュールで取り込む点は make-you-chic-ui と同じだが、make-you-chic-ui は npm の `file:` の依存であり、Gradle の composite build はこのリポジトリで初めての形である。中身はこのリポジトリから直接変更しない（`project.md` の Forbidden）。
- java-mustache-processor の版は、サブモジュールの固定先のコミットで固定する。取り込んでも、取得元を Maven Central だけに固定する決まりは崩さない。部品の推移依存も lockfile（`backend/gradle.lockfile`）と依存関係の脆弱性検査（OSV-Scanner）の対象に含める（部品そのものは lockfile に載らず、版はサブモジュールの固定先で固める）。固定先を更新して推移依存が変わるときは、lockfile と OSV-Scanner の対象に載ることを確かめ直す。
## Forbidden

## Mandated

## Corrections

- 依頼者に選択肢で尋ねるときは、選ぶ前に、推奨する選択肢とその理由を添える（推奨を答えの後に伝えない）。 (learned 2026-10-29) <!-- cid:261029-reference-lookup-cleanup:units-generation:e9ce29f8025edaf74f4c9c01bf6ad16fa14a39d4fd8f381b20d6a7c66ca56baf -->
