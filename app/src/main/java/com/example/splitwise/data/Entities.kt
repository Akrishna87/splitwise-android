package com.example.splitwise.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Every row has a UUID id, an updatedAt and a soft-delete flag so groups can be merged between phones. */
@Entity
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val kind: String = "GROUP", // GROUP or FRIEND
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(indices = [Index("groupId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val isMe: Boolean = false,
    val upiId: String = "",
    val paypalUser: String = "",
    val venmoUser: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(indices = [Index("groupId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amount: Long,            // in [currency] minor units
    val currency: String,
    val rate: Double = 1.0,      // 1 [currency] = rate group-currency units
    val baseAmount: Long,        // in the group's currency
    val paidById: String,
    val category: String = "General",
    val note: String = "",
    val date: Long,              // epoch day
    val splitType: String = "EQUAL",
    val sharesJson: String,      // {memberId: owed in group currency minor units}
    val inputsJson: String = "{}",
    val itemsJson: String = "[]",
    val receiptPath: String = "",
    val isSettlement: Boolean = false,
    val recurringId: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(indices = [Index("expenseId")])
data class CommentEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val expenseId: String,
    val author: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(indices = [Index("groupId")])
data class RecurringEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amount: Long,
    val currency: String,
    val rate: Double = 1.0,
    val baseAmount: Long,
    val paidById: String,
    val category: String = "General",
    val note: String = "",
    val splitType: String = "EQUAL",
    val sharesJson: String,
    val inputsJson: String = "{}",
    val itemsJson: String = "[]",
    val interval: String,
    val nextDue: Long,           // epoch day
    val active: Boolean = true,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
