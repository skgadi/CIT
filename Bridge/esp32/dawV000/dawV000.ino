/*Program to use GATT service on ESP32 to send Battery Level
 * ESP32 works as server - Mobile as client
 * Program by: B.Aswinth Raj
 * Dated on: 13-10-2018
 * Website: www.circuitdigest.com
 */

 
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h> //Library to use BLE as server
#include <BLE2902.h> 

 

bool _BLEClientConnected = false;

#define CIT_Service BLEUUID((uint16_t) 0x181C)
//Actuators
BLECharacteristic CHR_ACT0(BLEUUID((uint16_t)0x0000), BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
BLECharacteristic CHR_ACT1(BLEUUID((uint16_t)0x0001), BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
//Sensors
BLECharacteristic CHR_SEN0(BLEUUID((uint16_t)0x0002), BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE_NR);
BLECharacteristic CHR_SEN1(BLEUUID((uint16_t)0x0003), BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE_NR);

BLEDescriptor DES_ACT0(BLEUUID((uint16_t)0x2901));
BLEDescriptor DES_ACT1(BLEUUID((uint16_t)0x2901));

BLEDescriptor DES_SEN0(BLEUUID((uint16_t)0x2901));
BLEDescriptor DES_SEN1(BLEUUID((uint16_t)0x2901));

class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      _BLEClientConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      _BLEClientConnected = false;
    }
};

void InitBLE() {
  BLEDevice::init("CIT-Bridge");
  // Create the BLE Server
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  // Create the BLE Service
  BLEService *pCITService = pServer->createService(CIT_Service);

  //Characteristics
  pCITService->addCharacteristic(&CHR_ACT0);
  pCITService->addCharacteristic(&CHR_ACT1);
  pCITService->addCharacteristic(&CHR_SEN0);
  pCITService->addCharacteristic(&CHR_SEN1);

  //Descriptor
  DES_ACT0.setValue("Instantanious voltage out. Channel 0");
  DES_ACT1.setValue("Instantanious voltage out. Channel 1");
  DES_SEN0.setValue("A.C. voltage out. Channel 0");
  DES_SEN1.setValue("A.C. voltage out. Channel 1");


  CHR_ACT0.addDescriptor(&DES_ACT0);
  CHR_ACT1.addDescriptor(&DES_ACT1);
  CHR_SEN0.addDescriptor(&DES_SEN0);
  CHR_SEN1.addDescriptor(&DES_SEN1);

  CHR_ACT0.addDescriptor(new BLE2902());
  CHR_ACT1.addDescriptor(new BLE2902());
  CHR_SEN0.addDescriptor(new BLE2902());
  CHR_SEN1.addDescriptor(new BLE2902());
  


  pServer->getAdvertising()->addServiceUUID(CIT_Service);

  pCITService->start();
  // Start advertising
  pServer->getAdvertising()->start();
}

void setup() {
  /*Serial.begin(115200);
  Serial.println("Battery Level Indicator - BLE");
  */
  InitBLE();
}

  float ACT0, ACT1;
  float SEN0, SEN1;
void loop() {
  SEN0 = 0.004884004884004884 * analogRead(34) - 10.0;
  SEN1 = 0.004884004884004884 * analogRead(35) - 10.0;
  
  CHR_SEN0.setValue((uint8_t*)(&SEN0), 4);
  CHR_SEN1.setValue((uint8_t*)(&SEN1), 4);
/*
  BLE_DCI0.notify();
  BLE_DCI1.notify();
  BLE_ACI0.notify();
  BLE_ACI1.notify();
  BLE_ENC0.notify();
  BLE_ENC1.notify();
/**/
}
