package com.tyler.sqlplus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.tyler.sqlplus.exception.ConfigurationException;
import com.tyler.sqlplus.exception.SQLRuntimeException;
import com.tyler.sqlplus.functional.DBWork;
import com.tyler.sqlplus.functional.ReturningDBWork;

/**
 * This class is the primary entry point to the SQLPlus API.
 * 
 * An instance of a SQLPLus object provides an interface for executing actions against a database connection
 */
public class SQLPlus {

	private Supplier<Connection> connectionFactory;
	
	public SQLPlus(String url, String user, String pass) {
		this(() -> {
			try {
				return DriverManager.getConnection(url, user, pass);
			}
			catch (Exception ex) {
				throw new ConfigurationException("Failed to connect to database", ex);
			}
		});
	}

	public SQLPlus(DataSource src) {
		this(() -> {
			try {
				return src.getConnection();
			} catch (SQLException e) {
				throw new SQLRuntimeException(e);
			}
		});
	}
	
	public SQLPlus(Supplier<Connection> factory) {
		this.connectionFactory = factory;
	}
	
	public void testConnection() {
		open(conn -> {
			// Throws if problems opening connection
		});
	}
	
	/**
	 * Executes an action against a database connection obtained from this instance's connection factory
	 */
	public void open(DBWork action) {
		query(conn -> {
			action.transact(conn);
			return null;
		});
	}
	
	/**
	 * Executes a value-returning action against a database connection obtained from this instance's connection factory
	 */
	public <T> T query(ReturningDBWork<T> action) {
		try {
			try (Connection conn = connectionFactory.get()) {
				return action.query(conn);
			}
		} catch (Exception e) {
			throw new SQLRuntimeException(e);
		}
	}
	
	public int[] batchExec(String... stmts) {
		return query(conn -> {
			try {
				Statement s = conn.createStatement();
				for (String sql : stmts) {
					s.addBatch(sql);
				}
				return s.executeBatch();
			} catch (SQLException e) {
				throw new SQLRuntimeException(e);
			}
		});
	}
	
	/**
	 * Shortcut for creating a query which applies batch updates using the given entity classes
	 */
	public <T> void batchUpdate(String sql, List<T> entities) {
		open(conn -> {
			Query q = new Query(sql, conn);
			entities.forEach(q::bind);
			q.executeUpdate();
		});
	}
	
	/**
	 * Shortcut method for creating a query which immediately returns a list of maps
	 */
	public List<Map<String, String>> fetch(String sql, Object... params) {
		return query(conn -> {
			Query q = new Query(sql, conn);
			for (int i = 0; i < params.length; i++) {
				q.setParameter(i + 1, params[i]);
			}
			return q.fetch();
		});
	}
	
	/**
	 * Shortcut method for creating a query which immediately returns a list of mapped POJOs
	 */
	public <T> List<T> fetch(Class<T> pojoClass, String sql, Object... params) {
		return query(conn -> {
			Query q = new Query(sql, conn);
			for (int i = 0; i < params.length; i++) {
				q.setParameter(i + 1, params[i]);
			}
			return q.fetchAs(pojoClass);
		});
	}
	
	/**
	 * Shortcut method for creating a query which immediately finds a single instance of a mapped POJO
	 */
	public <T> T findUnique(Class<T> pojoClass, String sql, Object... params) {
		return query(conn -> {
			Query q = new Query(sql, conn);
			for (int i = 0; i < params.length; i++) {
				q.setParameter(i + 1, params[i]);
			}
			return q.getUniqueResultAs(pojoClass);
		});
	}
	
	/**
	 * Shortcut method for querying a single integer scalar value
	 */
	public int queryInt(String sql) {
		return queryScalar(Integer.class, sql);
	}
	
	/**
	 * Shortcut method for querying a single double scalar value
	 */
	public double queryDouble(String sql) {
		return queryScalar(Double.class, sql);
	}
	
	/**
	 * Shortcut method for querying a single boolean scalar value
	 */
	public boolean queryBoolean(String sql) {
		return queryScalar(Boolean.class, sql);
	}
	
	/**
	 * Shortcut method for querying a single string scalar value
	 */
	public String queryString(String sql) {
		return queryScalar(String.class, sql);
	}
	
	/**
	 * Shortcut method for querying a single character scalar value
	 */
	public char queryChar(String sql) {
		return queryScalar(Character.class, sql);
	}
	
	/**
	 * Shortcut method for pulling a scalar value from a query
	 */
	private <T> T queryScalar(Class<T> scalarClass, String sql) {
		return query(conn -> new Query(sql, conn).fetchScalar(scalarClass));
	}
	
}
