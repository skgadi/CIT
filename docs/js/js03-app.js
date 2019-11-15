var app = new Vue ({
    el: "#app",
    data: {
        lang: lang,
        sLang: "en",
        sAlgorithm: "sResponse",
        display: {
            "diagram": false,
            "bridgeDevice": false,
            "params": false,
            "graphs": false,
        }
    },
    computed: {
      cLang: function () {
          return this.lang[this.sLang];
      }
    }
});