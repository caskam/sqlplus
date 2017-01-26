package com.tyler.sqlplus;

import com.tyler.sqlplus.exception.SqlRuntimeException;
import com.tyler.sqlplus.functional.ReturningWork;
import com.tyler.sqlplus.functional.Work;
import com.tyler.sqlplus.proxy.TransactionAwareService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * This class is the primary entry point to the SQLPlus API.
 * 
 * An instance of a SQLPLus object provides an interface for executing actions against a database connection
 */
public class SqlPlus {

	private static final ThreadLocal<Session> CURRENT_THREAD_SESSION = new ThreadLocal<>();
	
	private DataSource dataSource;

	@SuppressWarnings("unused")
	private SqlPlus() {}
	
	public SqlPlus(String url, String user, String pass) {
		this(new BasicDataSource().setUrl(url).setUsername(user).setPassword(pass));
	}
	
	public SqlPlus(Supplier<Connection> connectionFactory) {
		this(new BasicDataSource() {
			
			@Override
			public Connection getConnection(String user, String pass) {
				throw new UnsupportedOperationException("This data source cannot supply connections for an arbitrary user");
			}

			@Override
			public Connection getConnection() {
				return connectionFactory.get();
			}
			
		});
	}
	
	public SqlPlus(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public <T> T createTransactionAwareService(Class<T> klass) throws InstantiationException, IllegalAccessException {
		return TransactionAwareService.create(klass, this);
	}

	public Session getCurrentSession() {
		Session currentSession = CURRENT_THREAD_SESSION.get();
		if (currentSession == null) {
			throw new IllegalStateException("No session is bound to the current thread");
		}
		return currentSession;
	}

	/**
	 * Executes an action inside of a single database transaction.
	 * 
	 * If any exceptions are thrown, the transaction is immediately rolled back
	 */
	public void transact(Work<Session> action) {
		query(session -> {
			action.doWork(session);
			return null;
		});
	}
	
	/**
	 * Executes a value-returning action against a database connection obtained from this instance's connection factory
	 */
	public <T> T query(ReturningWork<Session, T> action) {

		Session currentSession = CURRENT_THREAD_SESSION.get();
		if (currentSession != null) {
			try {
				return action.doReturningWork(currentSession);
			} catch (Exception e) {
				throw new SqlRuntimeException(e);
			}
		}

		Connection conn = null;
		T result;

		try {
			conn = dataSource.getConnection();
			conn.setAutoCommit(false);
			Session newSession = new Session(conn);
			CURRENT_THREAD_SESSION.set(newSession);
			result = action.doReturningWork(newSession);
			conn.commit();
		}
		catch (Exception e) {
			CURRENT_THREAD_SESSION.remove();
			if (conn != null) {
				try {
					conn.rollback();
					conn.close();
				} catch (SQLException e2) {
					throw new SqlRuntimeException(e2);
				}
			}
			throw new SqlRuntimeException(e);
		}

		CURRENT_THREAD_SESSION.remove();
		try {
			conn.close();
		} catch (SQLException e) {
			throw new SqlRuntimeException(e);
		}

		return result;
	}
	
	public int[] batchExec(String... stmts) {
		try (Connection conn = dataSource.getConnection()) {
			Statement s = conn.createStatement();
			for (String sql : stmts) {
				s.addBatch(sql);
			}
			return s.executeBatch();
		}
		catch (SQLException e) {
			throw new SqlRuntimeException(e);
		}
	}

}
