# 確かめの手順 — U5 u5-container-perf-docs

U5 は種類 `packaging`（設定・文書・手順）で、アプリのコードを持たないため、新しいテストのファイル（Vitest・JUnit）を作らない。代わりに、計画（`code-generation-plan.md`）4節の機械の確かめ CA1〜CA12・CB1〜CB21 を、終了コードと件数で合否を決めるコマンドで流す。この文書はそのコマンドの正である。Test Strategy は Standard で、確かめは「入れ替え（層 A）」12 件・「整理（層 B）」21 件。

## 0. 前提と枠組み

- 道具: Docker（colima）・`docker compose`・`colima`・`jq`・`node`・`gitleaks`・`curl`（`smtp` に対応）・`openssl`。新しい道具・依存は入れない。
- k6 は `perf/versions.env` の `K6_IMAGE`（`grafana/k6:2.3.0@sha256:9c2dee7f…`）を `docker run --rm` で使う。ホストに入れた k6 は使わない。`--env-file` を付けず、`-e` は `-e SCENARIO=<名前>` の値を書いた形だけにする。
- コマンドはプロジェクトのルートで bash で流す（zsh で変数にコマンドを入れると単語に分かれないため、関数か台本にする。`project.md` の学び）。
- 外の置き場は `B=~/.mastersmith-b6`（権限 700、ファイルは 600）。リポジトリに置かず、確かめの後に消す。
  ```bash
  B=~/.mastersmith-b6; mkdir -p "$B"/{inspect/before,inspect/after,lines,readme,lgtm-rollback,mail}; chmod -R go-rwx "$B"; umask 077
  ```
- 秘密: `.env`・`.env.*` を開かない。`docker compose config` の全体と `docker inspect` の全体の JSON を表示しない。記録の本文に `Config.Env` の語を書かない（CA12 の検索で 0 件にするため）。

## 1. 道具の用意の確かめ（計画 Step 2。最初の確かめの前に流す）

```bash
docker info > /dev/null && echo docker-ok
for c in colima jq node gitleaks curl openssl; do command -v "$c" > /dev/null || echo "無い: $c"; done
curl -V | grep -q ' smtp ' && echo curl-smtp-ok
bash perf/k6/thresholds-test.sh;     echo "thresholds-test rc=$?"      # 0 を基準として記録する
bash perf/dsl-mixed-memory-test.sh;  echo "dsl-mixed-memory-test rc=$?" # 0 を基準として記録する
```

## 2. 層 B（perf の文書と台本の整理）の確かめ

### 2.1 Build and Test で流し直せるもの（この単位の範囲だけ）

```bash
bash perf/k6/thresholds-test.sh          # CB7: 期待の表の 13 場面の iteration_duration と checks。終了コード 0
bash perf/dsl-mixed-memory-test.sh       # CB8: judge（0.95）・judge_090（0.9）・anon_max_ratio の出力。終了コード 0
```

### 2.2 場面の名前の集合（CB2・CB4）

```bash
names() {   # 先頭のコメント（import の前）の「//   <名前> 」の行の名前
  awk '/^import/{exit} /^\/\/   [A-Za-z0-9]+ /{print $2}' perf/k6/scenarios.js | LC_ALL=C sort -u
}
code_names() {   # コードの側の名前（name === '…'・*_SCENARIOS の配列・USER_THRESHOLDS のキー）
  { grep -oE "name === '[A-Za-z0-9]+'" perf/k6/scenarios.js | grep -oE "'[^']+'"
    awk '/_SCENARIOS = \[/{f=1} f{print} f&&/\]/{f=0}' perf/k6/scenarios.js | grep -oE "'[A-Za-z0-9]+'"
    awk '/^const USER_THRESHOLDS = \{/{f=1;next} f&&/^\}/{f=0} f&&/^  [A-Za-z0-9]+:/{sub(":.*","");gsub(" ","");print}' perf/k6/scenarios.js
  } | tr -d "'" | LC_ALL=C sort -u
}
names > "$B/inspect/names-before.txt"                                  # 整理の後は names-after.txt
LC_ALL=C comm -13 "$B/inspect/names-before.txt" <(code_names) | wc -l  # CB2: 0（今は 78 の名前、コードの側 72 はすべて入る）
# 整理の後
names > "$B/inspect/names-after.txt"
diff "$B/inspect/names-before.txt" "$B/inspect/names-after.txt" && echo CB4-ok
```

### 2.3 k6 inspect の前後（CB3）と「今の基準の一覧」との照合（CB6）

```bash
# 場面ごとに流す。phase は before（整理の前）か after（整理の後）。出力は thresholds と scenarios だけを保存する
inspect_all() {
  local phase="$1" name rc try
  source perf/versions.env
  : > "$B/inspect/$phase/_rc.txt"
  while read -r name; do
    for try in 1 2; do
      docker run --rm -e "SCENARIO=$name" -v "$PWD/perf/k6:/scripts:ro" "$K6_IMAGE" \
        inspect --include-system-env-vars /scripts/scenarios.js 2> /dev/null \
        | jq -S '{thresholds, scenarios}' > "$B/inspect/$phase/$name.json"
      rc=${PIPESTATUS[0]}; [ "$rc" -eq 0 ] && break
    done
    echo "$name $rc" >> "$B/inspect/$phase/_rc.txt"
  done < "$B/inspect/names-$phase.txt"
}
inspect_all before        # 計画 Step 16
inspect_all after         # 計画 Step 22
diff -r "$B/inspect/before" "$B/inspect/after" && echo CB3-ok    # 差が空（終了コードも同じ）
```

一覧の照合の道具（外の置き場に置く。リポジトリに置かない）。README の `### k6 の閾値（thresholdsFor）` の下の表を読み、行ごとの場面の名前（`` `名前` `` を「・」でつないだもの）に、閾値の列の `` `<キー>` `<式>` `` の組（`<場面>` を名前に置き換える）を当て、`inspect/after` の `thresholds` と場面ごとに集合で比べる。閾値の無い場面の行は「なし」（組が空）。

```js
// $B/check-list.mjs  使い方: node "$B/check-list.mjs" perf/README.md "$B/inspect/after"
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
const [readmePath, dir] = process.argv.slice(2)
const lines = readFileSync(readmePath, 'utf8').split('\n')
const start = lines.findIndex((l) => l === '### k6 の閾値（thresholdsFor）')
if (start < 0) { console.error('一覧の見出しが無い'); process.exit(2) }
const want = new Map()
let dup = 0
for (let i = start + 1; i < lines.length && !lines[i].startsWith('#'); i++) {
  const l = lines[i]
  if (!l.startsWith('|') || /^\|\s*-/.test(l) || l.startsWith('| 場面')) continue
  const cells = l.split('|').slice(1, -1).map((c) => c.trim())
  for (const [, n] of cells[0].matchAll(/`([^`]+)`/g)) {
    const set = new Set()
    for (const cell of [cells[1], cells[2]]) {
      for (const m of cell.matchAll(/`([^`]+)` `([^`]+)`/g)) set.add(`${m[1].replaceAll('<場面>', n)}=${m[2]}`)
    }
    if (want.has(n)) dup++
    want.set(n, set)
  }
}
let ok = 0, bad = 0, missing = 0, total = 0
for (const f of readdirSync(dir).filter((x) => x.endsWith('.json'))) {
  total++
  const n = f.slice(0, -5)
  const t = JSON.parse(readFileSync(join(dir, f), 'utf8')).thresholds ?? {}
  const got = new Set(Object.entries(t).map(([k, v]) => `${k}=${[].concat(v).join(',')}`))
  const w = want.get(n)
  if (!w) { missing++; console.log(`一覧に無い場面: ${n}`); continue }
  want.delete(n)
  if (got.size === w.size && [...got].every((x) => w.has(x))) ok++
  else { bad++; console.log(`食い違い: ${n}`) }
}
for (const n of want.keys()) console.log(`一覧にだけある場面: ${n}`)
console.log(`場面 ${total}・一致 ${ok}・食い違い ${bad}・一覧に無い ${missing}・一覧にだけある ${want.size}・重複 ${dup}`)
process.exit(bad || missing || want.size || dup ? 1 : 0)
```

```bash
node "$B/check-list.mjs" perf/README.md "$B/inspect/after"; echo "CB6 rc=$?"   # 0（場面 78・一致 78・ほかは 0）
```

- この照合は整理の後に、Build and Test でも流し直せる（`inspect_all after` を流した後）。

### 2.4 k6 の外の線の組（CB5）と、台本の差の形（CB20 の一部）

```bash
# 前（Step 16）: 線を含む行を取り出して保存し、そこから手で組の表（列: 対象・物差し・値、TAB 区切り）を作る
grep -nE 'judge|anon_max_ratio' perf/dsl-mixed-memory.sh > "$B/lines/thr-before.txt"
awk '/^## 性能・資源の目標の見直し/{f=1} f&&/^## 記録/{f=0} f' perf/README.md >> "$B/lines/thr-before.txt"
sed -n '935p' perf/README.md >> "$B/lines/thr-before.txt"
#   → "$B/lines/pairs-before.tsv" を作る（例: dslMixed<TAB>memory.peak/memory.max<TAB><0.95）
# 後（Step 22）: dsl-mixed-memory.sh の同じ行と、README の「今の基準の一覧」の 2 つの表から pairs-after.tsv を作る
LC_ALL=C sort -u "$B/lines/pairs-before.tsv" > "$B/lines/pb"; LC_ALL=C sort -u "$B/lines/pairs-after.tsv" > "$B/lines/pa"
LC_ALL=C comm -23 "$B/lines/pb" "$B/lines/pa" | wc -l     # 0（前の組がすべて後にある）
cut -f1,2 "$B/lines/pa" | LC_ALL=C sort | uniq -d | wc -l  # 0（同じ対象・物差しで値が 2 つ以上無い）

# 台本: scenarios.js はコメントの外の行の多重集合が develop と同じ（宣言を移しただけ）
diff <(git show develop:perf/k6/scenarios.js | grep -vE '^[[:space:]]*//' | grep -v '^[[:space:]]*$' | LC_ALL=C sort) \
     <(grep -vE '^[[:space:]]*//' perf/k6/scenarios.js | grep -v '^[[:space:]]*$' | LC_ALL=C sort) && echo scenarios-ok
# .sh の 2 つはコメントの外の行が順も含めて同じ
for f in perf/k6/thresholds-test.sh perf/dsl-mixed-memory.sh; do
  diff <(git show "develop:$f" | grep -vE '^[[:space:]]*#') <(grep -vE '^[[:space:]]*#' "$f") > /dev/null && echo "$f ok"
done
```

### 2.5 README の行の集合の突き合わせ（CB9〜CB12、L1〜L4）

前（Step 16）に `cp perf/README.md "$B/readme/before.md"`。整理の間に `"$B/lines/map.tsv"`（計画 2節の列）を書き、そこから `map-orig.txt`（元の行）・`map-body.txt`（本文の新しい行）・`map-hist.txt`（履歴へ移した行）を作る。

```bash
norm() { sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' "$1" | grep -v '^$' | LC_ALL=C sort; }
n=$(grep -n '^## 履歴$' perf/README.md | cut -d: -f1)
head -n $((n-1)) perf/README.md > "$B/lines/body.md"; tail -n +"$n" perf/README.md > "$B/lines/hist.md"
L="$B/lines"
norm "$B/readme/before.md" > "$L/before.s"; norm "$L/body.md" > "$L/body.s"; norm "$L/hist.md" > "$L/hist.s"
norm "$L/map-orig.txt" > "$L/morig.s"; norm "$L/map-body.txt" > "$L/mbody.s"; norm "$L/map-hist.txt" > "$L/mhist.s"
wc -l < "$L/before.s"; wc -l < "$L/body.s"; wc -l < "$L/hist.s"; wc -l < "$L/morig.s"   # 行の数を記録する
# L1 欠けない: 前 −（本文＋履歴＋対応表の元の行）が空
LC_ALL=C sort "$L/body.s" "$L/hist.s" "$L/morig.s" > "$L/cover.s"
LC_ALL=C comm -23 "$L/before.s" "$L/cover.s" | wc -l                       # 0
# L2 書き換えの裏付け: 新しい行はすべて本文に、移した行はすべて履歴に（あわせて map.tsv を 1 行ずつ見る）
LC_ALL=C comm -23 "$L/mbody.s" "$L/body.s" | wc -l                         # 0
LC_ALL=C comm -23 "$L/mhist.s" "$L/hist.s" | wc -l                         # 0
# L3 現役が履歴へ落ちない: 前のコードブロックの中の行（コメントの行と、対応表の元の行を除く）と U4 の節の行が本文にあり、履歴に無い
{ awk '/^```/{c=!c; next} c && $0 !~ /^[[:space:]]*#/' "$B/readme/before.md"
  awk '/^## 記録（evidence\/）に置く前の伏せ字$/{f=1} f&&/^## /&&!/^## 記録（evidence/{f=0} f' "$B/readme/before.md"
} > "$L/l3.md"
norm "$L/l3.md" > "$L/l3all.s"; LC_ALL=C comm -23 "$L/l3all.s" "$L/morig.s" > "$L/l3.s"
LC_ALL=C comm -23 "$L/l3.s" "$L/body.s" | wc -l                           # 0（すべて本文にある）
LC_ALL=C comm -12 "$L/l3.s" "$L/hist.s" | wc -l                           # 0（履歴に無い）
# 対応表の元の行のうちコマンドの行（コメントの切り分け）は、コメントの前のコマンドの部分が本文の新しい行と同じことを map.tsv で 1 行ずつ見る
# L4 増えた行:（本文＋履歴）−（前＋新しい行＋移した行）を一覧にし、種類に分ける
LC_ALL=C sort "$L/body.s" "$L/hist.s" > "$L/all.s"; LC_ALL=C sort "$L/before.s" "$L/mbody.s" "$L/mhist.s" > "$L/known.s"
LC_ALL=C comm -23 "$L/all.s" "$L/known.s" > "$L/added.txt"; wc -l < "$L/added.txt"
#   種類: 見出し（^#）・「今の基準の一覧」の表の行・履歴の章の見出しと対応表の行・参照の語（「（履歴 H<番号>）」だけの行）
#   種類に当たらない行が 0 件。数の値を含む行は一覧の表の行だけ（値は CB5・CB6 で確かめたもの）
```

### 2.6 README の事後の検索（CB13〜CB19、Q1〜Q7）

```bash
HEADS=('## 記録（evidence/）に置く前の伏せ字' '### 伏せる対象' '### 記録に置く唯一の入口' '### summary json の対象外（標準出力とログ）' \
       '### 置いた後の確かめ' '### 台本の決まり' '### 一時ファイル')
for h in "${HEADS[@]}"; do printf '%s\t' "$h"; grep -cxF "$h" perf/README.md; done          # Q1: どれも 1 以上
grep -nE 'evidence/' perf/README.md | cut -d: -f1                                           # Q2: 行の番号の一覧（1 行ずつ見る）
grep -nE 'evidence/' perf/README.md \
  | grep -E '(^|[[:space:]`(;|&])(cp|mv|tee)[[:space:]]|[[:space:]]>>?[[:space:]]*[^[:space:]]*evidence/' | wc -l   # Q2: 0
grep -cE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' perf/README.md                # Q3: 0
grep -oE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' perf/README.md \
  | grep -vE '@example\.(com|test)$' | wc -l                                                # Q4: 0（読み方は計画 9節の 1）
n=$(grep -n '^## 履歴$' perf/README.md | cut -d: -f1)
head -n $((n-1)) perf/README.md | grep -cE '(^|[^0-9])26[0-9]{4}([^0-9]|$)'                  # Q5: 0（Intent の名前）
head -n $((n-1)) perf/README.md | grep -cE '20[0-9]{2}-[0-9]{2}-[0-9]{2}'                     # Q5: 0（日付）
grep -oE '[a-z0-9./-]+:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}' perf/README.md | LC_ALL=C sort > "$B/lines/pins-after.txt"
diff "$B/lines/pins-before.txt" "$B/lines/pins-after.txt" && echo Q6-same                     # Q6: 前後で同じ
for img in grafana/otel-lgtm axllent/mailpit; do                                             # Q6: compose.yaml と同じ
  want=$(grep -oE "${img}:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}" compose.yaml)
  grep -ohE "${img}:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}" perf/README.md README.md docker/perf/compose.yaml | sort -u \
    | while read -r got; do [ "$got" = "$want" ] && echo "ok $img" || echo "違う $img"; done
done
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 perf/README.md; echo "Q7 rc=$?"   # 指摘 0 件（rc 0）
```

- `pins-before.txt` は Step 16 で同じ `grep -oE … | sort` で作る。
- 記録には検索の語と件数だけを書き、当たった行の本文を写さない（0 件でないときも行の番号だけ）。

### 2.7 差の範囲（CB20）と記録の検査（CB21）

```bash
git diff --name-only develop...HEAD | grep -vE '^(perf/README\.md|perf/k6/scenarios\.js|perf/k6/thresholds-test\.sh|perf/dsl-mixed-memory\.sh|aidlc/)' | wc -l   # 0
R=aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 "$R/perf-docs-check-record.md"; echo "rc=$?"   # 0
grep -cE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' "$R/perf-docs-check-record.md"                           # 0
```

## 3. 層 A（コンテナの入れ替え）の確かめ（指揮役。依頼者の了承の後に 1 回だけ流す）

```bash
check_digest() {   # CA1・CA4: 使い方 check_digest <サービス> <profile>。正は compose.yaml の image（タグ@index のダイジェスト）
  local svc="$1" prof="$2" want cid cfg img
  want=$(awk -v s="  ${svc}:" '$0==s{f=1;next} f&&/^    image:/{print $2; exit}' compose.yaml)
  cid=$(docker compose --profile "$prof" ps -a -q "$svc")
  cfg=$(docker inspect --format '{{.Config.Image}}' "$cid")
  img=$(docker inspect --format '{{.Image}}' "$cid")
  if [ "$cfg" = "$want" ] && docker image inspect --format '{{json .RepoDigests}}' "$img" \
       | jq -e --arg d "@${want#*@}" 'any(.[]; endswith($d))' > /dev/null; then r=一致; else r=不一致; fi
  echo "${svc} ${want%%@*} digest=$(cut -c8-19 <<< "${want#*@}") image=$(cut -c8-19 <<< "$img") ${r}"
}
# CA2: Mailpit の受信（宛先・差出人は example.com だけ。件名と本文は記録しない）
for i in $(seq 15); do [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8025/api/v1/info)" = 200 ] && break; sleep 2; done
mark="b6-$(date -u +%Y%m%dT%H%M%SZ)-$(openssl rand -hex 4)"
printf 'From: b6-check@example.com\r\nTo: b6-check@example.com\r\nSubject: %s\r\n\r\nB6 receive check\r\n' "$mark" > "$B/mail/msg.txt"
curl -sS --url smtp://127.0.0.1:1025 --mail-from b6-check@example.com --mail-rcpt b6-check@example.com --upload-file "$B/mail/msg.txt"
sleep 1
curl -sS -G --data-urlencode "query=subject:\"${mark}\"" http://127.0.0.1:8025/api/v1/search | jq '.messages_count'   # 1 以上
# CA3: VM の空きメモリ（読み取りだけ）
colima ssh -- free -m | awk '/^Mem:/{print "total=" $2 " available=" $7}'                     # available >= 1792
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}'
# CA5: Grafana の起動（120 秒まで）
for i in $(seq 60); do
  code=$(curl -s -o "$B/h.json" -w '%{http_code}' http://127.0.0.1:3000/api/health || true)
  [ "$code" = 200 ] && [ "$(jq -r .database "$B/h.json" 2> /dev/null)" = ok ] && break; sleep 2
done; echo "health=${code} database=$(jq -r .database "$B/h.json" 2> /dev/null)"; rm -f "$B/h.json"
# CA6: ダッシュボードのファイルの uid ごとに 1 件（60 秒まで読み直す）。全体の件数は参考
for f in docker/monitoring/dashboards/*.json; do
  uid=$(jq -r .uid "$f"); n=0
  for i in $(seq 12); do n=$(curl -s "http://127.0.0.1:3000/api/search?dashboardUIDs=${uid}" | jq 'length'); [ "$n" = 1 ] && break; sleep 5; done
  echo "uid=${uid} count=${n}"
done
curl -s 'http://127.0.0.1:3000/api/search?type=dash-db' | jq 'length'                           # 参考（判定に使わない）
# CA7: lgtm が止まっている
docker ps --format '{{.Names}}' | grep -c lgtm                                                   # 0
# CA8: アプリの起動確認（止まっていたときは docker compose start app の後。T0 は起こす前の UTC の時刻）
cid=$(docker compose ps -a -q app)
for i in $(seq 90); do [ "$(docker inspect --format '{{.State.Health.Status}}' "$cid")" = healthy ] && break; sleep 2; done
code=$(curl -s -o "$B/a.json" -w '%{http_code}' http://127.0.0.1:8080/actuator/health); echo "app=${code} status=$(jq -r .status "$B/a.json")"; rm -f "$B/a.json"
docker compose logs --no-color --no-log-prefix --since "$T0" app 2> /dev/null \
  | jq -cR 'fromjson? | select(.logger=="cherry.mastersmith.user.service.InitialAdminInitializer" and .level=="WARN" and has("conditions"))' \
  | wc -l                                                                                      # 救済の WARN の件数（値は表示しない）
# CA9・CA10: 前後の比べ（Step 4 と Step 13 で同じ形で保存する）
docker ps -a --format '{{.Names}}\t{{.Status}}' | awk -F'\t' '{split($2,a," "); print $1 "\t" a[1]}' | LC_ALL=C sort   # 名前と状態の語
for r in grafana/otel-lgtm axllent/mailpit; do docker image ls --no-trunc --format '{{.ID}}' "$r"; done | LC_ALL=C sort > "$B/ids-after.txt"
LC_ALL=C comm -23 "$B/ids-before.txt" "$B/ids-after.txt" | wc -l                                # CA10: 0（前にあった ID がすべて後にもある）
# CA11・CA12: 入れ替えの記録
R=aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/code-generation
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 "$R/container-swap-record.md"; echo "rc=$?"   # 0
grep -c 'Config\.Env' "$R/container-swap-record.md"                                             # 0
grep -cE 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' "$R/container-swap-record.md"       # 0
```

- `ids-before.txt` は Step 4 で同じ `docker image ls --no-trunc` の形で作る。状態の比べ（CA9）は、状態の語（`Up`・`Exited` など）だけを並べ、時間の語は比べない。
- 戻し（R1〜R5）のときの確かめも CA4〜CA6 と同じコマンドで流す。照らす正は A1 の ID と RepoDigests（計画 3節 Step 10）。

## 4. 期待するカバレッジ

- この単位はアプリのコード（`backend/src/main`・`frontend/src`）を変えないため、この単位だけのカバレッジの目標は無い。
- プロジェクト全体の下限（行 80%・分岐 70%、バックエンドはパッケージごとにも。`team.md` の Testing Posture）は、Build and Test がすべてのテストで測る。統合の前の `./gradlew verify`（計画 Step 27）でも下限の検証が流れるが、この文書の確かめのコマンドではない（プロジェクト全体の検査のため、ここには置かない）。下限と除外は変えない。

## 5. モックと作り物

- モックは使わない。Mailpit は実際に作り直したコンテナに SMTP で送って API で数え、Grafana は実際の `/api/health` と検索の API を読む。
- k6 は `inspect` だけを使い、アプリ・対象DB・網に触れない。負荷の試験の環境（`docker/perf/compose.yaml`）は起動しない。

## 6. 確かめのデータの扱い

- Mailpit に送る 1 通は、宛先・差出人が `b6-check@example.com`、件名は実行ごとに一意の目印、本文は固定の英語の 1 行で、個人に関する値を含まない。送った 1 通は Mailpit に残る（次の作り直しで消える）。
- 外の置き場（`~/.mastersmith-b6/`）の写し・取り出し・対応表・一時のファイルは、記録を書き終えたら消し、消したことを記録する（計画 Step 13・Step 26）。記録（`container-swap-record.md`・`perf-docs-check-record.md`）には件数・判定・名前・先頭 12 文字のダイジェストだけを書く。
- 2.3〜2.6 の前の取り出し（`before`）は整理の前にしか取れないため、コード生成の中で 1 回だけ流す。Build and Test で流し直せるのは 2.1、2.3 の照合（`inspect_all after` の後）、2.6 の検索、2.7 の差の範囲。
