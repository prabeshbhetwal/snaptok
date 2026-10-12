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
    @ColumnInfo(defaultValue = "''") val requestKey: String = downloadRequestKey(url, DownloadOptions(qualityHeight, destinationTreeUri, destinationLabel)),
    @ColumnInfo(defaultValue = "0") val unmetered: Boolean = false,
    @ColumnInfo(defaultValue = "0") val charging: Boolean = false,
    @ColumnInfo(defaultValue = "1024") val maxSizeMb: Int = 1024,
    @ColumnInfo(defaultValue = "1") val itemIndex: Int = 1,
    @ColumnInfo(defaultValue = "0") val audioOnly: Boolean = false,
    @ColumnInfo(defaultValue = "0") val favourite: Boolean = false,
    @ColumnInfo(defaultValue = "''") val collection: String = "",
    @ColumnInfo(defaultValue = "''") val creator: String = "",
    @ColumnInfo(defaultValue = "''") val caption: String = "",
    @ColumnInfo(defaultValue = "''") val sourceId: String = "",
    @ColumnInfo(defaultValue = "'images'") val photoMode: String = "images",
    @ColumnInfo(defaultValue = "3") val slideSeconds: Int = 3,
    @ColumnInfo(defaultValue = "1") val slideMusic: Boolean = true,
    @ColumnInfo(defaultValue = "'[]'") val outputsJson: String = "[]"

)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observeAll(): Flow<List<DownloadJob>>
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadJob?
    @Query("SELECT * FROM downloads WHERE url = :url") suspend fun findUrl(url: String): DownloadJob?
    @Query("SELECT * FROM downloads WHERE requestKey = :key") suspend fun findRequest(key: String): DownloadJob?
    @Query("DELETE FROM downloads WHERE id = :id AND status IN ('Saved', 'Failed', 'Cancelled') AND (status = 'Saved' OR (mediaUri IS NULL AND outputsJson = '[]'))")
    suspend fun removeFinished(id: String): Int
    @Query("SELECT * FROM downloads") suspend fun all(): List<DownloadJob>
    @Query("UPDATE downloads SET favourite = :value WHERE id = :id") suspend fun favourite(id: String, value: Boolean)
    @Query("UPDATE downloads SET collection = :value WHERE id = :id") suspend fun collection(id: String, value: String)
    @Query("UPDATE downloads SET creator = :creator, caption = :caption, sourceId = :sourceId WHERE id = :id")
    suspend fun metadata(id: String, creator: String, caption: String, sourceId: String)
    @Query("UPDATE downloads SET outputsJson = :outputs, mediaUri = CASE WHEN status = 'Saved' THEN mediaUri ELSE NULL END WHERE id = :id") suspend fun outputs(id: String, outputs: String)
    @Query("UPDATE downloads SET status = 'Queued', mediaUri = NULL, progress = 0 WHERE id = :id AND status = 'Saved'") suspend fun missing(id: String): Int
    @Query("UPDATE downloads SET unmetered = :unmetered, charging = :charging, maxSizeMb = :limit WHERE id = :id AND status IN ('Failed', 'Cancelled', 'Saved')")
    suspend fun requestOptions(id: String, unmetered: Boolean, charging: Boolean, limit: Int)
    @Query("UPDATE downloads SET status = 'Queued', error = NULL WHERE id = :id AND status = 'Downloading'")
    suspend fun interrupted(id: String)
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
    @Query("UPDATE downloads SET status = 'Saved', progress = 100, title = :title, mediaUri = :uri, outputsJson = :outputs, destinationLabel = :destination, error = NULL WHERE id = :id AND status = 'Saving'")
    suspend fun savedOutputs(id: String, title: String, uri: String, outputs: String, destination: String): Int
    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id AND status != 'Cancelled'")
    suspend fun failed(id: String, status: String, error: String)
    @Query("UPDATE downloads SET status = 'Cancelled', error = NULL WHERE id = :id AND status IN ('Queued', 'Retrying', 'Downloading')")
    suspend fun cancel(id: String): Int
    @Query("UPDATE downloads SET status = 'Queued', progress = 0, attempts = 0, error = NULL WHERE id = :id AND status IN ('Failed', 'Cancelled')")
    suspend fun reset(id: String): Int
}

@Database(entities = [DownloadJob::class], version = 4, exportSchema = true)
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("unmetered" to "0", "charging" to "0", "maxSizeMb" to "1024", "itemIndex" to "1", "audioOnly" to "0", "favourite" to "0").forEach { (name, default) ->
            db.execSQL("ALTER TABLE downloads ADD COLUMN $name INTEGER NOT NULL DEFAULT $default")
        }
        listOf("collection", "creator", "caption", "sourceId").forEach {
            db.execSQL("ALTER TABLE downloads ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
        }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN photoMode TEXT NOT NULL DEFAULT 'images'")
        db.execSQL("ALTER TABLE downloads ADD COLUMN slideSeconds INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE downloads ADD COLUMN slideMusic INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE downloads ADD COLUMN outputsJson TEXT NOT NULL DEFAULT '[]'")
    }
}
