# Copyright 2026 agwlvssainokuni
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# 使い捨ての環境の内部DB の接続先（PERF_DB_URL）の形を確かめる関数（Intent 261016-business-data-views の U5、
# security-design.md S1、計画の 4.6）。perf/dsl-timing.sh が source で読み、使い捨ての環境を起動する前に呼ぶ。
#
# 受け付ける形:
#   - 前の部分は jdbc:h2:file:/app/data/mastersmith ちょうど（後ろに ; の設定が続くか、何も続かない）
#   - ; で区切った設定は DEFRAG_ALWAYS=TRUE と RETENTION_TIME=<整数>（1〜9 桁の数字）だけ。名前は大文字だけを受け付ける
#   - 全体に空白（改行・タブを含む）・\・'・" を含まない
#   - 空の要素（;; や末尾の ;、前の部分の直後の ; だけ）と、同じ設定の重なりを拒否する
# RETENTION_TIME は接続先の URL の設定で渡せる（H2 2.4.240 の ConnectionInfo が SetTypes の名前を受け付ける）ため、INIT は受け付けない。
#
# 止めたときは、どの決まりに外れたか（前の部分・使えない文字・空の要素・許さない設定・重なり）だけを標準エラーに出し、受け取った値は出さない。

PERF_DB_URL_PREFIX='jdbc:h2:file:/app/data/mastersmith'

# 決まりに外れたことを、値を出さずに標準エラーへ出す。引数: 決まりの名前
perf_db_url_reject() {
  printf 'PERF_DB_URL の形を受け付けません（%s）。値は表示しません。perf/README.md の受け付ける形を見てください。\n' "$1" >&2
}

# 引数の接続先の形を確かめる。受け付けるとき 0、外れたとき 1 を返す。引数: 接続先
check_perf_db_url() {
  local url=${1-} rest part name seen=' '
  case "${url}" in
    "${PERF_DB_URL_PREFIX}" | "${PERF_DB_URL_PREFIX}"\;*) ;;
    *)
      perf_db_url_reject '前の部分'
      return 1
      ;;
  esac
  case "${url}" in
    *[[:space:]]* | *\\* | *\'* | *\"*)
      perf_db_url_reject '使えない文字'
      return 1
      ;;
  esac
  rest=${url#"${PERF_DB_URL_PREFIX}"}
  if [ -z "${rest}" ]; then
    return 0
  fi
  # 前の部分の直後の ; を除いた残りを、; で1つずつ切り出す（IFS の read は空の要素を落とすため使わない）。
  rest=${rest#\;}
  while :; do
    part=${rest%%;*}
    if [ -z "${part}" ]; then
      perf_db_url_reject '空の要素'
      return 1
    fi
    if ! [[ ${part} == DEFRAG_ALWAYS=TRUE || ${part} =~ ^RETENTION_TIME=[0-9]{1,9}$ ]]; then
      perf_db_url_reject '許さない設定'
      return 1
    fi
    name=${part%%=*}
    case "${seen}" in
      *" ${name} "*)
        perf_db_url_reject '重なり'
        return 1
        ;;
    esac
    seen="${seen}${name} "
    if [ "${part}" = "${rest}" ]; then
      break
    fi
    rest=${rest#*;}
  done
  return 0
}
