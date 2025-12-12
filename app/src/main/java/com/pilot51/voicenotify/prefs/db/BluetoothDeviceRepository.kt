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

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.pilot51.voicenotify.PermissionHelper.isPermissionGranted
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.bluetoothDeviceDaoFlow
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.enabledBluetoothDevicesFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

object BluetoothDeviceRepository {
	private val ioScope = CoroutineScope(Dispatchers.IO)

	@OptIn(ExperimentalCoroutinesApi::class)
	val devicesFlow = bluetoothDeviceDaoFlow
		.flatMapLatest { it.getAll() }

	val enabledDevicesFlow = enabledBluetoothDevicesFlow

	/**
	 * Syncs database with currently bonded Bluetooth devices.
	 * Adds new devices, removes unbonded devices, preserves enabled state.
	 * Also ensures the wired devices entry exists.
	 */
	suspend fun syncWithBondedDevices(context: Context) = withContext(Dispatchers.IO) {
		val dao = AppDatabase.db.bluetoothDeviceDao

		// Ensure wired devices entry exists (enabled by default on first creation)
		if (dao.getByAddress(BluetoothDevice.WIRED_DEVICE_ADDRESS) == null) {
			dao.insert(BluetoothDevice(
				deviceAddress = BluetoothDevice.WIRED_DEVICE_ADDRESS,
				deviceName = context.getString(R.string.wired_devices),
				isEnabled = true
			))
		}

		if (!hasBluetoothPermission(context)) {
			Log.w(TAG, "Missing Bluetooth permission, cannot sync devices")
			return@withContext
		}

		val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
		val bluetoothAdapter = bluetoothManager?.adapter

		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
			Log.w(TAG, "Bluetooth adapter not available or disabled")
			return@withContext
		}

		val bondedDevices = try {
			bluetoothAdapter.bondedDevices ?: emptySet()
		} catch (e: SecurityException) {
			Log.e(TAG, "SecurityException getting bonded devices", e)
			return@withContext
		}

		val existingDevices = dao.getAll().first()
		val existingAddresses = existingDevices.map { it.deviceAddress }.toSet()
		val bondedAddresses = bondedDevices.map { it.address }.toSet()

		// Remove unbonded devices (but keep wired devices entry)
		existingAddresses.subtract(bondedAddresses).forEach { address ->
			if (address != BluetoothDevice.WIRED_DEVICE_ADDRESS) {
				dao.deleteByAddress(address)
			}
		}

		// Add new bonded devices (enabled by default)
		bondedAddresses.subtract(existingAddresses).forEach { address ->
			val device = bondedDevices.first { it.address == address }
			dao.insert(BluetoothDevice(
				deviceAddress = address,
				deviceName = device.name ?: address,
				isEnabled = true
			))
		}

		// Update names for existing devices (in case they changed)
		existingDevices.forEach { existingDevice ->
			if (existingDevice.deviceAddress == BluetoothDevice.WIRED_DEVICE_ADDRESS) return@forEach
			bondedDevices.find { it.address == existingDevice.deviceAddress }?.let { bondedDevice ->
				val newName = bondedDevice.name ?: existingDevice.deviceAddress
				if (newName != existingDevice.deviceName) {
					dao.update(existingDevice.copy(deviceName = newName))
				}
			}
		}
	}

	suspend fun toggleDevice(address: String) = withContext(Dispatchers.IO) {
		val dao = AppDatabase.db.bluetoothDeviceDao
		val device = dao.getByAddress(address) ?: return@withContext
		dao.setEnabled(address, !device.isEnabled)
	}

	private fun hasBluetoothPermission(context: Context): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			context.isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
		} else {
			context.isPermissionGranted(Manifest.permission.BLUETOOTH)
		}
	}

	private const val TAG = "BluetoothDeviceRepository"
}
