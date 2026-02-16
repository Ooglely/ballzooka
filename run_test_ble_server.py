import asyncio
import sys

import bumble.logging
from bumble.att import ATT_INSUFFICIENT_ENCRYPTION_ERROR, ATT_Error
from bumble.device import Connection, Device
from bumble.gatt import (
    GATT_CHARACTERISTIC_USER_DESCRIPTION_DESCRIPTOR,
    GATT_DEVICE_INFORMATION_SERVICE,
    GATT_MANUFACTURER_NAME_STRING_CHARACTERISTIC,
    Characteristic,
    CharacteristicValue,
    Descriptor,
    Service,
)
from bumble.transport import open_transport


# -----------------------------------------------------------------------------
class Listener(Device.Listener, Connection.Listener):
    def __init__(self, device):
        self.device = device

    def on_connection(self, connection):
        print(f"=== Connected to {connection}")
        connection.listener = self

    def on_disconnection(self, reason):
        print(f"### Disconnected, reason={reason}")


def my_custom_read(connection):
    print("----- READ from", connection)
    return bytes(f"Hello {connection}", "ascii")


def my_custom_write(connection, value):
    print(f"----- WRITE from {connection}: {value}")


def my_custom_read_with_error(connection):
    print("----- READ from", connection, "[returning error]")
    if connection.is_encrypted:
        return bytes([123])

    raise ATT_Error(ATT_INSUFFICIENT_ENCRYPTION_ERROR)


def my_custom_write_with_error(connection, value):
    print(f"----- WRITE from {connection}: {value}", "[returning error]")
    if not connection.is_encrypted:
        raise ATT_Error(ATT_INSUFFICIENT_ENCRYPTION_ERROR)


# -----------------------------------------------------------------------------
async def main() -> None:
    print("<<< connecting to HCI...")
    async with await open_transport("android-netsim:_:8877") as hci_transport:
        print("<<< connected")

        # Create a device to manage the host
        device = Device.from_config_file_with_hci(
            "./run_test_ble_server.json", hci_transport.source, hci_transport.sink
        )
        device.listener = Listener(device)

        # Add a few entries to the device's GATT server

        manufacturer_name_characteristic = Characteristic(
            GATT_MANUFACTURER_NAME_STRING_CHARACTERISTIC,
            Characteristic.Properties.READ,
            Characteristic.READABLE,
            b"Fitbit",
        )
        sensor_service = Service(
            "ba10f731-f94d-45f8-8ccd-89e393b418f4",
            [
                Characteristic(
                    "D901B45B-4916-412E-ACCA-376ECB603B2C",
                    Characteristic.Properties.READ | Characteristic.Properties.WRITE,
                    Characteristic.READABLE | Characteristic.WRITEABLE,
                    CharacteristicValue(read=my_custom_read, write=my_custom_write),
                ),
                Characteristic(
                    "552957FB-CF1F-4A31-9535-E78847E1A714",
                    Characteristic.Properties.READ | Characteristic.Properties.WRITE,
                    Characteristic.READABLE | Characteristic.WRITEABLE,
                    CharacteristicValue(
                        read=my_custom_read_with_error, write=my_custom_write_with_error
                    ),
                ),
                Characteristic(
                    "486F64C6-4B5F-4B3B-8AFF-EDE134A8446A",
                    Characteristic.Properties.READ | Characteristic.Properties.NOTIFY,
                    Characteristic.READABLE,
                    bytes("hello", "utf-8"),
                ),
            ],
        )
        device.add_services([sensor_service])

        # Debug print
        for attribute in device.gatt_server.attributes:
            print(attribute)

        # Get things going
        await device.power_on()

        await device.start_advertising(auto_restart=True)

        await hci_transport.source.terminated


# -----------------------------------------------------------------------------
bumble.logging.setup_basic_logging("DEBUG")
asyncio.run(main())
