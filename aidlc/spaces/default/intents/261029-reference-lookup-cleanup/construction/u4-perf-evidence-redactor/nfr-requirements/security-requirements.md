# セキュリティの要件 — U4 u4-perf-evidence-redactor

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR5・NFR8）。契約 C7 は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`。

## 0. この文書の範囲と枝番

- U4 は種類 `library`（`perf/` に置く Node の 1 ファイルの道具 PerfEvidenceRedactor）で、画面・API・利用者・DB を持たない。認証・認可の仕組みは無く、この単位の「守るもの」は **k6 の結果に入る認証の値（JWT・招待のトークンなど）が記録（`evidence/`）とその表示に伏せずに残らないこと**（`project.md` の Forbidden「認証の値を伏せずに perf の記録へ置かない」、要件 FR5.5）である。
- 枝番は単位の間で重ならないよう `.61` から振る（U1 `.1`・U2 `.21`・U3 `.41`）。上流の NFR8（運用。JWT の伏せ字は記録を置く手順の中で毎回通る）に伏せ字・入口・検査の要件を、NFR5（テスト）に仕組みのテストの要件を寄せた。
- 性能・規模・可用性・観測の要件は、`library` の単位のため作らない（入力の大きさの上限は、資源を食い尽くさない守りとして NFR8.66 に置く）。

## 1. 守るものと脅威

| # | 脅威（起こりうること） | 守り（要件） |
|---|---|---|
| T1 | summary json の `setup_data` などに入ったアクセストークン（JWT）が、そのまま `evidence/` に写されコミットされる（前の Intent 261027-perf-targets-review で 120 件が入り、プッシュの後の CI で落ちた） | NFR8.61・NFR8.62・NFR8.68・NFR8.69 |
| T2 | 置換がキー名・入れ子・配列・長い文字列をたどり漏れ、JWT が残る | NFR8.62・NFR8.63 |
| T3 | JWT の形でない秘密（招待のトークン）が `setup_data` に入る | NFR8.62（`setup_data` の下の文字列をすべて伏せる） |
| T4 | 道具・検査の表示（標準出力・標準エラー、CI のログ）に値やその断片が出る | NFR8.64 |
| T5 | 失敗の途中の出力・一時ファイルが `evidence/` に残る | NFR8.63・NFR8.65 |
| T6 | 引数の誤り・抜け道（`evidence/` の外への書き出し、テキストの口で summary json を置く）で伏せ字を通らない | NFR8.67 |
| T7 | 巨大な入力で道具や verify が止まる・メモリを食い尽くす | NFR8.66 |
| T8 | 手で置いた記録・まだコミットしていない記録に JWT が残る | NFR8.68・NFR8.69 |
| T9 | JWT の形でない秘密が標準出力・ログに出て、テキストの口でも evidence の検査でも拾えない（残る穴） | NFR8.70・NFR8.69 |
| T10 | テストのソース自体に完成した JWT の形の値を書き、gitleaks と evidence の検査を自分で落とす・公開のリポジトリに秘密に似た値を置く | NFR8.71 |

## 2. 要件

| ID | 要件 | 合否の確かめ方 | 決まり |
|---|---|---|---|
| NFR8.61 | k6 の summary json と標準出力・ログを `evidence/` に置く唯一の入口はこの道具とする（summary json は JSON の口、標準出力・ログは `--text` のテキストの置換の口）。`cp`・手での編集・貼り付けで置く手順を README に書かない | `perf/README.md` の節に唯一の入口と 2 つの呼び方が書かれ、`cp` で置く手順が無いこと（Code Generation のレビューで確かめる） | BR4.1・BR6.1 |
| NFR8.62 | JWT の形（BR1.1）は、文字列の値・配列の要素・キー名・数 MB の文字列・`Bearer` の後ろのどこでも、1 つの文字列の中のすべてを `[REDACTED-JWT]` に替える。上の階層の `setup_data` の下の文字列の値は、JWT の形かにかかわらずすべて `[REDACTED-SETUP]` に替える（数・真偽・null と構造は保つ）。テキストの口は同じ置換の関数を中身全体に 1 回当て、別の正規表現を持たない | 性質ベースのテスト (1)(2)(3)(5)・(t1)(t2)(t3) と例のテスト（BR5.2）が通る | BR1.1〜BR1.4・BR1.7・BR1.8 |
| NFR8.63 | 置換とは独立の残存の検査（木をたどらず書き出す直前の文字列を端から調べる。JSON の口では読み直した木の `setup_data` の下も調べる）を当て、1 件でも見つかれば出力も一時ファイルも残さず終わりのコード 4 で終える | 置換をわざと漏らした場合（例のテストで置換を通さない文字列を渡す）にコード 4 で出力が無いこと | BR2.1・BR2.2・BR2.4 |
| NFR8.64 | 道具と evidence の検査の表示（標準出力・標準エラー）には、件数・ファイルのパス・位置（行と桁、またはバイトの位置）・衝突したオブジェクトの道（置換の後の名前）だけを出し、入力・出力の値（JWT の形の文字列・伏せた値・その前後の文字）を出さない。JSON の読み取りの誤りの実行環境の文言（入力の断片を含みうる）をそのまま出さない | 成功・各失敗（コード 1〜5）・evidence の検査の検出の場合について、表示を捕まえて、組み立てた JWT の形の値とその区間が含まれないことを例のテストで確かめる | BR2.3 |
| NFR8.65 | 書き出しは、出力と同じディレクトリの一時ファイルに書いてから名前を替えて一度に置く。どの失敗でも出力のパスは起動の前と同じ（無ければ無いまま・あれば変わらない）で、一時ファイルは残らない | コード 1〜5 の各場合で、出力のパスの前後の状態と一時ファイルが無いことを例のテストで確かめる | BR3.3・BR3.4 |
| NFR8.66 | 道具の入力の大きさの上限を **64 MiB** とし、道具の中の定数に置く。上限を超える入力は中身を読む前（ファイルの大きさで）に判定し、何も書かず終わりのコード 2（入力の誤り）で終える。上限ちょうどは受け付ける。k6 の summary json は数 MB で、上限に余裕がある。evidence の検査（NFR8.68）で上限を超えるファイルに当たったときは、黙って飛ばさず、パスと大きさだけを表示して検査を失敗にする | 上限ちょうどと上限＋1 バイトの境界を例のテストで確かめる（大きなファイルはテストの中で作り、終わったら消す）。上限の値はコード生成で定数に置き、その値をテストが参照する | 要件 NFR8・BR3.1・BR3.4 |
| NFR8.67 | 出力のパスは `evidence` という名前のディレクトリの下に限り、入力と出力が同じなら受け付けない（コード 1）。テキストの口は出力の拡張子 `.json` と、上の階層に `setup_data` を持つ JSON の入力を受け付けず（コード 1）、空・空白だけ・不正な UTF-8・NUL を含む入力を受け付けない（コード 2） | 各誤りの例のテストで、終わりのコードと出力が無いことを確かめる | BR3.2・BR3.4 |
| NFR8.68 | `./gradlew verify` の安全の検査の段（`verifySecurity`）で、`aidlc/` の下のすべての `evidence/` の下のすべての通常のファイル（拡張子で絞らない。先頭に NUL のあるものはバイナリとして飛ばし件数を出す。git の対象か否かを問わない）に残存の検査を当て、1 件でも見つかれば verify を失敗させる。CI も同じ verify で同じ検査が動く。組み込む前に、既存のすべての `evidence/` へ一度単独で流して 0 件を確かめ、件数を記録する（見つかれば止めて依頼者に諮る） | JWT の形を含む `.md`・`.log` を置いた一時のディレクトリで検査が失敗し値を表示しないこと（例のテスト）。導入の前の一度の実行の件数（調べたファイル・飛ばしたバイナリ・検出 0 件）の記録 | BR4.2 |
| NFR8.69 | どちらの口で置いた後も、コミットの前に evidence の検査（単独で呼ぶ）と `gitleaks dir`（リポジトリの `.gitleaks.toml`、値を伏せる表示）を `evidence/` に流し、どちらも 0 件であることを確かめてからコミットを提案する。見つかればコミットせず置き直す | 手順が README に書かれていること。Build and Test で実際の k6 の summary json を道具で置き `gitleaks dir` で 0 件（AC4.1.6） | BR4.3 |
| NFR8.70 | k6 の台本は、秘密の値（トークン・Cookie・Authorization ヘッダー・招待のトークン・パスワード）を、`setup_data` の外に出る所（タグの値・check・指標・group の名前）と標準出力・ログ（`console.log` の行・誤りの文）に出さない。秘密を外に出す必要が出たら置換の対象を広げるかを依頼者に諮り、それまでその場面の結果を記録に置かない。残る穴（JWT の形でない秘密が標準出力・ログに出る場合と、手で置いた json の `setup_data` の下）はこの決まりと NFR8.69 の `gitleaks dir` で守る | README の節に台本の決まりがあること。Code Generation で `perf/k6/scenarios.js` の `console.log` とタグ・check の名前に秘密の値を渡していないことを読み取りで確かめる | BR6.2・functional-spec.md 10節 |
| NFR8.71 | テストの中の JWT の形の値は実行の中で `eyJ` と base64url の区間をつないで組み立て、テストのソース・見本のファイルに完成した JWT の形の文字列を書かない（公開のリポジトリで、gitleaks と evidence の検査を自分で落とさないため）。招待のトークンに似た値も架空の値だけを使う | テストのソースに BR1.1 の形が 0 件であること（`gitleaksScan` と、コード生成のレビューでの検索） | BR5.2 |
| NFR5.61 | 置換と残存の検査に fast-check の性質ベースのテストを当て、JSON の口の 5 つの性質と、テキストの口の 3 つの性質を確かめる。失敗のときは乱数の種と道（値を含まない位置）を表示して再現できるようにする | Vitest の結果で性質のテストが通り、種の表示の仕組みがあること | BR5.1・BR5.4 |
| NFR5.62 | 仕組みのテスト（性質と例）は `frontend/` の Vitest の単体テストとして `./gradlew verify`（`verifyUnitTest`）と CI で毎回流れ、条件で飛ばす仕組みを持たない。数 MB の文字列の例のテストを含め、Vitest の時間の上限（既定）を延ばさない | verify と CI の結果に U4 のテストが skipped でなく passed で出ること（Build and Test で件数を記録） | BR5.3・team.md Testing Posture |
| NFR5.63 | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`thresholds`）とカバレッジの除外を、この単位のために下げない・増やさない。道具のファイルを `frontend/` のリンタ・型の検査・ライセンスヘッダーの検査の対象に入れる（ADR-006 の切り替え先を含め、入れ方はコード生成で決める） | verify の結果（下限が変わっていないこと）と、道具のファイルが各検査に入っていることをコード生成の計画とレビューで確かめる | BR5.3・要件 NFR5 |

## 3. 対象外とした NFR

| 上流 | 理由 |
|---|---|
| NFR1（参照先の読み取りのセキュリティ） | 業務データの API の要件で、この単位は API・権限の判定を持たない。この単位のセキュリティは NFR8.61〜NFR8.71 で扱う |
| NFR2（性能）・NFR3（接続） | 業務データの API の応答時間と対象DB の接続の要件で、この単位は API・DB を持たない（入力の大きさの上限は NFR8.66） |
| NFR4（アクセシビリティ） | 画面を持たない |

## 4. 機能設計との関係

- この段で、機能設計の文書の残る穴（functional-spec.md 10節）に「標準出力・ログに出る JWT の形でない秘密は、テキストの口でも evidence の検査でも拾えず、台本の決まり（BR6.2）と `gitleaks dir` に頼る」を足し、README の節の中身（6節 3項）と BR6.2 に「秘密の値を標準出力・ログに出さない」を書き足した。NFR8.70 はこの決まりを要件にしたもの。
- 入力の大きさの上限 64 MiB（NFR8.66）は、機能設計に値の無かった点をこの段で決めた（終わりのコードは既存の 2 を使い、新しいコードは足さない）。evidence の検査で上限を超えるファイルを失敗にする扱いは、黙って飛ばすと穴になるためこの段で足した。

## Sources

- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/functional-spec.md`・`rules.md`・`entities.md`
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR5・NFR5・NFR8）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C7）
- 決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`（ADR-004・ADR-006）
- 決まり: `aidlc/spaces/default/memory/project.md`（Forbidden・Testing Posture の k6 の summary json の学び）・`aidlc/spaces/default/memory/team.md`（Testing Posture の伏せ字のテスト）
- この段の質問の「決まっていること」: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-requirements/nfr-requirements-questions.md`
