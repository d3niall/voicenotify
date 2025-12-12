/*
 * Copyright 2011-2025 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pilot51.voicenotify.prefs.db

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.DeleteColumn
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Database(
	version = 4,
	entities = [
		App::class,
		Settings::class,
		BluetoothDevice::class
	],
	autoMigrations = [
		AutoMigration(from = 1, to = 2, spec = AppDatabase.Migration1To2::class),
		AutoMigration(from = 2, to = 3),
		AutoMigration(from = 3, to = 4)
	]
)
@RewriteQueriesToDropUnusedColumns
abstract class AppDatabase : RoomDatabase() {
	abstract val appDao: AppDao
	abstract val settingsDao: SettingsDao
	abstract val bluetoothDeviceDao: BluetoothDeviceDao

	interface BaseDao<T> {
		@Insert
		suspend fun insert(entity: T)

		@Insert
		suspend fun insert(entities: Collection<T>)

		@Update
		suspend fun update(entity: T)

		@Update
		suspend fun update(entity: Collection<T>)

		@Upsert
		suspend fun upsert(entity: T)

		@Delete
		suspend fun delete(entity: T)
	}

	@Dao
	interface AppDao : BaseDao<App> {
		@Query("SELECT * FROM apps WHERE package = :pkg")
		suspend fun get(pkg: String): App

		@Query("SELECT * FROM apps ORDER BY name")
		fun getAllFlow(): Flow<List<App>>

		/** @return The enabled state of an app by package name. */
		@Query("SELECT is_enabled FROM apps WHERE package = :pkg")
		fun isEnabled(pkg: String): Flow<Boolean>

		/** Toggles the enabled state of an app by package name. */
		@Query("UPDATE apps SET is_enabled = NOT is_enabled WHERE package = :pkg")
		suspend fun toggleEnable(pkg: String)

		/** Sets the enabled state of all apps. */
		@Query("UPDATE apps SET is_enabled = :enabled")
		suspend fun updateAllEnable(enabled: Boolean)
	}

	@Dao
	interface SettingsDao : BaseDao<Settings> {
		@Query("SELECT * FROM settings WHERE app_package IS NULL")
		fun getGlobalSettings(): Flow<Settings?>

		@Query("SELECT * FROM settings WHERE app_package = :pkg")
		fun getAppSettings(pkg: String): Flow<Settings?>

		@Query("SELECT app_package FROM settings WHERE app_package IS NOT NULL")
		fun packagesWithOverride(): Flow<List<String>>

		@Query("DELETE FROM settings WHERE app_package = :pkg")
		suspend fun deleteByPackage(pkg: String)
	}

	@Dao
	interface BluetoothDeviceDao : BaseDao<BluetoothDevice> {
		@Query("SELECT * FROM bluetooth_devices ORDER BY device_name")
		fun getAll(): Flow<List<BluetoothDevice>>

		@Query("SELECT * FROM bluetooth_devices WHERE is_enabled = 1")
		fun getEnabled(): Flow<List<BluetoothDevice>>

		@Query("SELECT * FROM bluetooth_devices WHERE device_address = :address")
		suspend fun getByAddress(address: String): BluetoothDevice?

		@Query("UPDATE bluetooth_devices SET is_enabled = :enabled WHERE device_address = :address")
		suspend fun setEnabled(address: String, enabled: Boolean)

		@Query("DELETE FROM bluetooth_devices WHERE device_address = :address")
		suspend fun deleteByAddress(address: String)
	}

	@DeleteColumn(tableName = "apps", columnName = "_id")
	class Migration1To2 : AutoMigrationSpec

	@OptIn(ExperimentalCoroutinesApi::class)
	companion object {
		const val DB_NAME = "apps.db"
		private val _db = MutableStateFlow<AppDatabase?>(buildDB())
		private val dbFlow = _db.filterNotNull()
		val db get() = _db.value ?: runBlocking { withTimeoutOrNull(1.seconds) { dbFlow.first() } }!!
		val appDaoFlow = dbFlow.mapLatest { it.appDao }
		val settingsDaoFlow = dbFlow.mapLatest { it.settingsDao }
		val bluetoothDeviceDaoFlow = dbFlow.mapLatest { it.bluetoothDeviceDao }
		val globalSettingsFlow = settingsDaoFlow.flatMapLatest { it.getGlobalSettings().filterNotNull() }
		val enabledBluetoothDevicesFlow = bluetoothDeviceDaoFlow.flatMapLatest { it.getEnabled() }

		private fun buildDB() = Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME).build()

		fun openDB() { _db.value = buildDB() }

		fun closeDB() {
			db.run {
				_db.value = null
				close()
			}
		}

		fun getAppSettingsFlow(app: App) = settingsDaoFlow.flatMapLatest { dao ->
			dao.getAppSettings(app.packageName).map {
				it ?: Settings(appPackage = app.packageName)
			}
		}
	}
}
