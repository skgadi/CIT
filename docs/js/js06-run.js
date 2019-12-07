async function run(server) {
    app.$set(app.$data.ble, "server", await server);
    app.$set(app.$data.ble, "service", await app.$data.ble.server.getPrimaryService(app.$data.ble.sUuid));
    app.$set(app.$data.ble, "cAct0", await app.$data.ble.service.getCharacteristic(0x0000));
    app.$set(app.$data.ble, "cAct1", await app.$data.ble.service.getCharacteristic(0x0001));
    app.$set(app.$data.ble, "cSen0", await app.$data.ble.service.getCharacteristic(0x0002));
    app.$set(app.$data.ble, "cSen1", await app.$data.ble.service.getCharacteristic(0x0003));

    console.log((await app.$data.ble.cSen0.readValue()).getFloat32());
}