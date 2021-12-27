#include <ArduinoWebsockets.h>
#include <WiFi.h>
#include <vector>


using namespace websockets;


WebsocketsServer server;

// a collection of all connected clients
std::vector<WebsocketsClient> allClients;
// this method goes thrugh every client and polls for new messages and events
void pollAllClients() {
  for(auto& client : allClients) {
    client.poll();
  }
}

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


 
const char *ssid = "wifiCIT";
const char *password = "skgadi.com";


void setup() {
 
  Serial.begin(115200);
  delay(400);
 


  WiFi.mode(WIFI_AP); 
  WiFi.softAP(ssid, password);
  Serial.println("Wait 100 ms for AP_START...");
  delay(100);
  Serial.println("Setting the AP");
  IPAddress Ip(192, 168, 1, 1);    //setto IP Access Point same as gateway
  IPAddress NMask(255, 255, 255, 0);
  WiFi.softAPConfig(Ip, Ip, NMask);
 
  server.listen(80);
  delay(100);

// configure LED PWM functionalitites
  ledcSetup(1, 5000, 8);
  
  // attach the channel to the GPIO to be controlled
  ledcAttachPin(16, 1);

  pinMode(32, INPUT);
  
}
 

void loop () {

  // while the server is alive
  if(server.available()) {

    // if there is a client that wants to connect
    if(server.poll()) {
      //accept the connection and register callback
      WebsocketsClient client = server.accept();
      client.onMessage(onMessage);

      // store it for later use
      allClients.push_back(client);
    }

    // check for updates in all clients
    pollAllClients();
  } else {
    Serial.println( "Restarting in 1 seconds" );
    delay(1000); 
    ESP.restart();
  }
}

