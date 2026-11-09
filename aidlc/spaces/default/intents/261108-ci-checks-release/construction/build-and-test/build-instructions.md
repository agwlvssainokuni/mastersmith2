# ビルドの手順（Intent 261108-ci-checks-release）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 前提

- JDK 25・Node 26・git・gh（README のとおり）。colima の PC では README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、長い実行は `caffeinate -dims` で包む。
- E2E の前に、Mailpit（`docker compose --profile mail up -d mailpit`）と、E2E 用のポートを開けた見本の PostgreSQL（`docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres`）を起動する（`./gradlew e2eTest` が足りないものを名指しして止まる）。
- Mailpit は送り手の逆引きをしない設定（`MP_SMTP_DISABLE_RDNS: "true"`）で動かす。つなぐネットワークによって、逆引きが遅く招待メールが TIMEOUT になるため（この Intent の Build and Test で見つけた）。

## 2. ビルドと検査

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3
caffeinate -dims ./gradlew e2eTest
```

- この Intent で、`verify` の 1〜3 の段（書式・リンタ・ライセンスヘッダー）がルートの `scripts/` も見るようになった。バックエンドのカバレッジの下限は、すべてのパッケージに当たる（外すパッケージの一覧を消した）。
- CI は `./gradlew verify -PitForks="$IT_FORKS"`（既定 3、手動の実行の入力 `it_forks` で 1〜8）を流す。

## 3. 確かめの台本

```bash
node scripts/ops-readonly-check.mjs        # A1: 手元の環境の読み取り（コンテナの版とダイジェスト・.env の名前・WAR の SHA-256 など）
git fetch origin && node scripts/ci-attempts.mjs   # A2: main への取り込みの前の CI の試行ごとの確かめ
node scripts/count-db-copy.mjs --copy <複写> --since <起点>   # A3: 内部DB の複写の件数（.env を読むため、AI が流すときは依頼者の許しを得る）
```

## 4. うまくいかないとき

- E2E の招待メールが SENT にならない: Mailpit の SMTP の挨拶の時間を `printf 'QUIT\r\n' | nc -w 10 127.0.0.1 1025` で測る。数秒かかるなら逆引きを疑い、Mailpit が `MP_SMTP_DISABLE_RDNS` 付きで動いているかを確かめる。
- A1 が終了コード 2: Docker の実行環境に繋がっていない（colima・`DOCKER_HOST`）。
- A2 が終了コード 2: 先頭のコミットの CI が走っている最中。終わってから流し直す。
