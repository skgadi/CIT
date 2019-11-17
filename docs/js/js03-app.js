var app = new Vue({
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
        },
        sInputs: {},
        sOutputs: {}
    },
    watch: {
        sAlgorithm: function () {
        },
        sLang: function () {
            }
    },
    mounted: function () {
        document.getElementById("loading").style.display = "none";
        document.getElementById("app").style.display = "block";
    },
    updated: function () {
        updateMathJax();
    },
    computed: {
        cLang: function () {
            return this.lang[this.sLang];
        }
    },
    methods: {
    }
});