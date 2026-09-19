package app.snaptok.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object JobStatus {
    const val QUEUED = "Queued"
    const val DOWNLOADING = "Downloading"
    const val SAVING = "Saving"
    const val RETRYING = "Retrying"
    const val SAVED = "Saved"
    const val FAILED = "Failed"
    const val CANCELLED = "Cancelled"
}

@Entity(tableName = "downloads", indices = [Index(value = ["requestKey"], unique = true)])
data class DownloadJob(
    @PrimaryKey val id: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "Video",
    val status: String = JobStatus.QUEUED,
    val progress: Int = 0,
    val attempts: Int = 0,
    val error: String? = null,
    val mediaUri: String? = null,
    @ColumnInfo(defaultValue = "0") val qualityHeight: Int = 0,
    val destinationTreeUri: String? = null,
    @ColumnInfo(defaultValue = "'Gallery · Movies/SnapTok'") val destinationLabel: String = DEFAULT_DESTINATION,
    @ColumnInfo(defaultValue = "''") val requestKey: String = downloadRequestKey(url, DownloadOptions(qualityHeight, destinationTreeUri, destinationLabel))
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadJob>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadJob?
    @Query("SELECT * FROM downloads WHERE url = :url") suspend fun findUrl(url: String): DownloadJob?
    @Query("SELECT * FROM downloads WHERE requestKey = :key") suspend fun findRequest(key: String): DownloadJob?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(job: DownloadJob): Long
    @Query("SELECT * FROM downloads WHERE status IN ('Queued', 'Downloading', 'Saving', 'Retrying') ORDER BY createdAt")
    suspend fun incomplete(): List<DownloadJob>
    @Query("UPDATE downloads SET status = 'Downloading', error = NULL, progress = 0, attempts = :attempt WHERE id = :id AND status NOT IN ('Saved', 'Cancelled', 'Failed')")
    suspend fun start(id: String, attempt: Int): Int
    @Query("UPDATE downloads SET progress = :progress WHERE id = :id AND status = 'Downloading'")
    suspend fun progress(id: String, progress: Int)
    @Query("UPDATE downloads SET status = 'Saving' WHERE id = :id AND status = 'Downloading'")
    suspend fun saving(id: String): Int
    @Query("UPDATE downloads SET status = 'Saved', progress = 100, title = :title, mediaUri = :uri, error = NULL WHERE id = :id")
    suspend fun saved(id: String, title: String, uri: String)
    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id AND status != 'Cancelled'")
    suspend fun failed(id: String, status: String, error: String)
    @Query("UPDATE downloads SET status = 'Cancelled', error = NULL WHERE id = :id AND status IN ('Queued', 'Retrying', 'Downloading')")
    suspend fun cancel(id: String): Int
    @Query("UPDATE downloads SET status = 'Queued', progress = 0, attempts = 0, error = NULL WHERE id = :id AND status IN ('Failed', 'Cancelled')")
    suspend fun reset(id: String): Int
}

@Database(entities = [DownloadJob::class], version = 2, exportSchema = true)
abstract class DownloadDatabase : RoomDatabase() { abstract fun downloads(): DownloadDao }

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN qualityHeight INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE downloads ADD COLUMN destinationTreeUri TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN destinationLabel TEXT NOT NULL DEFAULT 'Gallery · Movies/SnapTok'")
        db.execSQL("ALTER TABLE downloads ADD COLUMN requestKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE downloads SET requestKey = url || '|0|'")
        db.execSQL("DROP INDEX index_downloads_url")
        db.execSQL("CREATE UNIQUE INDEX index_downloads_requestKey ON downloads(requestKey)")
    }
}
