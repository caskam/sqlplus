package com.tyler.sqlplus.query;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is a builder-like class which allow dynamic queries to be easily be constructed
 */
public class QueryBuilder {

	private List<String> stmts = new ArrayList<>();
	private List<Object> params = new ArrayList<>();
	private Connection conn;
	
	public QueryBuilder(Connection conn) {
		this.conn = conn;
	}
	
	public QueryBuilder query(String sql, Object... params) {
		this.stmts.add(sql);
		addParameters(params);
		return this;
	}
	
	public QueryBuilder queryIf(boolean test, String sql, Object... params) {
		return test ? query(sql, params) : this;
	}
	
	public QueryBuilder queryIfNotNull(Object param, String sql) {
		return queryIf(param != null, sql, param);
	}
	
	public QueryBuilder addParameters(Object... params) {
		if (params.length == 0 || params == null) {
			return this;
		}
		this.params.addAll(Arrays.asList(params));
		return this;
	}
	
	public Query build() {
		Query q = new Query(stmts.stream().collect(Collectors.joining(" ")), conn);
		int i = 1;
		for (Object o : params) {
			q.setParameter(i++, o);
		}
		return q;
	}
	
}