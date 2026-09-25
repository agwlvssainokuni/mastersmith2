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

# アプリのコンテナ（1段）。Gradle で作った実行可能 WAR（./gradlew verify または :backend:bootWar）をコピーするだけで、
# イメージの中ではビルドしない。ベースイメージは Eclipse Temurin の JRE 25（Ubuntu）で、版の番号までタグで固定する。
FROM eclipse-temurin:25.0.4_7-jre-noble

# root 以外の専用の利用者で動かし、内部DBのファイルの置き場所（/app/data）はその利用者だけが読み書きできるようにする。
RUN groupadd --system --gid 10001 mastersmith \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --no-create-home --shell /usr/sbin/nologin mastersmith \
    && mkdir -p /app/data \
    && chown 10001:10001 /app/data \
    && chmod 700 /app/data

WORKDIR /app
COPY backend/build/libs/mastersmith.war /app/mastersmith.war

USER 10001:10001
EXPOSE 8080

# 既定の JVM の引数: 最大ヒープはコンテナのメモリの上限の 50%。タイムゾーンは Asia/Tokyo（compose の TZ と合わせる）。
#   50% にした理由（Intent 260925-storage-memory-fixes の FR2・計画 D1・D2）: 75% では 10MB の DSL の投入とログインを重ねたとき
#   （k6 の dslMixed）、ヒープが最大（上限 2g の 1,536MiB）まで広がり、ヒープ以外（約 320MiB）と合わせてプロセスのメモリが上限の
#   約 93% に達し、コンテナのメモリの上限での回収（memory.events の max）が起きた。止まっている間の生きているものは約 84MiB で、
#   大半は JVM の大きさの設定による。50% では同じ条件で上限の約 69%・max 0 回だった（計画の前の測定）。
# JVM の設定の口: 環境変数 MASTERSMITH_JAVA_OPTIONS の値を既定の引数の後ろに置く。JVM の -XX の指定は後に書いたものが効くため、
#   割合などを上書きでき、ヒープ以外の上限（-XX:MaxMetaspaceSize・-Xss・-XX:MaxDirectMemorySize など）も足せる。
#   値は空白で区切る（空白を含む値は扱わない）。set -f で * などのファイル名の展開を止める。渡さないときは今までと同じ引数になる。
#   JVM 標準の JAVA_TOOL_OPTIONS・JDK_JAVA_OPTIONS は使わない（コマンド行の引数より前に読まれて既定の 50% に上書きされ、
#   起動の時に「Picked up ...」の1行を標準エラーに出して1行1件の JSON のログの形を崩すため）。
# docker run の引数（"$@"）も同じ位置に渡す（確かめのとき -XX:+PrintFlagsFinal -version を付けて、アプリを起動せずに値を読む。
#   docker/check-container-limits.sh）。
# exec で sh を java に置き換えて java をコンテナの PID 1 にし、停止の合図（SIGTERM）を Java が直接受け取るようにする（穏やかな停止）。
ENTRYPOINT ["sh", "-c", "set -f; exec java -XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo ${MASTERSMITH_JAVA_OPTIONS:-} \"$@\" -jar /app/mastersmith.war", "mastersmith"]
