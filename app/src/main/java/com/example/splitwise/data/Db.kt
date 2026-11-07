package com.example.splitwise.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {
    @Query("SELECT * FROM GroupEntity WHERE deleted = 0 ORDER BY name COLLATE NOCASE") abstract fun groups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM MemberEntity WHERE deleted = 0 ORDER BY rowid") abstract fun members(): Flow<List<MemberEntity>>
    @Query("SELECT * FROM ExpenseEntity WHERE deleted = 0 ORDER BY date DESC, createdAt DESC") abstract fun expenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM RecurringEntity WHERE deleted = 0 ORDER BY nextDue") abstract fun recurring(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM CommentEntity WHERE deleted = 0 AND expenseId = :expenseId ORDER BY createdAt")
    abstract fun comments(expenseId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM GroupEntity") abstract suspend fun rawGroups(): List<GroupEntity>
    @Query("SELECT * FROM MemberEntity") abstract suspend fun rawMembers(): List<MemberEntity>
    @Query("SELECT * FROM ExpenseEntity") abstract suspend fun rawExpenses(): List<ExpenseEntity>
    @Query("SELECT * FROM CommentEntity") abstract suspend fun rawComments(): List<CommentEntity>
    @Query("SELECT * FROM RecurringEntity") abstract suspend fun rawRecurring(): List<RecurringEntity>

    @Upsert abstract suspend fun upsertGroup(x: GroupEntity)
    @Upsert abstract suspend fun upsertMember(x: MemberEntity)
    @Upsert abstract suspend fun upsertExpense(x: ExpenseEntity)
    @Upsert abstract suspend fun upsertComment(x: CommentEntity)
    @Upsert abstract suspend fun upsertRecurring(x: RecurringEntity)
    @Upsert abstract suspend fun upsertGroups(x: List<GroupEntity>)
    @Upsert abstract suspend fun upsertMembers(x: List<MemberEntity>)
    @Upsert abstract suspend fun upsertExpenses(x: List<ExpenseEntity>)
    @Upsert abstract suspend fun upsertComments(x: List<CommentEntity>)
    @Upsert abstract suspend fun upsertRecurrings(x: List<RecurringEntity>)

    /** "Who am I" is local to each phone, so it deliberately does not touch updatedAt. */
    @Query("UPDATE MemberEntity SET isMe = (id = :memberId) WHERE groupId = :groupId")
    abstract suspend fun setMe(groupId: String, memberId: String)
}

@Database(
    entities = [GroupEntity::class, MemberEntity::class, ExpenseEntity::class, CommentEntity::class, RecurringEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "splitwise.db")
                .fallbackToDestructiveMigration(true)
                .build().also { inst = it }
        }
    }
}
