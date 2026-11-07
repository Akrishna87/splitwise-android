package com.example.splitwise.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class GroupEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(foreignKeys = [ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId")])
data class MemberEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val groupId: Long, val name: String)

@Entity(foreignKeys = [ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val description: String,
    val amount: Long,
    val paidById: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isSettlement: Boolean = false,
)

@Entity(foreignKeys = [ForeignKey(ExpenseEntity::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expenseId")])
data class ShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val memberId: Long,
    val owed: Long,
)

@Dao
abstract class Dao {
    @Query("SELECT * FROM GroupEntity ORDER BY id DESC") abstract fun groups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM GroupEntity WHERE id = :id") abstract fun group(id: Long): Flow<GroupEntity?>
    @Query("SELECT * FROM MemberEntity WHERE groupId = :g ORDER BY id") abstract fun members(g: Long): Flow<List<MemberEntity>>
    @Query("SELECT * FROM ExpenseEntity WHERE groupId = :g ORDER BY createdAt DESC") abstract fun expenses(g: Long): Flow<List<ExpenseEntity>>
    @Query("SELECT s.* FROM ShareEntity s JOIN ExpenseEntity e ON e.id = s.expenseId WHERE e.groupId = :g")
    abstract fun shares(g: Long): Flow<List<ShareEntity>>

    @Insert abstract suspend fun insert(g: GroupEntity): Long
    @Insert abstract suspend fun insert(m: MemberEntity): Long
    @Insert abstract suspend fun insert(e: ExpenseEntity): Long
    @Insert abstract suspend fun insertShares(s: List<ShareEntity>)
    @Query("DELETE FROM ExpenseEntity WHERE id = :id") abstract suspend fun deleteExpense(id: Long)
    @Query("DELETE FROM GroupEntity WHERE id = :id") abstract suspend fun deleteGroup(id: Long)

    @Transaction
    open suspend fun addExpense(e: ExpenseEntity, owed: Map<Long, Long>) {
        val id = insert(e)
        insertShares(owed.filter { it.value != 0L }.map { ShareEntity(expenseId = id, memberId = it.key, owed = it.value) })
    }
}

@Database(entities = [GroupEntity::class, MemberEntity::class, ExpenseEntity::class, ShareEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): Dao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "splitwise.db")
                .build().also { inst = it }
        }
    }
}
