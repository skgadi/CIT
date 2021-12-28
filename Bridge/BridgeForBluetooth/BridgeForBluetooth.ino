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


uint8_t recBytes[3];



void setup() {
 
  Serial.begin(115200);
  delay(400);
 
// configure LED PWM functionalitites
  ledcSetup(1, 5000, 8);
  
  // attach the channel to the GPIO to be controlled
  ledcAttachPin(16, 1);

  pinMode(32, INPUT);

  SerialBT.begin("btCIT-SKGadi"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  SerialBT.setTimeout(1);

  recBytes[2] = '\n';
}
 

void loop () {
  if ( Serial.available()) {
    Serial.read();
    recBytes[0] = 1;
    recBytes[1] = 31;
    
    SerialBT.write(recBytes,3);
  }
  if (SerialBT.available()) {
    byte recBytes1[2];
    SerialBT.readBytes(recBytes1, 2);
    if (recBytes1[0] == 0) {
      uint16_t adc = analogRead(32);
      //SerialBT.write( (uint8_t*) &adc , 2);
      uint8_t sendItem[3];
      sendItem[0] = adc;
      sendItem[1] = adc>>8;
      sendItem[2] = '\n';
      SerialBT.write(sendItem,3);
    } else {
      ledcWrite(1, recBytes1[1]);
    }
    //Serial.print(recBytes1[0]);
    //Serial.print(recBytes1[1]);
  }
  delay(20);
}

