package com.sawwere.yoloapp.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.commonsware.cwac.saferoom.SQLCipherUtils
import com.sawwere.yoloapp.BuildConfig
import com.sawwere.yoloapp.core.data.converter.Converters
import com.sawwere.yoloapp.core.data.dao.CategoryDao
import com.sawwere.yoloapp.core.data.dao.PhotoDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.Photo
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Category::class, Photo::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    abstract fun categoryDao(): CategoryDao

    companion object {
        private const val DATABASE_NAME = "signature_app_db"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, key: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {

               return if (BuildConfig.ENCRYPT_DATABASE) {
                   val databaseState = SQLCipherUtils.getDatabaseState(context, DATABASE_NAME)
                   if (databaseState == SQLCipherUtils.State.UNENCRYPTED) {
                       SQLCipherUtils.encrypt(context, DATABASE_NAME, key)
                   }
                   val factory = SupportFactory(key)
                   Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                       .openHelperFactory(factory)
                       .fallbackToDestructiveMigration()
                       .build().also {
                           INSTANCE = it
                       }
                } else {
                   Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                       .fallbackToDestructiveMigration()
                       .build().also {
                           INSTANCE = it
                       }
               }
            }
        }
    }
}