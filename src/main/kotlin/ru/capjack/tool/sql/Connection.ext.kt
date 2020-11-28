package ru.capjack.tool.sql

import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

inline fun <R> Connection.transaction(block: Connection.() -> R): R {
	val a = autoCommit
	autoCommit = false
	try {
		val r = block()
		commit()
		return r
	}
	catch (e: Throwable) {
		rollback()
		throw e
	}
	finally {
		autoCommit = a
	}
}

fun Connection.prepareAddableStatement(@Language("SQL") sql: String): AddablePreparedStatement {
	return AddablePreparedStatementImpl(prepareStatement(sql))
}

fun Connection.prepareAddableStatement(@Language("SQL") sql: String, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): AddablePreparedStatement {
	return AddablePreparedStatementImpl(prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability))
}

fun Connection.prepareAddableStatement(@Language("SQL") sql: String, autoGeneratedKeys: Int): AddablePreparedStatement {
	return AddablePreparedStatementImpl(prepareStatement(sql, autoGeneratedKeys))
}

fun Connection.prepareAddableStatement(@Language("SQL") sql: String, columnIndexes: IntArray): AddablePreparedStatement {
	return AddablePreparedStatementImpl(prepareStatement(sql, columnIndexes))
}

fun Connection.prepareAddableStatement(@Language("SQL") sql: String, columnNames: Array<String>): AddablePreparedStatement {
	return AddablePreparedStatementImpl(prepareStatement(sql, columnNames))
}

///

inline fun <R> Connection.execute(@Language("SQL") sql: String, result: ResultSet.() -> R): R {
	createStatement().use {
		it.executeQuery(sql).use { rs ->
			return rs.result()
		}
	}
}

inline fun Connection.iterate(@Language("SQL") sql: String, action: ResultSet.() -> Unit) {
	execute(sql) {
		forEach(action)
	}
}

inline fun <E> Connection.fetchList(@Language("SQL") sql: String, fetcher: ResultSet.() -> E): List<E> {
	val list = mutableListOf<E>()
	iterate(sql) {
		list.add(fetcher())
	}
	return list
}

inline fun <K, V> Connection.fetchMap(@Language("SQL") sql: String, fetcher: ResultSet.() -> Pair<K, V>): Map<K, V> {
	val map = mutableMapOf<K, V>()
	iterate(sql) {
		val (key, value) = fetcher()
		map[key] = value
		
	}
	return map
}

inline fun <K, V> Connection.fetchGroups(@Language("SQL") sql: String, transform: ResultSet.() -> Pair<K, V>): Map<K, List<V>> {
	val map = mutableMapOf<K, MutableList<V>>()
	iterate(sql) {
		val (key, value) = transform()
		map.getOrPut(key) { mutableListOf() }.add(value)
	}
	return map
}

inline fun <R> Connection.fetch(@Language("SQL") sql: String, result: ResultSet.() -> R): R {
	return fetchOrElse(sql, result) {
		throw SQLException("Query has empty result")
	}
}

inline fun <R> Connection.fetchOrElse(@Language("SQL") sql: String, result: ResultSet.() -> R, other: Connection.() -> R): R {
	execute(sql) {
		if (next()) {
			return result()
		}
	}
	return other()
}

inline fun <R> Connection.fetchMaybe(@Language("SQL") sql: String, result: ResultSet.() -> R): R? {
	return fetchOrElse(sql, result, { null })
}

fun Connection.exists(@Language("SQL") sql: String): Boolean {
	return fetchOrElse(sql, { true }, { false })
}

///

fun Connection.update(@Language("SQL") sql: String): Int {
	val rows = updateMaybe(sql)
	if (rows == 0) {
		throw SQLException("Update has not made any changes")
	}
	return rows
}

fun Connection.updateMaybe(@Language("SQL") sql: String): Int {
	createStatement().use {
		return it.executeUpdate(sql)
	}
}

inline fun Connection.updateOrElse(@Language("SQL") sql: String, other: Connection.() -> Unit) {
	if (0 == updateMaybe(sql)) {
		other()
	}
}


fun Connection.updateAndGetGeneratedKeyInt(@Language("SQL") sql: String): Int {
	return updateAndGetGeneratedKeysOrElse(sql, { getInt(1) }, { throw SQLException("Update has not made any changes") })
}

fun Connection.updateAndGetGeneratedKeyLong(@Language("SQL") sql: String): Long {
	return updateAndGetGeneratedKeysOrElse(sql, { getLong(1) }, { throw SQLException("Update has not made any changes") })
}


fun Connection.updateAndGetGeneratedKeyIntMaybe(@Language("SQL") sql: String): Int? {
	return updateAndGetGeneratedKeysOrElse(sql, { getInt(1) }, { null })
}

fun Connection.updateAndGetGeneratedKeyLongMaybe(@Language("SQL") sql: String): Long? {
	return updateAndGetGeneratedKeysOrElse(sql, { getLong(1) }, { null })
}


fun Connection.updateAndGetGeneratedKeyIntOrElse(@Language("SQL") sql: String, other: Connection.() -> Int): Int {
	return updateAndGetGeneratedKeysOrElse(sql, { getInt(1) }, other)
}

fun Connection.updateAndGetGeneratedKeyLongOrElse(@Language("SQL") sql: String, other: Connection.() -> Long): Long {
	return updateAndGetGeneratedKeysOrElse(sql, { getLong(1) }, other)
}


inline fun <R> Connection.updateAndGetGeneratedKeysOrElse(@Language("SQL") sql: String, result: ResultSet.() -> R, other: Connection.() -> R): R {
	createStatement().use { st ->
		if (st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS) != 0) {
			st.generatedKeys.use {
				if (it.next()) {
					return it.result()
				}
				throw SQLException("Update has empty generate keys")
			}
		}
	}
	return other()
}


// Prepared

inline fun <R> Connection.execute(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, result: ResultSet.() -> R): R {
	prepareAddableStatement(sql).use { st ->
		st.setup()
		st.executeQuery().use {
			return it.result()
		}
	}
}

inline fun Connection.iterate(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, action: ResultSet.() -> Unit) {
	execute(sql, setup) {
		forEach(action)
	}
}

inline fun <E> Connection.fetchList(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, transform: ResultSet.() -> E): List<E> {
	val list = mutableListOf<E>()
	iterate(sql, setup) {
		list.add(transform())
	}
	return list
}

inline fun <K, V> Connection.fetchMap(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, transform: ResultSet.() -> Pair<K, V>): Map<K, V> {
	val map = mutableMapOf<K, V>()
	iterate(sql, setup) {
		val (key, value) = transform()
		map[key] = value
		
	}
	return map
}

inline fun <K, V> Connection.fetchGroups(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, transform: ResultSet.() -> Pair<K, V>): Map<K, List<V>> {
	val map = mutableMapOf<K, MutableList<V>>()
	iterate(sql, setup) {
		val (key, value) = transform()
		map.getOrPut(key) { mutableListOf() }.add(value)
	}
	return map
}

inline fun <R> Connection.fetch(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, result: ResultSet.() -> R): R {
	return fetchOrElse(sql, setup, result) {
		throw SQLException("Query has empty result")
	}
}

inline fun <R> Connection.fetchOrElse(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, result: ResultSet.() -> R, other: Connection.() -> R): R {
	execute(sql, setup) {
		if (next()) {
			return result()
		}
	}
	return other()
}

inline fun <R> Connection.fetchMaybe(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, result: ResultSet.() -> R): R? {
	return fetchOrElse(sql, setup, result, { null })
}

inline fun Connection.exists(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Boolean {
	return fetchOrElse(sql, setup, { true }, { false })
}

///

inline fun Connection.update(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Int {
	val rows = updateMaybe(sql, setup)
	if (rows == 0) {
		throw SQLException("Update has not made any changes")
	}
	return rows
}

inline fun Connection.updateMaybe(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Int {
	prepareAddableStatement(sql).use {
		it.setup()
		return it.executeUpdate()
	}
}

inline fun Connection.updateOrElse(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, other: Connection.() -> Unit) {
	if (0 == updateMaybe(sql, setup)) {
		other()
	}
}

inline fun Connection.updateAndGetGeneratedKeyInt(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Int {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getInt(1) }, { throw SQLException("Update has not made any changes") })
}

inline fun Connection.updateAndGetGeneratedKeyLong(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Long {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getLong(1) }, { throw SQLException("Update has not made any changes") })
}


inline fun Connection.updateAndGetGeneratedKeyIntMaybe(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Int? {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getInt(1) }, { null })
}

inline fun Connection.updateAndGetGeneratedKeyLongMaybe(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit): Long? {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getLong(1) }, { null })
}


inline fun Connection.updateAndGetGeneratedKeyIntOrElse(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, other: Connection.() -> Int): Int {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getInt(1) }, other)
}

inline fun Connection.updateAndGetGeneratedKeyLongOrElse(@Language("SQL") sql: String, setup: AddablePreparedStatement.() -> Unit, other: Connection.() -> Long): Long {
	return updateAndGetGeneratedKeysOrElse(sql, setup, { getLong(1) }, other)
}


inline fun <R> Connection.updateAndGetGeneratedKeysOrElse(
	@Language("SQL") sql: String,
	setup: AddablePreparedStatement.() -> Unit,
	result: ResultSet.() -> R,
	other: Connection.() -> R
): R {
	prepareAddableStatement(sql, Statement.RETURN_GENERATED_KEYS).use { st ->
		st.setup()
		
		if (st.executeUpdate() == 0) {
			return other()
		}
		
		st.generatedKeys.use {
			if (it.next()) {
				return it.result()
			}
			throw SQLException("Update has empty generate keys")
		}
	}
}

inline fun <E> Connection.updateBatch(@Language("SQL") sql: String, collection: Collection<E>, batch: Int = collection.size, setup: AddablePreparedStatement.(E) -> Unit) {
	prepareAddableStatement(sql).use { st ->
		var i = 0
		collection.forEach {
			st.setup(it)
			st.addBatch()
			if (++i == batch) {
				st.executeBatch()
				i = 0
			}
		}
		if (i != 0) {
			st.executeBatch()
		}
	}
}