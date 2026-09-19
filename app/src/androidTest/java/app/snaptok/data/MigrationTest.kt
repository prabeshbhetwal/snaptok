package app.snaptok.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DownloadDatabase::class.java)
    @Test fun upgradeKeepsExistingSavedVideoAndAddsSafeDefaults() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO downloads(id,url,createdAt,title,status,progress,attempts,error,mediaUri) VALUES('old','https://www.tiktok.com/@a/video/1234567890',1,'Saved before upgrade','Saved',100,1,NULL,'content://media/old')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).apply {
            query("SELECT title,mediaUri,qualityHeight,destinationTreeUri,destinationLabel,requestKey FROM downloads WHERE id='old'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Saved before upgrade", it.getString(0))
                assertEquals("content://media/old", it.getString(1))
                assertEquals(0, it.getInt(2))
                assertTrue(it.isNull(3))
                assertEquals(DEFAULT_DESTINATION, it.getString(4))
                assertEquals("https://www.tiktok.com/@a/video/1234567890|0|", it.getString(5))
            }
            close()
        }
    }
}
