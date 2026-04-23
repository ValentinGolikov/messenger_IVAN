package com.example

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val yandexId = varchar("yandex_id", 30).uniqueIndex()
    val displayName = varchar("display_name", 50)
    val realName = varchar("real_name", 100).nullable()
    val email = varchar("email", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}
object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val senderId = integer("sender_id").references(Users.id)
    val text = text("text")
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}