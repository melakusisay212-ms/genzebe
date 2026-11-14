package com.genzebe.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GenzebeDao {
    /** Returns -1 when a transaction with the same dedupeKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTx(tx: TxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwnerName(owner: OwnerNameEntity)

    @Update
    suspend fun updateTx(tx: TxEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TxEntity>>

    @Query("SELECT * FROM accounts ORDER BY label")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT name FROM owner_names")
    suspend fun ownerNames(): List<String>

    @Query("SELECT * FROM transactions WHERE accountKey = :accountKey ORDER BY occurredAt ASC, id ASC")
    suspend fun txForAccountAsc(accountKey: String): List<TxEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt ASC, id ASC")
    suspend fun allTxAsc(): List<TxEntity>

    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :from AND :to ORDER BY occurredAt ASC, id ASC")
    suspend fun txBetween(from: Long, to: Long): List<TxEntity>

    @Query("SELECT * FROM transactions WHERE transferGroup = :group")
    suspend fun txByGroup(group: String): List<TxEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun txById(id: Long): TxEntity?

    @Query("SELECT * FROM accounts WHERE accountKey = :accountKey")
    suspend fun account(accountKey: String): AccountEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTx(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTx()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("DELETE FROM owner_names")
    suspend fun deleteAllOwnerNames()
}
