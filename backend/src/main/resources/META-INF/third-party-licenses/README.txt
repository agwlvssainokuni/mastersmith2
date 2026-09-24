Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

----

第三者のライセンスの文書

実行可能 WAR に同梱する部品のうち、部品の jar の中にライセンスの文書を含まないものの文書をここに置く。
部品の jar は変更せず、WEB-INF/lib に独立した jar のまま同梱する（利用者が差し替えられる）。

- MariaDB Connector/J（org.mariadb.jdbc:mariadb-java-client）: GNU Lesser General Public License 2.1 以降
  （mariadb-java-client-LGPL-2.1.txt）。ソースは https://github.com/mariadb-corporation/mariadb-connector-j で公開されている。

jar の中に文書を含むもの（ここには置かない）:

- MySQL Connector/J（com.mysql:mysql-connector-j）: GPL v2 ＋ Universal FOSS Exception 1.0（jar の中の LICENSE）
- PostgreSQL JDBC（org.postgresql:postgresql）: BSD 2-Clause（jar の中の META-INF/LICENSE）
- Checker Framework qualifiers（org.checkerframework:checker-qual。PostgreSQL JDBC の依存）: MIT（jar の中の META-INF/LICENSE.txt）
