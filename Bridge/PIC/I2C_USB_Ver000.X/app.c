#include "app.h"

void WriteToUSB () {
    //USBDeviceTasks();
    if((USBGetDeviceState() < CONFIGURED_STATE) ||
       (USBIsDeviceSuspended() == true))
    {
        //Either the device is not configured or we are suspended
        //  so we don't want to do execute any application code
        return;//continue;   //go back to the top of the while loop
    }
    else
    {
        //Keep trying to send data to the PC as required
        CDCTxService();
        if(USBUSARTIsTxTrfReady())
        {
            int numBytes;
            numBytes = getsUSBUSART(USBBuffer, sizeof(USBBuffer)); //until the buffer is free.
            if (numBytes>0) {
                switch (USBBuffer[0]) {
                    case 0x01:
                        ProgStatus = RECEIVED_USB_INPUT;
                        for (int i=numBytes-1; i>=0; i--)
                            EEPROM_Buffer[i] = USBBuffer[i+1];
                        break;
                    case 0x02:
                        putUSBUSART(EEPROM_Buffer, sizeof(EEPROM_Buffer));
                        break;
                }
            }
            if (EEPROM_Buffer[I2C_EEPROM_SIZE-1]>0) {
                putUSBUSART(EEPROM_Buffer, sizeof(EEPROM_Buffer));
                EEPROM_Buffer[I2C_EEPROM_SIZE-1] = 0;
            }
        }


        //Run application code.
        //UserApplication();
    }/**/
}

void FillEEPROM (void) {
    for (uint8_t i = 0; i<sizeof(EEPROM_Buffer); i++ ) {
        EEPROM_Buffer[i] = i+2;
    }
}