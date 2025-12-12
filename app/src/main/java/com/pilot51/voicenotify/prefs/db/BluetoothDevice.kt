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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "bluetooth_devices",
	indices = [Index(value = ["device_address"], unique = true)]
)
data class BluetoothDevice(
	@PrimaryKey
	@ColumnInfo(name = "device_address")
	val deviceAddress: String,

	@ColumnInfo(name = "device_name")
	val deviceName: String,

	@ColumnInfo(name = "is_enabled")
	val isEnabled: Boolean = true
) {
	companion object {
		const val WIRED_DEVICE_ADDRESS = "__WIRED_DEVICES__"
	}
}
