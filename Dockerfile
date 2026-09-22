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

# exec 形式で起動し、停止の合図（SIGTERM）を Java が直接受け取るようにする（穏やかな停止）。
# 最大ヒープはコンテナのメモリの 75%。タイムゾーンは Asia/Tokyo（compose の TZ と合わせる）。
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Tokyo", "-jar", "/app/mastersmith.war"]
