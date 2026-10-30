# セキュリティの要件 — U5 u5-container-perf-docs

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6・FR7・NFR5・NFR8・NFR9）・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U5）。受け入れ基準は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md` の US4.2・US4.3、Bolt は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md` の B6、決まっていることは同じ単位の `nfr-requirements-questions.md`。U5 は種類 `packaging` で Functional Design の段の外のため、`functional-spec.md`・`rules.md` は無い（決まりの番号 BR は持たず、上の要件・受け入れ基準を直接たどる）。

## 0. この文書の範囲と枝番

- U5 は設定・文書・手順の作業で、アプリのコード・API・画面・DB を持たない。この単位で守るものは、(1) コンテナの入れ替えの作業の中で `.env` の資格情報と環境変数の値が画面・記録に出ないこと、(2) 入れ替えで手元の確かめの道具（Mailpit・otel-lgtm）が固定した版から外れず、戻し先を失わないこと、(3) perf の文書と台本の整理で、合否の線と伏せ字の手順（U4）が弱まらないこと、である。
- 枝番は単位の間で重ならないよう `.81` から振る（U1 `.1`・U2 `.21`・U3 `.41`・U4 `.61`）。上流の NFR8（運用。戻し方を残す・伏せ字は手順の中で毎回通る）に入れ替えと文書の要件を、NFR9（供給網）にイメージの固定と依存の要件を、NFR5（テスト）に統合の前の検査の要件を寄せた。
- 性能・規模・可用性・観測の要件は、`packaging` の単位のため作らない。

## 1. 守るものと脅威

| # | 脅威（起こりうること） | 守り（要件） |
|---|---|---|
| T1 | 依頼者が知らないまま Mailpit を作り直し、溜まった確かめ用のメールが消える | NFR8.81 |
| T2 | 止まっているコンテナが作り直されず、`compose.yaml` と違う古い版のまま残る（前の Intent で lgtm が 0.34.0 のまま残っていた） | NFR8.82・NFR9.81 |
| T3 | 入れ替えの後に問題が出たとき、戻し先のイメージ（0.34.0 の lgtm）を消してしまい戻せない | NFR8.83 |
| T4 | `docker inspect` の結果をそのまま記録に写し、`Config.Env` に入った環境変数の値（資格情報を含みうる）が公開のリポジトリにコミットされる | NFR8.86 |
| T5 | 作業の途中で `.env` を開く・`docker compose config` の出力を表示するなどで、資格情報が画面と会話の記録に出る | NFR8.86 |
| T6 | `k6 inspect --include-system-env-vars` の出力にシェルの環境変数の値が入り、それがリポジトリの中に置かれる・表示される | NFR8.87 |
| T7 | 整理の途中で合否の線の値が変わり（緩む・取り違える）、緩んだ線で判定される | NFR8.87・NFR8.88 |
| T8 | README の整理で U4 の伏せ字の節が落ちる・弱まり、`cp` で `evidence/` に置く手順が戻る | NFR8.89 |
| T9 | 「履歴」の章に過去の記録の値を写すとき、JWT やメールアドレスなどの値が混ざる | NFR8.90 |
| T10 | Mailpit の受信の確かめで、実在しそうな宛先を使う・外へ送る | NFR8.84 |
| T11 | lgtm を起動したままにし、VM のメモリの余裕（約 100 MiB）を食い、使っていない画面を開けたままにする | NFR8.85 |
| T12 | 整理の作業で新しい依存・新しいイメージを持ち込む、または固定先の版とダイジェストが食い違う | NFR9.81・NFR9.82 |

## 2. 要件

| ID | 要件 | 合否の確かめ方 | 出典 |
|---|---|---|---|
| NFR8.81 | Mailpit の作り直しの前に、溜まったメールが消えることを依頼者に伝え、了承を得てから作り直す。了承の取得を B6 の最初の手順に固定し、了承の日時を記録に残す | コード生成の計画の Step 1 が了承の取得であり、記録に了承の日時と答えがあること | AC4.2.1・FR6.1 |
| NFR8.82 | Mailpit（profile `mail`）と otel-lgtm（profile `monitoring`）を `compose.yaml` から `docker compose up -d --force-recreate --no-deps` で作り直し、`docker inspect` でコンテナのイメージのタグと、`compose.yaml` に書かれた複数アーキテクチャの index のダイジェストが、イメージの `RepoDigests` に含まれることを確かめて記録する。引いた1つのプラットフォームのダイジェストではなく index のダイジェストと照らす。一致しないときは原因を記録し、確かめ直すまで完了にしない | 記録に、2 つのコンテナそれぞれのタグ・`compose.yaml` の index のダイジェスト・`RepoDigests`・一致の判定が並んでいること | AC4.2.2・FR6.1・FR6.2・`team.md` の Deployment |
| NFR8.83 | 古い 0.34.0 の lgtm のイメージは戻し先として消さずに残す。この単位では `docker image rm`・`docker image prune`・`docker system prune` を行わない。Mailpit の入れ替えの前のイメージが手元にあれば、それも消さない | 作り直しの後に `docker image ls grafana/otel-lgtm` に 0.34.0 があることを記録すること | AC4.2.3・FR6.3・NFR8 |
| NFR8.84 | 作り直した Mailpit に SMTP で 1 通送り、Mailpit の API で取得できることを確かめる。宛先と差出人は予約のドメイン（`example.com`）だけにし、送り先は `127.0.0.1:1025`（`compose.yaml` で PC の localhost にだけ結び付けた口）に限る。あわせてアプリの起動確認（ヘルスチェック）が通ることを確かめる | 記録に、送った宛先のドメイン・取得の件数・ヘルスチェックの結果があること（本文と件名の値は表示しない） | AC4.2.4・`team.md` の Testing Posture（予約のドメイン）・`project.md` の Forbidden（外部の SMTP へ送らない） |
| NFR8.85 | lgtm は版を確かめ終えたら止め（`docker compose stop lgtm`）、`docker ps` で動いていないことを確かめて、確かめた結果と止めたことを記録する | 記録に、止めた時刻と `docker ps` に lgtm が無いことがあること | AC4.2.5・FR6.4 |
| NFR8.86 | 作業の間、`.env`・`.env.*` を開かず、値を表示しない。`docker compose config` の全体を表示しない。`docker inspect` は `--format` で `Config.Image`・`Image`・`RepoDigests`・`State.Status` などの必要な項目だけを取り出し、`Config.Env` と全体の JSON を表示も記録もしない。記録を置いた後に、記録のファイルに `gitleaks dir` を流して 0 件を確かめる | 記録に `Config.Env` の項目が無いこと、`gitleaks dir` の結果が 0 件であること | `project.md` の Forbidden（秘密情報・`.env`）・`project.md` の Corrections（秘密情報の値は表示しない） |
| NFR8.87 | perf の文書と台本の整理では、合否の線の値を変えない。整理の前に `k6 inspect --include-system-env-vars` を場面ごとに流して出力をリポジトリの外（ホームの下の権限 700 の置き場）に保存し、整理の後に同じ場面を流して差が空であることを確かめる。`--include-system-env-vars` はシェルの環境変数を出力に含めるため、k6 には場面の名前など比べるのに要る値だけを `-e` で渡し、資格情報を含む環境変数を渡さない。出力そのものは記録・リポジトリに置かず、記録には場面の名前と差の有無だけを書く。確かめの後に、外の置き場の出力を消す | 記録に、場面ごとの差の有無（すべて空）と場面の数があり、出力の本文が無いこと | AC4.3.4・FR7.4・`team.md` の Testing Posture（物差しを変えない） |
| NFR8.88 | 合否の線を定義する場所を `perf/k6/scenarios.js` の `thresholdsFor` の 1 か所に集め、`thresholdsFor` から取り出した値の表と、README の「今の基準の一覧」・`perf/k6/thresholds-test.sh`・`perf/dsl-mixed-memory.sh` の値が一致することを比べて記録する。整理の後に `bash perf/k6/thresholds-test.sh` が通ること（この台本は `./gradlew verify` に入っていないため、手で流す） | 比べた表と一致の判定、`thresholds-test.sh` の終了コード 0 が記録にあること | AC4.3.3・FR7.3 |
| NFR8.89 | `perf/README.md` の整理は U4（B5）の後に行い、U4 が書く伏せ字の節（JSON の口とテキストの置換の口、伏せる対象と対象外、台本の決まり）を、意味を変えずに整理した README に収める。k6 の結果を `evidence/` に置く手順は U4 の道具を通る形だけとし、`cp`・手での編集・貼り付けで置く手順を書かない | 整理の後の README に伏せ字の節があり、`evidence/` へ置く手順が U4 の道具だけであること（Code Generation のレビューで確かめる） | FR5.4・NFR8（伏せ字は手順の中で毎回通る）・契約 C7 |
| NFR8.90 | 過去の値を「履歴」の章へまとめるとき、移す中身は今の README にある文と値だけとし、前の Intent の記録（`evidence/` など）から新しく値を写さない。履歴の章を含む README の全体に、JWT の形（`eyJ` で始まる 3 区間）・`example.com` 以外のメールアドレス・資格情報の値が無いことを確かめる。履歴の章を除いた本文で、Intent の名前（`26` で始まる 6 桁）と日付の検索が 0 件であること | `eyJ` の検索 0 件・`gitleaks dir` 0 件、履歴の章を除いた本文の検索 0 件が記録にあること | AC4.3.1・AC4.3.2・FR7.1・FR7.2・`project.md` の Forbidden（認証の値を perf の記録へ置かない） |
| NFR9.81 | この単位では、新しい依存（npm・Gradle）もイメージの版の変更も持ち込まない。作り直しは `compose.yaml` に固定済みのタグと index のダイジェスト（Mailpit `axllent/mailpit:v1.32.0@sha256:0790692b…`、lgtm `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`）のまま行う | 作業ブランチの差に lockfile・`compose.yaml` のイメージの行・`perf/versions.env` の変更が無いこと | NFR9・`team.md` の Deployment（版のタグと index のダイジェストで固定） |
| NFR9.82 | Dependabot が見ない固定先（`perf/README.md` の `grafana/otel-lgtm`、`README.md` の Mailpit、`docker/perf/compose.yaml` の Mailpit）の版とダイジェストが、整理の後も `compose.yaml` と同じであることを `grep` で確かめる。README の整理でこれらの行を書き換えるときは、版とダイジェストを変えない | `grep` の結果（版とダイジェストの一致）が記録にあること | `team.md` の Deployment・`.github/dependabot.yml` の固定先の一覧 |
| NFR5.81 | 統合の前に `./gradlew verify` が通ること（カバレッジの下限と除外は変えない）。この Bolt は文書・台本のコメント・`aidlc/` だけを変えるため、統合の前に E2E は流さない（`team.md` の Way of Working）。台本 `perf/k6/scenarios.js` の変更はコメントと定義の置き場の整理に限り、場面の構造（`scenarios` の形・場面の名前）を変えない | verify の成功の記録、差に場面の構造の変更が無いこと | B6 の Definition of Done・FR7.3・`team.md` の Way of Working |

## 3. 認証・認可・データの保護

- 認証・認可: この単位はアプリの API・画面・利用者を持たないため、認証・認可の要件は無い。lgtm の画面（Grafana）は `compose.yaml` のとおり `127.0.0.1:3000` にだけ結び付き、匿名の閲覧（Viewer）の設定を変えない。Mailpit の口も `127.0.0.1` にだけ結び付いたまま変えない。
- データの保護: Mailpit に溜まった確かめ用のメールは作り直しで消える（NFR8.81 で了承を得る）。内部DB・対象DB のデータには触れない（アプリ・対象DB のコンテナは作り直さない。`--no-deps`）。
- 記録の置き場: 入れ替えと整理の記録は Intent の記録（`aidlc/` の下）に置き、公開のリポジトリにコミットされる前提で、資格情報・環境変数の値・個人に関する値を含めない（NFR8.86・NFR8.87・NFR8.90）。

## 4. 未決の点

なし。
