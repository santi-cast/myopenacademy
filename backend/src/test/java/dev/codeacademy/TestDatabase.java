package dev.codeacademy;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

final class TestDatabase {

	private static final String CONTENTS = """
			select n.nspname as schema_name, c.relname as table_name,
			       query_to_xml(format('select * from %I.%I order by 1', n.nspname, c.relname),
			                    true, false, '')::text as contents
			from pg_class c join pg_namespace n on c.relnamespace = n.oid
			where c.relkind = 'r' and n.nspname not in ('pg_catalog', 'information_schema')
			  and n.nspname not like 'pg_toast%'
			""";

	private TestDatabase() {
	}

	/** Every application table with its full contents, so a rejected request can prove it changed nothing. */
	static List<Map<String, Object>> snapshot(JdbcTemplate jdbc) {
		return jdbc.queryForList(CONTENTS + "order by n.nspname, c.relname");
	}

	/** The full contents of the named code_academy tables, to prove an allowed change left them untouched. */
	static List<Map<String, Object>> snapshot(JdbcTemplate jdbc, String... tables) {
		return jdbc.queryForList(CONTENTS + "and n.nspname = 'code_academy' and c.relname = any (?) "
				+ "order by n.nspname, c.relname", (Object) tables);
	}
}
