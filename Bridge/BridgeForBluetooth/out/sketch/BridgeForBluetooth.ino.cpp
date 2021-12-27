#include <Arduino.h>
#line 1 "c:\\Suresh\\git\\CIT\\Bridge\\BridgeForBluetooth\\BridgeForBluetooth.ino"
#include <BluetoothSerial.h>
BluetoothSerial SerialBT;


/*
// this callback is common for all clients, the client that sent that
// message is the one that gets the echo response
void onMessage(WebsocketsClient& client, WebsocketsMessage message) {
  uint16_t adc = analogRead(32);
  if (message.rawData().at(0) == 0) {
    client.sendBinary((char *) &adc, 2);
    //Serial.println("Requested adc...");
  } else {
    ledcWrite(1, message.rawData().at(1));
    //Serial.println("Got pwm...");
  }
}
*/


#line 21 "c:\\Suresh\\git\\CIT\\Bridge\\BridgeForBluetooth\\BridgeForBluetooth.ino"
void setup();
#line 40 "c:\\Suresh\\git\\CIT\\Bridge\\BridgeForBluetooth\\BridgeForBluetooth.ino"
void loop();
#line 21 "c:\\Suresh\\git\\CIT\\Bridge\\BridgeForBluetooth\\BridgeForBluetooth.ino"
void setup() {
 
  Serial.begin(115200);
  delay(400);
 
// configure LED PWM functionalitites
  ledcSetup(1, 5000, 8);
  
  // attach the channel to the GPIO to be controlled
  ledcAttachPin(16, 1);

  pinMode(32, INPUT);

  SerialBT.begin("CIT"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  
}
 

void loop () {
  if ( Serial.available())
        SerialBT.write(Serial.read());
  if (SerialBT.available()) {
    byte recBytes[2];
    SerialBT.readBytes(recBytes, 2);
    if (recBytes[0] == 0) {
      uint16_t adc = analogRead(32);
      SerialBT.write( (uint8_t*) &adc , 2);
      /*char sendItem[2];
      sendItem[0] = adc;
      sendItem[1] = adc>>8;
      SerialBT.print(sendItem);*/
    } else {
      ledcWrite(1, recBytes[1]);
    }
    Serial.write(SerialBT.read());
  }
  delay(20);
}


