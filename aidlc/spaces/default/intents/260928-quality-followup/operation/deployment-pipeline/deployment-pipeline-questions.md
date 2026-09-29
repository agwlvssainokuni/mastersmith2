# Deployment Pipeline の質問（Intent 260928-quality-followup）

## 質問にせず、決まっていることとして扱う点

前の Intent の配備の手順（`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/`）と、`team.md`・`project.md` の決まりで決まっている点です。

- **配備先と配り方**: 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）へ、依頼者の承認のうえで手で配備する。CI Pipeline・Infrastructure Design の段は無いため、前の Intent の配備の手順を正として、今回の差だけを書く（`project.md` の Deployment）。
- **配備する版**: `develop` の先頭。コードは `d1fda19`（Build and Test で手元の `verify`・E2E と CI が通った版）。統合は済んでいる。
- **配備の前の関門**: Build and Test の手元の `verify`（対象DB を含む）と E2E 110 件、CI の `verify` の結果を正とする。配備するソースは同じなので、流し直さない。配備の前の k6 は流さない。応答の処理を変えておらず、負荷の目標も無いため（`project.md` の Deployment）。
- **バックアップ**: スキーマの変更（Flyway）が無いため、内部DB のバックアップは要らない（`project.md` の Deployment）。
- **`.env`**: 変えない（この Intent で足す環境変数は無い）。
- **戻し方**: 配備の前に今のイメージ（`mastersmith:local`、`9e5243a3…`）に戻し先のタグ `mastersmith:pre-quality-followup` を付け、イメージだけで戻す。データとスキーマは戻さない（変わらないため）。
- **手元の監視**: ダッシュボードのファイル（メールの送信の p95 のパネル）と `grafana/otel-lgtm` 0.34.0 は、次に profile `monitoring` で起動したときに効く。今は起動していないため、配備の手順には入れない。
- **新しく届いた Dependabot の3件**（#18 `@types/node`・#19 spotless・#20 Jackson の BOM 3.1.7）: 次の Intent で取り込む（Build and Test の承認の後の依頼者の決定）。

## Q1. 見本の対象DB（PostgreSQL）のコンテナの作り直し

`compose.yaml` の見本の対象DB `targetdb-postgres` のイメージは、Dependabot の更新で digest が変わりました（18.6 のまま、`86c951e0…` → `5a5a84b1…`）。今動いているコンテナは古い digest のままです。データはボリューム `mastersmith_mastersmith-targetdb-postgres` にあり、作り直しても残ります。

A. 配備のときに `docker compose --profile targetdb-postgres up -d` で、アプリと一緒に新しい digest で作り直す。作り直した後に、アプリから既定の DSL の生成（読み取り）ができることをスモークテストで確かめる。
B. 今回はアプリだけを入れ替え、見本の対象DB は古い digest のまま動かす。次にコンテナを作り直したときに新しい digest になる。
X. Other (please specify)

[Answer]: A

## Q2. `main` への取り込み（リリース）

`team.md` の Way of Working では、`main` はリリース版の置き場です。リリースのときに、依頼者の承認を得て `develop` から `main` へ取り込みます。今の `origin/main` は最初のコミット（`2673bd5`）だけです。前の Intent では、CI が失敗していたため取り込みを見送りました（前の Intent の `cd-config.md` の 4節）。今回は CI が通っています。

A. この Intent の配備の後に、`develop` を `main` へ取り込み、`main` にタグを付ける（例: `v0.1.0`）。`git push` は依頼者が行う。CI はタグのプッシュでも動く（`v*`）。
B. 今回も取り込まない。リリースの時期は、配備先が決まったときなどに改めて決める。
X. Other (please specify)

[Answer]: A

## F1.（Q2 の追加の質問）タグの名前と、公開のリリースが作られること

`origin/main`（`2673bd5`）は `develop` の祖先で、fast-forward で取り込めます。ただし CI の `.github/workflows/ci.yml` には、`v*` のタグのプッシュで GitHub のリリースを作り、検査を通った WAR（`mastersmith-<タグ>-<コミット>.war`）を添付するジョブ（`release`）があります。リポジトリは公開なので、WAR は誰でも取れる形で公開されます。

A. タグは `v0.1.0`。`main` を `develop` の先頭へ fast-forward で取り込み、`v0.1.0` を付ける。公開のリリースと WAR の添付を受け入れる（WAR には秘密情報を含めない決まりのとおり）。
B. `main` へは fast-forward で取り込むが、タグは付けない（公開のリリースは作らない）。タグとリリースは配備先が決まったときに改めて決める。
C. タグの名前を変える（名前を書く）。
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 配備: `develop` の先頭（コードは `d1fda19`）のイメージ `mastersmith:local` を作り直して入れ替える。関門は Build and Test の結果（手元の `verify`・E2E 110 件・CI の `verify`）を正とし、流し直さない。k6・バックアップ・`.env` の変更は無し。
- 戻し方: 配備の前に今のイメージに `mastersmith:pre-quality-followup` を付け、イメージだけで戻す。
- Q1: A。見本の対象DB（PostgreSQL）を、アプリと一緒に新しい digest で作り直し、既定の DSL の生成（読み取り）で確かめる。
- Q2: A・F1: B。配備の後に `main` を `develop` の先頭へ fast-forward で取り込む（`git push` は依頼者）。タグと公開のリリースは付けない。配備先が決まったときに改めて決める。`team.md` の Deployment「リリース時に main にタグを付ける」との差を記録する。
- 手元の監視の変更（ダッシュボードと otel-lgtm 0.34.0）は、次に profile `monitoring` で起動したときに効く。新しく届いた Dependabot の3件は次の Intent で取り込む。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

