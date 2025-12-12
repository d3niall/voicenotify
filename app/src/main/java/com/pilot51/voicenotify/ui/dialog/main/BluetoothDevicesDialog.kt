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
package com.pilot51.voicenotify.ui.dialog.main

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.PermissionHelper.isPermissionGranted
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.db.BluetoothDeviceRepository
import com.pilot51.voicenotify.ui.VNPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BluetoothDevicesDialog(
	onDismiss: () -> Unit,
	onRequestPermission: () -> Unit
) {
	val context = LocalContext.current
	val devices by BluetoothDeviceRepository.devicesFlow.collectAsState(initial = emptyList())
	val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		context.isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
	} else {
		context.isPermissionGranted(Manifest.permission.BLUETOOTH)
	}

	// Sort devices: Wired devices first, then Bluetooth devices alphabetically
	val sortedDevices = remember(devices) {
		devices.sortedWith(compareBy(
			{ it.deviceAddress != com.pilot51.voicenotify.prefs.db.BluetoothDevice.WIRED_DEVICE_ADDRESS },
			{ it.deviceName }
		))
	}

	LaunchedEffect(hasPermission) {
		if (hasPermission) {
			BluetoothDeviceRepository.syncWithBondedDevices(context)
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = { Text(stringResource(R.string.bluetooth_devices_dialog_title)) },
		text = {
			Column {
				if (!hasPermission) {
					Text(
						text = stringResource(R.string.bluetooth_permission_required),
						modifier = Modifier.padding(bottom = 16.dp)
					)
					TextButton(onClick = onRequestPermission) {
						Text(stringResource(R.string.grant_permission))
					}
				} else if (sortedDevices.isEmpty()) {
					Text(stringResource(R.string.no_bluetooth_devices))
				} else {
					LazyColumn {
						item {
							Text(
								text = stringResource(R.string.bluetooth_devices_description),
								modifier = Modifier.padding(bottom = 8.dp),
								fontSize = 14.sp
							)
						}
						itemsIndexed(sortedDevices) { _, device ->
							Row(
								modifier = Modifier
									.toggleable(
										value = device.isEnabled,
										onValueChange = {
											CoroutineScope(Dispatchers.IO).launch {
												BluetoothDeviceRepository.toggleDevice(device.deviceAddress)
											}
										},
										role = Role.Checkbox
									)
									.fillMaxWidth()
									.heightIn(min = 56.dp)
									.wrapContentHeight(align = Alignment.CenterVertically)
									.padding(horizontal = 16.dp),
								horizontalArrangement = Arrangement.spacedBy(10.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Checkbox(
									checked = device.isEnabled,
									onCheckedChange = null
								)
								Column {
									Text(
										text = device.deviceName,
										fontSize = 16.sp
									)
									if (device.deviceAddress != com.pilot51.voicenotify.prefs.db.BluetoothDevice.WIRED_DEVICE_ADDRESS) {
										Text(
											text = device.deviceAddress,
											fontSize = 12.sp,
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
									}
								}
							}
						}
					}
				}
			}
		}
	)
}

@VNPreview
@Composable
private fun BluetoothDevicesDialogPreview() {
	AppTheme {
		BluetoothDevicesDialog(
			onDismiss = {},
			onRequestPermission = {}
		)
	}
}
