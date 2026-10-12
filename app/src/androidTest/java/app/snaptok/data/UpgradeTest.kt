package app.snaptok.data
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
class UpgradeTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DownloadDatabase::class.java)
    @Test fun photoUpgradePreservesHistoryAndDefaults() {
        helper.createDatabase("v4-photos-upgrade", 3).apply { close() }
        helper.runMigrationsAndValidate("v4-photos-upgrade", 4, true, MIGRATION_3_4).apply {
            query("PRAGMA table_info(downloads)").use { cursor ->
                val names = mutableSetOf<String>(); while (cursor.moveToNext()) names += cursor.getString(1)
                assertTrue(names.containsAll(listOf("photoMode", "slideSeconds", "slideMusic", "outputsJson")))
            }; close()
        }
    }
    @Test fun upgradeToV3KeepsExistingHistoryAndDefaults() {
        helper.createDatabase("v3-upgrade",2).apply {
            execSQL("INSERT INTO downloads(id,url,createdAt,title,status,progress,attempts,error,mediaUri,qualityHeight,destinationTreeUri,destinationLabel,requestKey) VALUES('legacy','https://www.tiktok.com/@a/video/1234567890',1,'Keep me','Saved',100,1,NULL,'content://media/1',720,NULL,'Gallery · Movies/SnapTok','legacy-key')")
            close()
        }
        helper.runMigrationsAndValidate("v3-upgrade",3,true,MIGRATION_2_3).apply {
            query("SELECT title,mediaUri,qualityHeight,unmetered,charging,maxSizeMb,itemIndex,audioOnly,favourite,collection,requestKey FROM downloads WHERE id='legacy'").use {
                assertTrue(it.moveToFirst()); assertEquals("Keep me",it.getString(0)); assertEquals("content://media/1",it.getString(1)); assertEquals(720,it.getInt(2))
                assertEquals(0,it.getInt(3)); assertEquals(0,it.getInt(4)); assertEquals(1024,it.getInt(5)); assertEquals(1,it.getInt(6)); assertEquals(0,it.getInt(7)); assertEquals(0,it.getInt(8)); assertEquals("",it.getString(9)); assertEquals("legacy-key",it.getString(10))
            }; close()
        }
    }
}
