package com.example.bleadvertiser

import java.util.UUID

object Constants {
    // ESP32 tarafında tanımlanacak UUID'ler
    val PROVISIONING_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb") // TODO: ESP32 ile eşleştir
    val ADD_RESIDENT_CHAR_UUID: UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb") // TODO: ESP32 ile eşleştir
}
