/* 
 * File:   app.h
 * Author: gadis
 *
 * Created on April 28, 2018, 12:37 AM
 */

#ifndef APP_H
#define	APP_H

#ifdef	__cplusplus
extern "C" {
#endif

//#include "mcc_generated_files/usb/fixed_address_memory.h"
#include "mcc_generated_files/mcc.h"

#define I2C_EEPROM_SIZE 0x40
uint8_t EEPROM_Buffer[I2C_EEPROM_SIZE];
uint8_t USBBuffer[I2C_EEPROM_SIZE];
void WriteToUSB ();
void FillEEPROM (void);
bool WRIETOUSB = false;

typedef enum _ProgrammingStatus {
    WAITING_FOR_USB_INPUT,
    RECEIVED_USB_INPUT,
    SENT_TO_I2C,
    RECEIVED_FROM_I2C,
    WRITTEN_TO_USB
} ProgrammingStatus;
ProgrammingStatus ProgStatus = WAITING_FOR_USB_INPUT;

#ifdef	__cplusplus
}
#endif

#endif	/* APP_H */

