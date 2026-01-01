package com.example.bleadvertiser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserDataStore(context: Context) {

    private val appContext = context.applicationContext

    val userData: Flow<UserIdentity> = appContext.dataStore.data.map { preferences ->
        UserIdentity(
            name = preferences[KEY_NAME] ?: "",
            apartment = preferences[KEY_APARTMENT] ?: "",
            role = Role.valueOf(preferences[KEY_ROLE] ?: Role.NONE.name),
            ownerToken = preferences[KEY_OWNER_TOKEN],
            signalId = preferences[KEY_SIGNAL_ID],
            bleKey = preferences[KEY_BLE_KEY],
            isGeofenceSet = preferences[KEY_GEOFENCE_SET] ?: false,
            txPowerPos = preferences[KEY_TX_POWER_POS] ?: 0,
            advModePos = preferences[KEY_ADV_MODE_POS] ?: 0
        )
    }

    suspend fun saveIdentity(name: String, apartment: String, role: Role) {
        appContext.dataStore.edit { preferences ->
            preferences[KEY_NAME] = name
            preferences[KEY_APARTMENT] = apartment
            preferences[KEY_ROLE] = role.name
        }
    }

    suspend fun saveResidentData(signalId: String, bleKey: String) {
        appContext.dataStore.edit { preferences ->
            preferences[KEY_SIGNAL_ID] = signalId
            preferences[KEY_BLE_KEY] = bleKey
        }
    }
    
    suspend fun saveAdvSettings(txPowerPos: Int, advModePos: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[KEY_TX_POWER_POS] = txPowerPos
            preferences[KEY_ADV_MODE_POS] = advModePos
        }
    }

    suspend fun saveGeofenceStatus(isSet: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[KEY_GEOFENCE_SET] = isSet
        }
    }

    suspend fun saveOwnerToken(token: String) {
        appContext.dataStore.edit { preferences ->
            preferences[KEY_OWNER_TOKEN] = token
        }
    }

    suspend fun clearData() {
        appContext.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        private val KEY_NAME = stringPreferencesKey("user_name")
        private val KEY_APARTMENT = stringPreferencesKey("user_apartment")
        private val KEY_ROLE = stringPreferencesKey("user_role")
        private val KEY_OWNER_TOKEN = stringPreferencesKey("owner_token")
        private val KEY_SIGNAL_ID = stringPreferencesKey("signal_id")
        private val KEY_BLE_KEY = stringPreferencesKey("ble_key")
        private val KEY_GEOFENCE_SET = booleanPreferencesKey("geofence_set")
        private val KEY_TX_POWER_POS = intPreferencesKey("tx_power_pos")
        private val KEY_ADV_MODE_POS = intPreferencesKey("adv_mode_pos")
    }
}

data class UserIdentity(
    val name: String,
    val apartment: String,
    val role: Role,
    val ownerToken: String?,
    val signalId: String?,
    val bleKey: String?,
    val isGeofenceSet: Boolean,
    val txPowerPos: Int,
    val advModePos: Int
)

enum class Role {
    OWNER,
    RESIDENT,
    NONE
}
