package com.moodledger.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import com.moodledger.core.Category
import com.moodledger.core.Frequency
import com.moodledger.core.RecurringMode
import com.moodledger.core.TxnType
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun txnType(v: TxnType): String = v.name
    @TypeConverter fun txnType(v: String): TxnType = TxnType.valueOf(v)
    @TypeConverter fun category(v: Category): String = v.name
    @TypeConverter fun category(v: String): Category = Category.fromName(v)
    @TypeConverter fun frequency(v: Frequency): String = v.name
    @TypeConverter fun frequency(v: String): Frequency = Frequency.valueOf(v)
    @TypeConverter fun mode(v: RecurringMode): String = v.name
    @TypeConverter fun mode(v: String): RecurringMode = RecurringMode.valueOf(v)
}

@Dao
interface DayDao {
    @Query("SELECT * FROM day_entries WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<DayEntry>>

    @Query("SELECT * FROM day_entries WHERE epochDay = :day")
    fun observe(day: Long): Flow<DayEntry?>

    @Query("SELECT * FROM day_entries WHERE epochDay = :day")
    suspend fun get(day: Long): DayEntry?

    @Query("SELECT * FROM day_entries WHERE epochDay BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<DayEntry>

    @Upsert suspend fun upsert(entry: DayEntry)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE epochDay = :day ORDER BY createdAt")
    fun observeDay(day: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE epochDay BETWEEN :from AND :to ORDER BY createdAt")
    fun observeRange(from: Long, to: Long): Flow<List<PhotoEntity>>

    @Insert suspend fun insert(photo: PhotoEntity): Long
    @Delete suspend fun delete(photo: PhotoEntity)
}

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, createdAt")
    fun observeRange(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE epochDay = :day ORDER BY createdAt")
    fun observeDay(day: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE epochDay >= :from")
    suspend fun since(from: Long): List<TxnEntity>

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE converted = 0")
    suspend fun unconverted(): List<TxnEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TxnEntity?

    @Insert suspend fun insert(txn: TxnEntity): Long
    @Update suspend fun update(txn: TxnEntity)
    @Update suspend fun updateAll(txns: List<TxnEntity>)
    @Delete suspend fun delete(txn: TxnEntity)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, name")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun active(): List<RecurringEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Insert suspend fun insert(rule: RecurringEntity): Long
    @Update suspend fun update(rule: RecurringEntity)
    @Delete suspend fun delete(rule: RecurringEntity)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates")
    suspend fun all(): List<RateEntity>

    @Query("SELECT * FROM rates")
    fun observeAll(): Flow<List<RateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(rates: List<RateEntity>)
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun all(): List<CategoryRule>

    @Upsert suspend fun upsert(rule: CategoryRule)
}

@Database(
    entities = [
        DayEntry::class, PhotoEntity::class, TxnEntity::class, RecurringEntity::class,
        RateEntity::class, CategoryRule::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun days(): DayDao
    abstract fun photos(): PhotoDao
    abstract fun txns(): TxnDao
    abstract fun recurring(): RecurringDao
    abstract fun rates(): RateDao
    abstract fun categoryRules(): CategoryRuleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "moodledger.db").build()
    }
}
