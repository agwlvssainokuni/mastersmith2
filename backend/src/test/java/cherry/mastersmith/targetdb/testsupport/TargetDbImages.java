/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.targetdb.testsupport;

import org.testcontainers.utility.DockerImageName;

/**
 * 対象DB の結合テストのイメージの版とダイジェスト（NFR12.1。1か所の定数）。
 *
 * <p>compose.yaml の手元で試す対象DB（profile {@code targetdb-*}）も同じ値を使う（{@code <版>@<ダイジェスト>}）。版を上げる
 * ときは、両方を同じ値に直し、全検査を通してから統合する。負荷の試験の使い捨ての環境（docker/perf/compose.yaml）も同じ値に
 * そろえる。ダイジェストは複数の CPU（amd64・arm64）をまとめた一覧のもの。
 *
 * <p>Testcontainers には、ダイジェストだけで指定する（版とダイジェストを両方書くと、版をイメージの名前の一部と読むため）。
 */
public final class TargetDbImages {

    /**
     * MySQL の版（26.7 の系列）。長く支援される版（8.4 の系列）に限る決まりを外し、Dependabot の更新を結合テストで確かめて
     * 上げた（Intent 260928-quality-followup の FR5.2）。
     */
    public static final String MYSQL_VERSION = "26.7.0";

    /** MySQL のイメージのダイジェスト。 */
    public static final String MYSQL_DIGEST = "sha256:ade067ae2fb15eeb6143e81185afe22573e3b3addc58aaf90c41999fa6883991";

    /**
     * MariaDB の版（13.0 の系列）。長く支援される版（11.8 の系列）に限る決まりを外し、Dependabot の更新を結合テストで確かめて
     * 上げた（Intent 260928-quality-followup の FR5.2）。
     */
    public static final String MARIADB_VERSION = "13.0.2";

    /** MariaDB のイメージのダイジェスト。 */
    public static final String MARIADB_DIGEST =
            "sha256:d4fdec0510ad498e4f3127da30a99df3745bd6d5e611ae6ac5f76403d9284a8d";

    /** PostgreSQL の版（18 の系列）。 */
    public static final String POSTGRES_VERSION = "18.6";

    /** PostgreSQL のイメージのダイジェスト。 */
    public static final String POSTGRES_DIGEST =
            "sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722";

    private TargetDbImages() {}

    /**
     * MySQL のイメージを返す。
     *
     * @return イメージ（ダイジェストで固定）
     */
    public static DockerImageName mysql() {
        return DockerImageName.parse("mysql@" + MYSQL_DIGEST);
    }

    /**
     * MariaDB のイメージを返す。
     *
     * @return イメージ（ダイジェストで固定）
     */
    public static DockerImageName mariadb() {
        return DockerImageName.parse("mariadb@" + MARIADB_DIGEST);
    }

    /**
     * PostgreSQL のイメージを返す。
     *
     * @return イメージ（ダイジェストで固定）
     */
    public static DockerImageName postgres() {
        return DockerImageName.parse("postgres@" + POSTGRES_DIGEST);
    }
}
