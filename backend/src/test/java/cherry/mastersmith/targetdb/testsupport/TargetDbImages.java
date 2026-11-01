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
     * MySQL の版（26.10 の系列）。長く支援される版（8.4 の系列）に限る決まりを外し、Dependabot の更新を結合テストで確かめて
     * 上げた（Intent 260928-quality-followup の FR5.2）。
     */
    public static final String MYSQL_VERSION = "26.10.0";

    /** MySQL のイメージのダイジェスト。 */
    public static final String MYSQL_DIGEST = "sha256:7b16ec7caa94ca7e92f4c5ec287de69d2e8738db056ceb629022951a6f6d910b";

    /**
     * MariaDB の版（13.0 の系列）。長く支援される版（11.8 の系列）に限る決まりを外し、Dependabot の更新を結合テストで確かめて
     * 上げた（Intent 260928-quality-followup の FR5.2）。
     */
    public static final String MARIADB_VERSION = "13.0.2";

    /** MariaDB のイメージのダイジェスト。 */
    public static final String MARIADB_DIGEST =
            "sha256:f4054da26e3a9dd6c8b5cc97363e544408b15c0fb369d7359c25dc04826eb6e1";

    /** PostgreSQL の版（18 の系列）。 */
    public static final String POSTGRES_VERSION = "18.6";

    /** PostgreSQL のイメージのダイジェスト。 */
    public static final String POSTGRES_DIGEST =
            "sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81";

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
