package il.ronmad.speedruntimer

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import il.ronmad.speedruntimer.realm.Category
import il.ronmad.speedruntimer.realm.Game
import il.ronmad.speedruntimer.realm.Point
import il.ronmad.speedruntimer.realm.Split
import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import java.util.*

const val REALM_SCHEMA_VERSION = 4L

class MyApplication : Application() {

    var installedAppsMap: Map<String, ApplicationInfo> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        initRealm()
    }

    /**
     * Gets the list of installed non-system apps.
     * This is slow — must be called from a background thread.
     */
    fun setupInstalledAppsMap() {
        if (installedAppsMap.isNotEmpty()) return
        installedAppsMap = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && app.packageName != packageName
            }
            .associateBy { app ->
                packageManager.getApplicationLabel(app).toString().lowercase(Locale.US)
            }
    }

    private fun initRealm() {
        Realm.init(this)

        val realmConfig = RealmConfiguration.Builder()
            .schemaVersion(REALM_SCHEMA_VERSION)
            .allowWritesOnUiThread(true)
            .migration(Migration())
            .build()
        Realm.setDefaultConfiguration(realmConfig)
    }
}

/**
 * Explicit RealmMigration class to avoid Kotlin 2.x SAM conversion issues.
 */
private class Migration : RealmMigration {
    private var gamePrimaryKey = 0L
    private var categoryPrimaryKey = 0L
    private var splitPrimaryKey = 0L
    private var pointPrimaryKey = 0L

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val schema = realm.schema
        var version = oldVersion.toInt()

        if (version == 0) {
            val splitSchema = schema.create(Split::class.java.simpleName)
                .addField("name", String::class.java, FieldAttribute.REQUIRED)
                .addField("pbTime", Long::class.java, FieldAttribute.REQUIRED)
                .addField("bestTime", Long::class.java, FieldAttribute.REQUIRED)
            schema.get(Category::class.java.simpleName)
                ?.addRealmListField("splits", splitSchema)
            version++
        }
        if (version == 1) {
            schema.get(Game::class.java.simpleName)?.addIndex("name")
            schema.get(Category::class.java.simpleName)?.addIndex("name")
            schema.get(Split::class.java.simpleName)?.addIndex("name")
            version++
        }
        if (version == 2) {
            schema.get(Game::class.java.simpleName)
                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                ?.transform { it.setLong("id", ++gamePrimaryKey) }
                ?.addPrimaryKey("id")
            schema.get(Category::class.java.simpleName)
                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                ?.transform { it.setLong("id", ++categoryPrimaryKey) }
                ?.addPrimaryKey("id")
            schema.get(Split::class.java.simpleName)
                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                ?.transform { it.setLong("id", ++splitPrimaryKey) }
                ?.addPrimaryKey("id")
            schema.get(Point::class.java.simpleName)
                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                ?.transform { it.setLong("id", ++pointPrimaryKey) }
                ?.addPrimaryKey("id")
            version++
        }
        if (version == 3) {
            schema.get(Category::class.java.simpleName)
                ?.addField("gameName", String::class.java, FieldAttribute.REQUIRED)
                ?.transform { obj ->
                    obj.linkingObjects(Game::class.java.simpleName, "categories")
                        .singleOrNull()
                        ?.let { game ->
                            obj.setString("gameName", game.getString("name"))
                        }
                }
            version++
        }
    }
}
