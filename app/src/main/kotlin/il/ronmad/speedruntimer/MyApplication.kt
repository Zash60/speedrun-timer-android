package il.ronmad.speedruntimer

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import il.ronmad.speedruntimer.realm.Category
import il.ronmad.speedruntimer.realm.Game
import il.ronmad.speedruntimer.realm.Point
import il.ronmad.speedruntimer.realm.Split
import io.realm.FieldAttribute
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmSchema
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
        var gamePrimaryKey = 0L
        var categoryPrimaryKey = 0L
        var splitPrimaryKey = 0L
        var pointPrimaryKey = 0L

        val realmConfig = RealmConfiguration.Builder()
            .schemaVersion(REALM_SCHEMA_VERSION)
            .allowWritesOnUiThread(true)
            .migration { realm, oldVersion, _ ->
                var version = oldVersion.toInt()

                fun migrate(block: RealmSchema.() -> Unit) {
                    block()
                    version++
                }

                realm.schema.apply {
                    if (version == 0) {
                        // Split class added, RealmList<Split> field added to Category
                        migrate {
                            val splitSchema = create(Split::class.java.simpleName)
                                .addField("name", String::class.java, FieldAttribute.REQUIRED)
                                .addField("pbTime", Long::class.java, FieldAttribute.REQUIRED)
                                .addField("bestTime", Long::class.java, FieldAttribute.REQUIRED)
                            get(Category::class.java.simpleName)
                                ?.addRealmListField("splits", splitSchema)
                        }
                    }
                    if (version == 1) {
                        // "name" fields indexed
                        migrate {
                            get(Game::class.java.simpleName)?.addIndex("name")
                            get(Category::class.java.simpleName)?.addIndex("name")
                            get(Split::class.java.simpleName)?.addIndex("name")
                        }
                    }
                    if (version == 2) {
                        // Primary keys added to all objects
                        migrate {
                            get(Game::class.java.simpleName)
                                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                                ?.transform { it.setLong("id", ++gamePrimaryKey) }
                                ?.addPrimaryKey("id")
                            get(Category::class.java.simpleName)
                                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                                ?.transform { it.setLong("id", ++categoryPrimaryKey) }
                                ?.addPrimaryKey("id")
                            get(Split::class.java.simpleName)
                                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                                ?.transform { it.setLong("id", ++splitPrimaryKey) }
                                ?.addPrimaryKey("id")
                            get(Point::class.java.simpleName)
                                ?.addField("id", Long::class.java, FieldAttribute.INDEXED)
                                ?.transform { it.setLong("id", ++pointPrimaryKey) }
                                ?.addPrimaryKey("id")
                        }
                    }
                    if (version == 3) {
                        // gameName field added to Category
                        migrate {
                            get(Category::class.java.simpleName)
                                ?.addField("gameName", String::class.java, FieldAttribute.REQUIRED)
                                ?.transform { obj ->
                                    obj.linkingObjects(Game::class.java.simpleName, "categories")
                                        .singleOrNull()
                                        ?.let { game ->
                                            obj.setString("gameName", game.getString("name"))
                                        }
                                }
                        }
                    }
                }
            }
            .build()
        Realm.setDefaultConfiguration(realmConfig)
    }
}
