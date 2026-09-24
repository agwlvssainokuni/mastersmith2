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
 * ときは、両方を同じ値に直し、全検査を通してから統合する。ダイジェストは複数の CPU（amd64・arm64）をまとめた一覧のもの。
 *
 * <p>Testcontainers には、ダイジェストだけで指定する（版とダイジェストを両方書くと、版をイメージの名前の一部と読むため）。
 */
public final class TargetDbImages {

    /** MySQL の版（長く支援される版 8.4 の系列）。 */
    public static final String MYSQL_VERSION = "8.4.11";

    /** MySQL のイメージのダイジェスト。 */
    public static final String MYSQL_DIGEST = "sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d";

    /** MariaDB の版（長く支援される版 11.8 の系列）。 */
    public static final String MARIADB_VERSION = "11.8.9";

    /** MariaDB のイメージのダイジェスト。 */
    public static final String MARIADB_DIGEST =
            "sha256:79d59758afc91b89b120b0a8904d637f5a3b3e1c4900f29b740d6d46c72fef68";

    /** PostgreSQL の版（18 の系列）。 */
    public static final String POSTGRES_VERSION = "18.6";

    /** PostgreSQL のイメージのダイジェスト。 */
    public static final String POSTGRES_DIGEST =
            "sha256:86c951e05bf56c93d95d397747fb8820ac76cc3bedb78f43abd83eedbe3666ae";

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
