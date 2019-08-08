package ru.capjack.tool.sql

import java.sql.ResultSet
import java.time.LocalDateTime

inline fun ResultSet.forNext(block: (ResultSet) -> Unit): Boolean {
	return if (next()) {
		block(this)
		true
	}
	else false
}

inline fun ResultSet.forEach(block: (ResultSet) -> Unit) {
	while (next()) {
		block(this)
	}
}

fun ResultSet.getLocalDateTime(columnIndex: Int): LocalDateTime? {
	return getTimestamp(columnIndex)?.toLocalDateTime()
}

fun ResultSet.getLocalDateTime(columnLabel: String): LocalDateTime? {
	return getTimestamp(columnLabel)?.toLocalDateTime()
}