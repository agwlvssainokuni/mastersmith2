# NFR Requirements の質問 — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。U5 は種類が `packaging`（設定・文書・手順の作業）のため、この段の成果物は security-requirements.md・tech-stack-decisions.md・traceability.json だけで、性能・規模・可用性・観測の要件は対象外です。判断が分かれる点は、要件・ストーリー・Delivery Planning で決まっています。

## 決まっていること（質問しません）

- 要件の枝番は、単位の間で重ならないよう、U5 は `NFRx.81` から振る（U1 は `.1`、U2 は `.21`、U3 は `.41`、U4 は `.61` から）。
- コンテナの入れ替え（Mailpit・otel-lgtm）は、最初の手順で Mailpit のメールが消えることを依頼者に伝えて了承を得てから行う（AC4.2.1、R-03 の引き継ぎ）。入れ替えの後に `docker inspect` で版と複数アーキテクチャの index のダイジェストが `compose.yaml` と一致することを確かめ、古い 0.34.0 の lgtm のイメージは戻し先として残し、lgtm は確かめたら止める（AC4.2.2〜AC4.2.5、`team.md` の Deployment、要件 NFR8）。
- 入れ替えの間、資格情報（`.env` の値）を表示しない。`docker inspect` の結果の記録には環境変数の値を含めない（`project.md` の Forbidden）。
- perf の文書の整理では、合否の線の値を変えない。整理の前後で `k6 inspect --include-system-env-vars` の出力が場面ごとに同じことで確かめる（AC4.3.4）。整理の前の出力はリポジトリの外に置く。
- `perf/README.md` を U4 も触るため、U4 の後に整理し、U4 が書く伏せ字の節（JSON の口とテキストの置換の口、台本の決まり）を整理した README に収める。
- 新しい依存は足さない。
