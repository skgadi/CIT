var app = new Vue({
    el: "#app",
    data: {
        lang: lang,
        sLang: "en",
        sAlgorithm: "sResponse",
        display: {
            "mModel": false,
            "bridgeDevice": false,
            "params": false,
            "fGens": false,
            "graphs": false,
        },
        sInputs: {},
        iGains: {},
        iOffsets: {},
        sOutputs: {},
        oGains: {},
        oOffsets: {},
        params: {},
        fGens: {},
        defaults: {
            sInputs: "analog",
            iGains: 1,
            iOffsets: 0,
            sOutputs: "dc0",
            oGains: 1,
            oOffsets: 0
        }
    },
    watch: {
        sAlgorithm: function () {},
        sLang: function () {}
    },
    mounted: function () {
        document.getElementById("loading").style.display = "none";
        document.getElementById("app").style.display = "block";
    },
    updated: function () {
        this.resetInvalidsToDefault();
        updateMathJax();
    },
    computed: {
        cLang: function () {
            return this.lang[this.sLang];
        },
        mModel: function () {
            return `<iframe src='https://docs.google.com/gview?url=https://cit.skgadi.com/m-models/${this.cLang.algorithms[this.sAlgorithm].documentation}&embedded=true' style='width:100%; height:300px;' frameborder='0'></iframe>`;
        }
    },
    methods: {
        resetInvalidsToDefault: function () {
            //Input and outputs
            Object.keys(this.cLang.algorithms[this.sAlgorithm].inputs).forEach((key) => {
                if (app.$data.sInputs[key] === undefined) app.$set(app.$data.sInputs, key, app.$data.defaults.sInputs);
                if (app.$data.iOffsets[key] === undefined) app.$set(app.$data.iOffsets, key, app.$data.defaults.iOffsets);
                if (app.$data.iGains[key] === undefined) app.$set(app.$data.iGains, key, app.$data.defaults.iGains);
            });
            Object.keys(this.cLang.algorithms[this.sAlgorithm].outputs).forEach((key) => {
                if (!app.$data.sOutputs[key]) app.$set(app.$data.sOutputs, key, app.$data.defaults.sOutputs);
                if (app.$data.oOffsets[key] === undefined) app.$set(app.$data.oOffsets, key, app.$data.defaults.oOffsets);
                if (app.$data.oGains[key] === undefined) app.$set(app.$data.oGains, key, app.$data.defaults.oGains);
            });
            Object.keys(app.$data.defaults).forEach(function (key) {
                Object.keys(app.$data[key]).forEach(function (key1) {
                    if (!app.$data[key][key1]) {
                        app.$set(app.$data[key], key1, app.$data.defaults[key]);
                    }
                })
            });
            //Params
            if (!!this.cLang.algorithms[this.sAlgorithm].params) {
                Object.keys(this.cLang.algorithms[this.sAlgorithm].params).forEach((key) => {
                    if (app.$data.params[key] === undefined) app.$set(app.$data.params, key, app.cLang.algorithms[app.sAlgorithm].params[key].dValue);
                });
            }
            //fGens
            if (!!this.cLang.algorithms[this.sAlgorithm].fGens) {
                Object.keys(this.cLang.algorithms[this.sAlgorithm].params).forEach((key) => {
                    if (app.$data.fGens[key] === undefined) app.$set(app.$data.fGens, key, {sType: 'step', params: {amp: 1}});
                });
            }
        }
    }
});