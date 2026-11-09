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
//
// 内部DB の複写（docs/internal-db.md の手順で取った .tgz）を読み取りだけで開き、件数だけを数える台本
// （Intent 261108-ci-checks-release の A3、FR5.3）。前にホームの下に置いていた count-db.sh・count-db2.sh・count-db3.sh
// （と同じ置き場の Java の補助）の数え方を1つにまとめた。
//
// 流し方（リポジトリのルートで。JDK 11 以上の java が PATH にあること）:
//   node scripts/count-db-copy.mjs --copy <複写の .tgz> [--since <T0>] [--set <組>[,<組>…]] [--h2-jar <jar>] [--env <file>]
//   - --copy: 複写の .tgz（必須）。
//   - --since: 監査を数える起点の時刻（ISO-8601。例 2026-11-01T05:00:00Z）。無いときは起点からの件数を数えない。
//   - --set: 数える組（既定 basic）。basic・roles・reflookup・tz・audit をカンマで並べられる。どの組が前のどの道具に
//     当たるかは docs/internal-db.md。
//   - --h2-jar: H2 の jar。無ければ環境変数 H2_JAR、それも無ければ Gradle のキャッシュから backend/gradle.lockfile の版の
//     jar を探す。
//   - --env: 初期管理者のメールアドレス（MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL）を読むファイル（既定 .env）。
//
// 決まり:
// - 作業の置き場は build/count-db-copy/ の下に実行ごとに作り（権限 700）、終わったら（失敗・中断でも）消す。
// - H2 は読み取り（ACCESS_MODE_DATA=r）で開く。出すのは件数・区分の名前（監査の種類・結果・理由）・スキーマの版・
//   DSL の識別子だけで、行の値（メールアドレス・氏名・DSL の本文・業務データ）は出さない。
// - 初期管理者のメールアドレスは .env から読み、子の java へは標準入力で渡す（引数・環境変数・ファイルに置かない）。
//   SQL にはバインド変数で渡す。値は出さず、「.env にあるか」だけを出す。
// - SQL の誤りは例外のクラスの名前と SQLState だけを出す（H2 の誤りの文は値を含みうるため）。java の標準エラーも
//   例外のクラスの名前だけにする。ホームの下のパス（jar・複写の置き場）は出さない（ファイル名だけを出す）。
// - 数える Java のコードは下の JAVA_SOURCE。実行ごとに作業の置き場へ書き、java のソースの直接の実行で動かす。
import { spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const WORK_ROOT = path.join(repoRoot, 'build', 'count-db-copy')
const SETS = ['basic', 'roles', 'reflookup', 'tz', 'audit']
const EMAIL_KEY = 'MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL'
const T0_PATTERN = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})$/

/** 引数・道具の誤り（終了コード 3）。 */
class ToolError extends Error {}

function parseArgs(argv) {
  const options = { copy: null, since: null, sets: ['basic'], h2Jar: null, env: '.env' }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const value = argv[i + 1]
    if (value === undefined || value.startsWith('--'))
      throw new ToolError(`${arg} の値がありません`)
    i++
    if (arg === '--copy') options.copy = value
    else if (arg === '--since') options.since = value
    else if (arg === '--set') options.sets = value.split(',').map((s) => s.trim())
    else if (arg === '--h2-jar') options.h2Jar = value
    else if (arg === '--env') options.env = value
    else throw new ToolError(`分からない引数です: ${arg}`)
  }
  if (options.copy === null) throw new ToolError('--copy <複写の .tgz> がありません')
  const unknown = options.sets.filter((s) => !SETS.includes(s))
  if (unknown.length > 0 || options.sets.length === 0) {
    throw new ToolError(
      `分からない組です: ${unknown.join(',') || '（空）'}（使える組: ${SETS.join('・')}）`,
    )
  }
  if (options.since !== null && !T0_PATTERN.test(options.since)) {
    throw new ToolError('--since は ISO-8601 の時刻（例 2026-11-01T05:00:00Z）で渡してください')
  }
  return options
}

/** H2 の jar を探す（引数 → 環境変数 H2_JAR → Gradle のキャッシュ）。 */
function findH2Jar(given) {
  const explicit = given ?? process.env.H2_JAR
  if (explicit) {
    if (!existsSync(explicit)) throw new ToolError(`H2 の jar が無い: ${path.basename(explicit)}`)
    return { jar: explicit, from: given ? '引数' : '環境変数 H2_JAR' }
  }
  const lock = readFileSync(path.join(repoRoot, 'backend', 'gradle.lockfile'), 'utf8')
  const version = /^com\.h2database:h2:([^=\s]+)=/m.exec(lock)?.[1]
  if (!version) throw new ToolError('backend/gradle.lockfile に H2 の版が無い')
  const gradleHome = process.env.GRADLE_USER_HOME || path.join(os.homedir(), '.gradle')
  const base = path.join(
    gradleHome,
    'caches',
    'modules-2',
    'files-2.1',
    'com.h2database',
    'h2',
    version,
  )
  if (existsSync(base)) {
    for (const hash of readdirSync(base)) {
      const jar = path.join(base, hash, `h2-${version}.jar`)
      if (existsSync(jar)) return { jar, from: 'Gradle のキャッシュ' }
    }
  }
  throw new ToolError(
    `Gradle のキャッシュに h2-${version}.jar が無い（./gradlew :backend:compileJava などで取得するか、--h2-jar で渡す）`,
  )
}

/** .env から初期管理者のメールアドレスを読み、アプリと同じく前後の空白を除き小文字にそろえる（出さない）。 */
function readInitialAdminEmail(envFile) {
  const full = path.resolve(repoRoot, envFile)
  if (!existsSync(full)) return null
  for (const line of readFileSync(full, 'utf8').split('\n')) {
    if (!line.startsWith(`${EMAIL_KEY}=`)) continue
    let value = line.slice(EMAIL_KEY.length + 1).trim()
    if (
      value.length >= 2 &&
      ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'")))
    ) {
      value = value.slice(1, -1)
    }
    value = value.trim().toLowerCase()
    return value === '' ? null : value
  }
  return null
}

/** java の標準エラーから、例外のクラスの名前だけを拾う（文やパスは出さない）。 */
function exceptionClass(stderr) {
  const match = /([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:Exception|Error))\b/.exec(stderr ?? '')
  return match ? match[1] : '（分からない）'
}

/**
 * 前の実行が強制終了・端末の切断などで消せずに残した置き場（work-*）を消す。複写には個人に関する値が入りうるため、
 * 残したままにしない（Code Generation のレビュー R-04）。消した数を返す。
 */
function removeLeftovers() {
  if (!existsSync(WORK_ROOT)) return 0
  const leftovers = readdirSync(WORK_ROOT).filter((name) => name.startsWith('work-'))
  for (const name of leftovers) rmSync(path.join(WORK_ROOT, name), { recursive: true, force: true })
  return leftovers.length
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  const removed = removeLeftovers()
  if (removed > 0) {
    process.stderr.write(`count-db-copy: 前の実行の置き場の残り ${removed} 件を消しました\n`)
  }
  const copy = path.resolve(repoRoot, options.copy)
  if (!existsSync(copy)) throw new ToolError(`複写が無い: ${path.basename(options.copy)}`)
  const { jar, from } = findH2Jar(options.h2Jar)
  const email = readInitialAdminEmail(options.env)

  const work = path.join(WORK_ROOT, `work-${process.pid}-${randomBytes(4).toString('hex')}`)
  const cleanup = () => rmSync(work, { recursive: true, force: true })
  const onSignal = (signal) => {
    cleanup()
    process.exit(signal === 'SIGINT' ? 130 : 143)
  }
  process.once('SIGINT', onSignal)
  process.once('SIGTERM', onSignal)
  try {
    mkdirSync(WORK_ROOT, { recursive: true, mode: 0o700 })
    mkdirSync(path.join(work, 'data'), { recursive: true, mode: 0o700 })
    const tar = spawnSync('tar', ['xzf', copy, '-C', path.join(work, 'data')], { encoding: 'utf8' })
    if (tar.error || tar.status !== 0) throw new ToolError('複写を展開できない（tar が失敗した）')
    if (!existsSync(path.join(work, 'data', 'mastersmith.mv.db'))) {
      throw new ToolError('複写の中に mastersmith.mv.db が無い')
    }
    const source = path.join(work, 'CountDbCopy.java')
    writeFileSync(source, JAVA_SOURCE, { mode: 0o600 })

    process.stdout.write(`# 内部DB の複写の件数\n\n`)
    process.stdout.write(`- 複写: ${path.basename(copy)}\n`)
    process.stdout.write(`- 起点（--since）: ${options.since ?? 'なし'}\n`)
    process.stdout.write(`- 組: ${options.sets.join('・')}\n`)
    process.stdout.write(`- H2 の jar: ${path.basename(jar)}（${from}）\n\n`)
    const args = ['-cp', jar, source, path.join(work, 'data'), options.sets.join(',')]
    if (options.since !== null) args.push(options.since)
    const java = spawnSync('java', args, {
      cwd: work,
      encoding: 'utf8',
      input: `${email ?? ''}\n`,
      maxBuffer: 16 * 1024 * 1024,
    })
    if (java.error) throw new ToolError(`java を起動できない（${java.error.code ?? 'unknown'}）`)
    process.stdout.write('```text\n')
    process.stdout.write(java.stdout)
    process.stdout.write('```\n')
    if (java.status !== 0) {
      process.stderr.write(
        `count-db-copy: java が終了コード ${java.status} で終わった（${exceptionClass(java.stderr)}）\n`,
      )
      return 1
    }
    return 0
  } finally {
    cleanup()
    process.removeListener('SIGINT', onSignal)
    process.removeListener('SIGTERM', onSignal)
  }
}

/**
 * 数える Java のコード（H2 の JDBC だけを使う）。組ごとの問い合わせは、前の道具の SQL をそのまま引き継いだ:
 * basic = count-db.sh、roles = count-db2.sh、reflookup = count-db3.sh（CountRefLookup.java）、tz = CountTz.java、
 * audit = SinceCount.java。前の道具がシェルの H2 Shell に文字列で埋めていたメールアドレスと起点の時刻は、バインド変数で渡す。
 */
const JAVA_SOURCE = String.raw`/*
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** scripts/count-db-copy.mjs が実行ごとに書いて動かす。使い方: <展開した置き場> <組,…> [T0]。メールアドレスは標準入力の1行。 */
public class CountDbCopy {
    static final String ADMIN_ID = "(SELECT user_id FROM users WHERE email = ?)";

    public static void main(String[] a) throws Exception {
        String dir = a[0];
        List<String> sets = List.of(a[1].split(","));
        String t0 = a.length > 2 ? a[2] : null;
        String email = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (email != null && email.isBlank()) email = null;
        System.out.println("init_admin_email_in_env " + (email == null ? 0 : 1));
        String url = "jdbc:h2:file:" + dir + "/mastersmith;ACCESS_MODE_DATA=r";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            for (String set : sets) {
                System.out.println();
                System.out.println("== " + set);
                switch (set) {
                    case "basic" -> basic(c, email, t0);
                    case "roles" -> roles(c, email, t0);
                    case "reflookup" -> reflookup(c, email, t0);
                    case "tz" -> tz(c);
                    case "audit" -> audit(c, t0);
                    default -> System.out.println("分からない組");
                }
            }
        } catch (SQLException e) {
            System.out.println("開けない " + e.getClass().getSimpleName() + " " + e.getSQLState());
        }
        email = null;
    }

    /** 前の count-db.sh。 */
    static void basic(Connection c, String email, String t0) {
        initAdminBySum(c, email);
        one(c, "users_total", "SELECT COUNT(*) FROM users");
        one(c, "admin_true", "SELECT SUM(CASE WHEN admin_flag THEN 1 ELSE 0 END) FROM users");
        one(c, "admin_active", "SELECT SUM(CASE WHEN admin_flag AND NOT suspended THEN 1 ELSE 0 END) FROM users");
        one(c, "suspended_total", "SELECT SUM(CASE WHEN suspended THEN 1 ELSE 0 END) FROM users");
        one(c, "dsl_applied_revisions", "SELECT COUNT(*) FROM dsl_applied_revisions");
        one(c, "dsl_previews", "SELECT COUNT(*) FROM dsl_previews");
        auditByTypeResult(c, t0);
        one(c, "schema_version", "SELECT \"version\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\" DESC LIMIT 1");
    }

    /** 前の count-db2.sh。 */
    static void roles(Connection c, String email, String t0) {
        initAdminBySum(c, email);
        one(c, "schema_version", "SELECT \"version\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\" DESC LIMIT 1");
        one(c, "dsl_applied_revisions", "SELECT COUNT(*) FROM dsl_applied_revisions");
        dslHits(c, new String[] {"customers_hits", "customers"}, new String[] {"customer_orders_hits", "customer_orders"});
        one(c, "dsl_previews", "SELECT COUNT(*) FROM dsl_previews");
        one(c, "roles", "SELECT COUNT(*) FROM roles");
        if (email == null) {
            System.out.println("admin_user_roles・admin_group_roles・admin_work_role 数えない（.env にメールアドレスが無い）");
        } else {
            one(c, "admin_user_roles", "SELECT COUNT(*) FROM user_role_assignments WHERE user_id = " + ADMIN_ID, email);
            one(c, "admin_group_roles", "SELECT COUNT(*) FROM group_role_assignments g JOIN group_members m ON m.group_id = g.group_id WHERE m.user_id = " + ADMIN_ID, email);
            one(c, "admin_work_role", "SELECT COUNT(*) FROM work_role_selections WHERE user_id = " + ADMIN_ID, email);
        }
        auditByTypeResult(c, t0);
    }

    /** 前の count-db3.sh（CountRefLookup.java）。 */
    static void reflookup(Connection c, String email, String t0) {
        one(c, "schema_version", "SELECT \"version\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\" DESC LIMIT 1");
        one(c, "dsl_applied_revisions", "SELECT COUNT(*) FROM dsl_applied_revisions");
        String y = latestDsl(c);
        if (y != null) {
            for (String w : List.of("orders", "customers", "customer_id", "REFERENCE", "LOOKUP", "source: REFERENCE", "source: LOOKUP")) {
                System.out.println("dsl_hits[" + w + "] " + count(y, w));
            }
            referencePaths(y);
        }
        y = null;
        one(c, "dsl_previews", "SELECT COUNT(*) FROM dsl_previews");
        one(c, "roles", "SELECT COUNT(*) FROM roles");
        one(c, "permission_settings", "SELECT COUNT(*) FROM permission_settings");
        one(c, "users_total", "SELECT COUNT(*) FROM users");
        one(c, "admin_active", "SELECT COUNT(*) FROM users WHERE admin_flag AND NOT suspended");
        if (email != null) {
            one(c, "init_admin_rows", "SELECT COUNT(*) FROM users WHERE email = ?", email);
            one(c, "init_admin_suspended", "SELECT COUNT(*) FROM users WHERE email = ? AND suspended", email);
            one(c, "init_admin_flag", "SELECT COUNT(*) FROM users WHERE email = ? AND admin_flag", email);
            one(c, "admin_user_roles", "SELECT COUNT(*) FROM user_role_assignments WHERE user_id = " + ADMIN_ID, email);
            one(c, "admin_group_roles", "SELECT COUNT(*) FROM group_role_assignments g JOIN group_members m ON m.group_id = g.group_id WHERE m.user_id = " + ADMIN_ID, email);
            one(c, "admin_work_role", "SELECT COUNT(*) FROM work_role_selections WHERE user_id = " + ADMIN_ID, email);
            one(c, "admin_work_role_in_assigned", "SELECT COUNT(*) FROM work_role_selections w WHERE w.user_id = " + ADMIN_ID + " AND (w.role_id IN (SELECT role_id FROM user_role_assignments WHERE user_id = w.user_id) OR w.role_id IN (SELECT g.role_id FROM group_role_assignments g JOIN group_members m ON m.group_id = g.group_id WHERE m.user_id = w.user_id))", email);
        }
        one(c, "audit_events_total", "SELECT COUNT(*) FROM audit_events");
        if (t0 != null) {
            one(c, "audit_since_t0", "SELECT COUNT(*) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE)", t0);
            rows(c, "audit_by_type", "SELECT event_type, result, COALESCE(failure_reason, '-'), COUNT(*) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE) GROUP BY event_type, result, failure_reason ORDER BY 1, 2, 3", t0);
            rows(c, "business_row_columns", "SELECT event_type, COUNT(*), SUM(CASE WHEN target_schema IS NULL OR target_schema = '' THEN 1 ELSE 0 END), SUM(CASE WHEN target_table IS NULL OR target_table = '' THEN 1 ELSE 0 END), SUM(CASE WHEN work_role_id IS NULL THEN 1 ELSE 0 END) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE) AND event_type LIKE 'BUSINESS_ROW_%' GROUP BY event_type ORDER BY 1", t0);
            System.out.println("  (business_row_columns の列: 種類 件数 schema空 table空 work_role_id空)");
        }
    }

    /** 前の CountTz.java。 */
    static void tz(Connection c) {
        one(c, "schema_version_max", "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL");
        one(c, "v14_rows", "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '14'");
        boolean col = "1".equals(value(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND UPPER(TABLE_NAME) = 'USERS' AND UPPER(COLUMN_NAME) = 'DISPLAY_TIME_ZONE'"));
        System.out.println("users.display_time_zone " + (col ? "列あり" : "列なし"));
        if (col) {
            one(c, "display_time_zone_set", "SELECT COUNT(*) FROM USERS WHERE DISPLAY_TIME_ZONE IS NOT NULL");
            one(c, "display_time_zone_null", "SELECT COUNT(*) FROM USERS WHERE DISPLAY_TIME_ZONE IS NULL");
        }
        dslHits(c, new String[] {"dsl_hits[customers]", "customers"}, new String[] {"dsl_hits[created_at]", "created_at"});
    }

    /** 前の SinceCount.java。 */
    static void audit(Connection c, String t0) {
        if (t0 == null) {
            System.out.println("audit_by_type_result・audit_since_t0 数えない（--since が無い）");
        } else {
            rows(c, "audit_by_type_result", "SELECT event_type, result, COUNT(*) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE) GROUP BY event_type, result ORDER BY 1, 2", t0);
            one(c, "audit_since_t0", "SELECT COUNT(*) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE)", t0);
        }
        one(c, "audit_events_total", "SELECT COUNT(*) FROM audit_events");
    }

    /** count-db.sh・count-db2.sh の初期管理者の3項目（SUM のため、行が無いと null）。 */
    static void initAdminBySum(Connection c, String email) {
        if (email == null) {
            System.out.println("init_admin_rows・init_admin_suspended・init_admin_flag 数えない（.env にメールアドレスが無い）");
            return;
        }
        one(c, "init_admin_rows", "SELECT COUNT(*) FROM users WHERE email = ?", email);
        one(c, "init_admin_suspended", "SELECT SUM(CASE WHEN suspended THEN 1 ELSE 0 END) FROM users WHERE email = ?", email);
        one(c, "init_admin_flag", "SELECT SUM(CASE WHEN admin_flag THEN 1 ELSE 0 END) FROM users WHERE email = ?", email);
    }

    /** count-db.sh・count-db2.sh の、起点からの監査の種類と結果ごとの件数。 */
    static void auditByTypeResult(Connection c, String t0) {
        if (t0 == null) {
            System.out.println("audit_by_type_result 数えない（--since が無い）");
            return;
        }
        rows(c, "audit_by_type_result", "SELECT event_type, result, COUNT(*) FROM audit_events WHERE occurred_at >= CAST(? AS TIMESTAMP WITH TIME ZONE) GROUP BY event_type, result ORDER BY 1, 2", t0);
    }

    /** 最新の適用済みの DSL に、決めた語が現れる回数だけを出す（本文は出さない）。引数は {名前, 語} の並び。 */
    static void dslHits(Connection c, String[]... labelAndWord) {
        String y = latestDsl(c);
        if (y == null) return;
        for (String[] pair : labelAndWord) {
            System.out.println(pair[0] + " " + count(y, pair[1]));
        }
        y = null;
    }

    /** 最新の適用済みの DSL の本文を返す（無ければ「dsl_latest 無し」と出して null）。本文は出さない。 */
    static String latestDsl(Connection c) {
        try (PreparedStatement s = c.prepareStatement("SELECT yaml_bytes FROM dsl_applied_revisions ORDER BY sequence_no DESC LIMIT 1");
             ResultSet r = s.executeQuery()) {
            if (!r.next()) {
                System.out.println("dsl_latest 無し");
                return null;
            }
            return new String(r.getBytes(1), StandardCharsets.UTF_8);
        } catch (SQLException e) {
            System.out.println("dsl 誤り " + e.getClass().getSimpleName() + " " + e.getSQLState());
            return null;
        }
    }

    /**
     * 本文を行で読み、字下げから写像の鍵の道（schemas/<schema>/tables/<table>/columns/<column>/options/source）をたどり、
     * 選択肢の元が REFERENCE・LOOKUP のカラムと参照先のテーブルの名前（識別子だけ）を出す。値・表示名は出さない。
     * YAML の部品を使わない簡易の判定（生成した DSL の書式を前提にする）。CountRefLookup.java と同じ。
     */
    static void referencePaths(String y) {
        Deque<int[]> inds = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        String pending = null;
        int pendingInd = -1;
        int found = 0;
        for (String raw : y.split("\n", -1)) {
            String t = raw.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int ind = raw.length() - raw.stripLeading().length();
            if (t.startsWith("- ")) {
                t = t.substring(2).strip();
                ind += 2;
            }
            int colon = t.indexOf(':');
            if (colon < 0) continue;
            String key = t.substring(0, colon).strip();
            String val = t.substring(colon + 1).strip().replaceAll("^[\"']|[\"']$", "");
            while (!inds.isEmpty() && inds.peekLast()[0] >= ind) {
                inds.removeLast();
                keys.removeLast();
            }
            if (pending != null && ind == pendingInd && key.equals("table")) {
                System.out.println(pending + " -> " + val);
                pending = null;
            } else if (pending != null && ind < pendingInd) {
                System.out.println(pending + " -> ?");
                pending = null;
            }
            inds.addLast(new int[] {ind});
            keys.addLast(key);
            if (key.equals("source") && (val.equals("REFERENCE") || val.equals("LOOKUP"))) {
                List<String> k = new ArrayList<>(keys);
                int ti = k.indexOf("tables");
                int ci = k.indexOf("columns");
                String id = (ti > 0 && ci > ti + 1 && k.size() > ci + 1)
                        ? k.get(ti - 1) + "." + k.get(ti + 1) + "." + k.get(ci + 1)
                        : "?";
                if (pending != null) System.out.println(pending + " -> ?");
                pending = "dsl_option_source " + val + " " + id;
                pendingInd = ind;
                found++;
            }
        }
        if (pending != null) System.out.println(pending + " -> ?");
        System.out.println("dsl_option_sources " + found);
    }

    static int count(String s, String w) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(w, i)) >= 0) {
            n++;
            i += w.length();
        }
        return n;
    }

    static String value(Connection c, String sql, String... p) {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) s.setString(i + 1, p[i]);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    static void one(Connection c, String label, String sql, String... p) {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) s.setString(i + 1, p[i]);
            try (ResultSet r = s.executeQuery()) {
                System.out.println(label + " " + (r.next() ? r.getString(1) : "該当なし"));
            }
        } catch (SQLException e) {
            System.out.println(label + " 誤り " + e.getClass().getSimpleName() + " " + e.getSQLState());
        }
    }

    static void rows(Connection c, String label, String sql, String... p) {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) s.setString(i + 1, p[i]);
            try (ResultSet r = s.executeQuery()) {
                int cols = r.getMetaData().getColumnCount();
                boolean any = false;
                System.out.println(label + ":");
                while (r.next()) {
                    any = true;
                    StringBuilder b = new StringBuilder("  ");
                    for (int i = 1; i <= cols; i++) b.append(i > 1 ? " " : "").append(r.getString(i));
                    System.out.println(b);
                }
                if (!any) System.out.println("  該当なし");
            }
        } catch (SQLException e) {
            System.out.println(label + " 誤り " + e.getClass().getSimpleName() + " " + e.getSQLState());
        }
    }
}
`

try {
  process.exitCode = main()
} catch (error) {
  if (error instanceof ToolError) {
    process.stderr.write(`count-db-copy: ${error.message}\n`)
    process.exitCode = 3
  } else {
    throw error
  }
}
