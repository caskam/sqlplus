package com.tyler.sqlplus.functional;

import java.sql.Connection;

/**
 * Defines the contract for a section of code which consumes a database connection and returns a value
 */
@FunctionalInterface
public interface ReturningDBWork<T> {

	public T doReturningWork(Connection conn) throws Exception;
	
}