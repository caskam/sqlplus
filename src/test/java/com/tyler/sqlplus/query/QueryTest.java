package com.tyler.sqlplus.query;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;

import com.tyler.sqlplus.Query;
import com.tyler.sqlplus.exception.POJOBindException;
import com.tyler.sqlplus.exception.QuerySyntaxException;
import com.tyler.sqlplus.query.QueryTest.Employee.Type;
import com.tyler.sqlplus.rule.H2EmployeeDBRule;
import com.tyler.sqlplus.utility.Tasks.Task;

public class QueryTest {

	@Rule
	public H2EmployeeDBRule h2 = new H2EmployeeDBRule();
	
	protected static void assertThrows(Task t, Class<? extends Throwable> expectType) {
		assertThrows(t, expectType, null);
	}
	
	protected static void assertThrows(Task t, Class<? extends Throwable> expectType, String expectMsg) {
		try {
			t.run();
			fail("Expected test to throw instance of " + expectType.getName() + " but no error was thrown");
		}
		catch (Throwable thrownError) {
			if (!expectType.equals(thrownError.getClass())) {
				fail("Expected test to throw instance of " + expectType.getName() + " but no instead got error of type " + thrownError.getClass().getName());
			}
			if (expectMsg != null) {
				if (!Objects.equals(thrownError.getMessage(), expectMsg)) {
					fail("Expected error with message " + expectMsg + ", instead got message " + thrownError.getMessage());
				}
			}
		}
	}
	
	public static class Employee {
		public enum Type { HOURLY, SALARY; }
		public Integer employeeId;
		public Type type;
		public String name;
		public LocalDate hired;
		public Integer salary;
	}
	
	public static class Address {
		
		public Integer addressId;
		public String street;
		public String city;
		public String state;
		public String zip;
		
		public Address() {}
		
		public Address(String street, String city, String state, String zip) {
			this.street = street;
			this.city = city;
			this.state = state;
			this.zip = zip;
		}
		
	}
	
	@Test
	public void testErrorThrownIfUnkownParamAdded() throws SQLException {
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				Query q = new Query("select * from employee where employee_id = :id", conn);
				q.setParameter("idx", "123");
			}, QuerySyntaxException.class, "Unknown query parameter: idx");
		});
	}
	
	@Test
	public void testErrorThrownIfParamValueNotSet() throws Exception {
		h2.batch("insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')");
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				new Query("select address_id from address where state = :state and city = :city", conn).setParameter("state", "s").getUniqueResultAs(Address.class);
			}, QuerySyntaxException.class, "Missing parameter values for the following parameters: [city]");
		});
	}
	
	@Test
	public void testThrowsIfParamIndexOutOfRange() throws Exception {
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				new Query("select address_id from address where city = ?", conn).setParameter(1, "city").setParameter(2, "state").getUniqueResultAs(Address.class);
			}, QuerySyntaxException.class, "Parameter index 2 is out of range of this query's parameters (max parameters: 1)");
		});
	}
	
	@Test
	public void testErrorThrownIfNoParamsSet() throws Exception {
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				new Query("select * from employee where name = :name", conn).fetch();
			}, QuerySyntaxException.class, "No parameters set");
		});
	}
	
	@Test
	public void testsQueryingWithParameterLabels() throws Exception {
		h2.batch(
			"insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')",
			"insert into address (street, city, state, zip) values('Elm Street', 'Othertown', 'CA', '54321')",
			"insert into address (street, city, state, zip) values('Main Street', 'Bakersfield', 'CA', '54321')"
		);
		h2.getSQLPlus().transact(conn -> {
			Address result = new Query("select address_id as \"addressId\", street as \"street\", state as \"state\", city as \"city\", zip as \"zip\" from address a where state = :state and city = :city", conn)
			                     .setParameter("state", "CA")
			                     .setParameter("city", "Othertown")
			                     .getUniqueResultAs(Address.class);
			assertEquals("Elm Street", result.street);
		});
	}
	
	@Test
	public void testQueryingWithWithMixtureOfParameterLabelsAndQuestionMarks() throws Exception {
		
		h2.batch(
				"insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')",
				"insert into address (street, city, state, zip) values('Elm Street', 'Othertown', 'CA', '54321')"
				);
		
		h2.getSQLPlus().transact(conn -> {
			
			String sql =
					"select address_id as \"addressId\", street as \"street\", state as \"state\", city as \"city\", zip as \"zip\" " +
							"from address a " +
							"where a.city = ? and a.state = :state";
			
			Address addr = new Query(sql, conn).setParameter(1, "Anytown").setParameter("state", "MN").getUniqueResultAs(Address.class);
			assertEquals("Maple Street", addr.street);
			assertEquals("Anytown", addr.city);
			assertEquals("MN", addr.state);
			assertEquals("12345", addr.zip);
		});
	}
	
	@Test
	public void testMappingSinglePOJOWithNoCustomFieldMappings() throws Exception {
		h2.batch(
			"insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')",
			"insert into address (street, city, state, zip) values('Elm Street', 'Othertown', 'CA', '54321')"
		);
		
		List<Address> results = h2.getSQLPlus().fetch(Address.class, "select address_id as \"addressId\", street as \"street\", state as \"state\", city as \"city\", zip as \"zip\" from address");
		assertEquals(2, results.size());
		
		Address first = results.get(0);
		assertEquals(new Integer(1), first.addressId);
		assertEquals("Maple Street", first.street);
		assertEquals("Anytown", first.city);
		assertEquals("MN", first.state);
		assertEquals("12345", first.zip);
		
		Address second = results.get(1);
		assertEquals(new Integer(2), second.addressId);
		assertEquals("Elm Street", second.street);
		assertEquals("Othertown", second.city);
		assertEquals("CA", second.state);
		assertEquals("54321", second.zip);
	}
	
	@Test
	public void testMappingSinglePOJOWithCustomFieldMappings() throws Exception {
		h2.batch(
			"insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')",
			"insert into address (street, city, state, zip) values('Elm Street', 'Othertown', 'CA', '54321')"
		);
		
		List<Address> results = h2.getSQLPlus().query(conn -> {
			return new Query("select address_id as ADD_ID, street as STREET_NAME, state as STATE_ABBR, city as CITY_NAME, zip as POSTAL from address", conn)
						.addColumnMapping("ADD_ID", "addressId")
						.addColumnMapping("STREET_NAME", "street")
						.addColumnMapping("STATE_ABBR", "state")
						.addColumnMapping("CITY_NAME", "city")
						.addColumnMapping("POSTAL", "zip")
						.fetchAs(Address.class);
		});
		
		assertEquals(2, results.size());
		
		Address first = results.get(0);
		assertEquals(new Integer(1), first.addressId);
		assertEquals("Maple Street", first.street);
		assertEquals("Anytown", first.city);
		assertEquals("MN", first.state);
		assertEquals("12345", first.zip);
		
		Address second = results.get(1);
		assertEquals(new Integer(2), second.addressId);
		assertEquals("Elm Street", second.street);
		assertEquals("Othertown", second.city);
		assertEquals("CA", second.state);
		assertEquals("54321", second.zip);
	}
	
	@Test
	public void testMappingSinglePOJOWithCustomFieldMappingsThrowsErrorIfUnknownFieldName() throws Exception {
		
		h2.batch(
			"insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')",
			"insert into address (street, city, state, zip) values('Elm Street', 'Othertown', 'CA', '54321')"
		);
		
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				new Query("select address_id as ADD_ID, street as STREET_NAME, state as STATE_ABBR, city as CITY_NAME, zip as POSTAL from address", conn)
					.addColumnMapping("ADD_ID", "addressId")
					.addColumnMapping("STREET_NAME", "streetName")
					.addColumnMapping("STATE_ABBR", "state")
					.addColumnMapping("CITY_NAME", "city")
					.addColumnMapping("POSTAL", "zip")
					.fetchAs(Address.class);
			}, POJOBindException.class, "Custom-mapped field streetName not found in class " + Address.class.getName() + " for result set column STREET_NAME");
		});
		
	}
	
	@Test
	public void testBatchUpdate() throws Exception {
		
		List<Address> toInsert = Arrays.asList(
			new Address("street1", "city1", "state1", "zip1"),
			new Address("street2", "city2", "state2", "zip2"),
			new Address("street3", "city3", "state3", "zip3"),
			new Address("street4", "city4", "state4", "zip4")
		);
		
		h2.getSQLPlus().batchUpdate("insert into address (street, city, state, zip) values (:street, :city, :state, :zip)", toInsert);
		
		String[][] results = h2.query("select * from address");
		String[][] expect = {
			{"1", "street1", "city1", "state1", "zip1"},
			{"2", "street2", "city2", "state2", "zip2"},
			{"3", "street3", "city3", "state3", "zip3"},
			{"4", "street4", "city4", "state4", "zip4"}
		};
		
		assertArrayEquals(expect, results);
	}
	
	@Test
	public void testBatchExec() throws Exception {
		
		h2.getSQLPlus().batchExec(
			"insert into address (street, city, state, zip) values ('street1', 'city1', 'state1', 'zip1')",
			"insert into address (street, city, state, zip) values ('street2', 'city2', 'state2', 'zip2')",
			"insert into address (street, city, state, zip) values ('street3', 'city3', 'state3', 'zip3')",
			"insert into address (street, city, state, zip) values ('street4', 'city4', 'state4', 'zip4')"
		);
	
		String[][] results = h2.query("select * from address");
		String[][] expect = {
			{"1", "street1", "city1", "state1", "zip1"},
			{"2", "street2", "city2", "state2", "zip2"},
			{"3", "street3", "city3", "state3", "zip3"},
			{"4", "street4", "city4", "state4", "zip4"}
		};
		
		assertArrayEquals(expect, results);
	}

	
	@Test
	public void testFieldsNotPresentInResultSetAreLeftNullInPOJO() throws Exception {
		h2.batch("insert into address (street, city, state, zip) values('Maple Street', 'Anytown', 'MN', '12345')");
		
		Address result = h2.getSQLPlus().findUnique(Address.class, "select street as \"street\", city as \"city\" from address");
		assertNull(result.state);
		assertNull(result.zip);
		assertNotNull(result.street);
		assertNotNull(result.city);
	}
	
	@Test
	public void testMapEnumTypes() throws Exception {
		h2.batch("insert into employee(type, name, salary, hired) values('HOURLY', 'Billy Bob', '42000', '2015-01-01')");
		List<Employee> es = h2.getSQLPlus().fetch(Employee.class, "select employee_id as \"employeeId\", type as \"type\", name as \"name\", salary as \"salary\", hired as \"hired\" from employee");
		assertEquals(Type.HOURLY, es.get(0).type);
	}
	
	@Test
	public void testQueryIntScalar() throws Exception {
		h2.batch(
			"insert into employee(type, name, salary, hired) values ('SALARY', 'Steve Jobs', '41000000', '1982-05-13')",
			"insert into office(office_name, `primary`, employee_id) values ('Office A', 1, 1)",
			"insert into office(office_name, `primary`, employee_id) values ('Office B', 0, 1)",
			"insert into office(office_name, `primary`, employee_id) values ('Office C', 0, 1)"
		);
		Integer total = h2.getSQLPlus().queryInt("select sum(office_id) from office");
		assertEquals(new Integer(6), total);
	}
	
	@Test
	public void testBatchesAreAddedWhenExplicitlyAdded() throws Exception {
		
		h2.getSQLPlus().transact(conn -> {
			
			new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			    .setParameter("type", Type.SALARY)
			    .setParameter("name", "test1")
			    .setParameter("hired", "2015-01-01")
			    .setParameter("salary", "100")
			    .finishBatch()
			    .setParameter("type", Type.SALARY)
			    .setParameter("name", "test2")
			    .setParameter("hired", "2015-01-02")
			    .setParameter("salary", "200")
			    .finishBatch()
			    .executeUpdate();
			
			String[][] expect = {
				{"1", "SALARY", "test1", "2015-01-01", "100", null},
				{"2", "SALARY", "test2", "2015-01-02", "200", null}
			};
			
			String[][] actual = h2.query("select employee_id, type, name, hired, salary, address_id from employee");
			assertArrayEquals(expect, actual);
		});
		
	}
	
	@Test
	public void testTheLastManualBatchIsAutoAddedIfNotExplicitlyAdded() throws Exception {
		
		h2.getSQLPlus().transact(conn -> {
			
			new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			    .setParameter("type", Type.SALARY)
			    .setParameter("name", "test1")
			    .setParameter("hired", "2015-01-01")
			    .setParameter("salary", "100")
			    .finishBatch()
			    .setParameter("type", Type.SALARY)
			    .setParameter("name", "test2")
			    .setParameter("hired", "2015-01-02")
			    .setParameter("salary", "200")
			    // Don't manually add last batch, query should auto-add it
			    .executeUpdate();
			
			String[][] expect = {
				{"1", "SALARY", "test1", "2015-01-01", "100", null},
				{"2", "SALARY", "test2", "2015-01-02", "200", null}
			};
			
			String[][] actual = h2.query("select employee_id, type, name, hired, salary, address_id from employee");
			assertArrayEquals(expect, actual);
		});
	}
	
	@Test
	public void testFinishingABatchWithMissingParametersThrowsError() throws Exception {
		h2.getSQLPlus().transact(conn -> {
			Query q = new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			    .setParameter("type", Type.SALARY)
			    .setParameter("name", "test1")
			    .setParameter("hired", "2015-01-01");
			
			assertThrows(q::finishBatch, QuerySyntaxException.class, "Missing parameter values for the following parameters: [salary]");
		});
	}
	
	@Test
	public void testBindingParamsFromObject() throws Exception {
		
		Employee toCreate = new Employee();
		LocalDate hiredAt = LocalDate.now();
		toCreate.hired = hiredAt;
		toCreate.name = "tester-pojo";
		toCreate.salary = 20000;
		toCreate.type = Type.HOURLY;
		h2.getSQLPlus().transact(conn -> {
			
			new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			    .bind(toCreate)
			    .executeUpdate();
			
			String[] actualRow = h2.query("select type, name, hired, salary from employee")[0];
			String[] expectRow = { Type.HOURLY.name(), "tester-pojo",  hiredAt.toString(), "20000" };
			assertArrayEquals(expectRow, actualRow);
		});
	}
	
	@Test
	public void testBindingObjectParamsWhenSomeFieldIsNullSetsNull() throws Exception {
		
		Employee toCreate = new Employee();
		toCreate.type = Type.HOURLY;
		toCreate.name = "tester-pojo";
		
		h2.getSQLPlus().transact(conn -> {
			
			new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			    .bind(toCreate)
			    .executeUpdate();
			
			String[] actualRow = h2.query("select type, name, hired, salary from employee")[0];
			String[] expectRow = { Type.HOURLY.name(), "tester-pojo",  null, null };
			assertArrayEquals(expectRow, actualRow);
			assertArrayEquals(expectRow, actualRow);
		});
		
	}
	
	public static class EmployeeMissingBindParam {
		public com.tyler.sqlplus.query.QueryTest.Employee.Type type;
		public String name;
		public Integer salary;
	}
	
	@Test
	public void testBindParamsFailsIfNoMemberForParam() throws Exception {
		
		EmployeeMissingBindParam toCreate = new EmployeeMissingBindParam();
		toCreate.name = "tester-pojo";
		toCreate.salary = 20000;
		toCreate.type = Type.HOURLY;
		
		h2.getSQLPlus().transact(conn -> {
			assertThrows(() -> {
				new Query("insert into employee(hired, type, name, salary) values (:hired, :type, :name, :salary)", conn).bind(toCreate);
			}, POJOBindException.class);
		});
	}
	
	@Test
	public void returnGeneratedKeys() throws Exception {
		h2.batch("insert into employee(type, name, hired, salary) values ('SALARY', 'tester-1', '2015-01-01', 20500)");
		
		h2.getSQLPlus().transact(conn -> {
			List<Integer> keys = new Query("insert into employee(type, name, hired, salary) values (:type, :name, :hired, :salary)", conn)
			                                   .setParameter("type", "HOURLY")
			                                   .setParameter("name", "tester-2")
			                                   .setParameter("hired", "2015-01-01")
			                                   .setParameter("salary", "10000")
			                                   .executeUpdate(Integer.class);
			assertEquals(new Integer(2), keys.get(0));
		});
	}

}
