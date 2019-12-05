var app = new Vue({
  el: "#app",
  data: {
    lang: lang,
    sLang: "en",
    sAlgorithm: "",
    display: {
      "mModel": true,
      "bridgeDevice": true,
      "params": true,
      "fGens": true,
      "graphs": true,
      "errors": true
    },
    sInputs: {},
    iGains: {},
    iOffsets: {},
    sOutputs: {},
    oGains: {},
    oOffsets: {},
    params: {},
    fGens: {},
    errors: [],
    plots: {},
    chart: {
      uCounter: 0
    },
    pHeight: 500,
    defaults: {
      sInputs: "analog",
      iGains: 1,
      iOffsets: 0,
      sOutputs: "dc0",
      oGains: 1,
      oOffsets: 0
    },
    dFGen: {
      sType: 'step',
      params: {},
      iSignal: false
    },
    sim: {
      running: false,
      ts: 0.1,
      tPeriod: 5
    }
  },
  watch: {
    'chart.uCounter': function () {
      if (!!this.chart.ready) {
        this.chart.view.draw(this.chart.data, this.chart.options);
      }
    },
    'plots.allSelected': function (nVal, oVal) {
      var keys = Object.keys(this.plots.signals);
      for (var i = 0; i < keys.length; i++) {
        var key = keys[i];
        this.plots.signals[key].selected = nVal;
      }
      setTimeout(() => {
        document.getElementById('all_plots_display').indeterminate = true;
      }, 1000);
    },
    sAlgorithm: {
      deep: true,
      handler: function () {
        this.resetPlots();
        this.validateInputs();
      }
    },
    sLang: function () {},
    sim: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    oGains: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    oOffsets: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    iGains: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    iOffsets: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    params: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    },
    fGens: {
      deep: true,
      handler: function () {
        this.validateInputs();
      }
    }
  },
  beforeMount: function () {
    this.$set(this, "sAlgorithm", "sResponse");
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
      return `<embed src='https://cit.skgadi.com/m-models/${this.cLang.algorithms[this.sAlgorithm].documentation}' style='width:100%; height:500px; display: block;'/>`;
    }
  },
  methods: {
    resetColors: function () {
      var keys = Object.keys(this.plots.signals);
      for (var i=0; i<keys.length; i++) {
        var key = keys[i];
        this.$set(this.plots.signals[key],"color", getColorAt(i));
      }
    },
    updateChartData: function () {

    },
    resetChart: function () {
      if (!!this.chart.ready) {
        this.chart.data.removeColumns(0, this.chart.data.getNumberOfColumns());
        this.chart.data.addColumn('number', 'Time');
      }

      var tempPlots = this.cLang.algorithms[this.sAlgorithm].plots;
      var tempPlotKeys = Object.keys(tempPlots);
      for (var i = 0; i < tempPlotKeys.length; i++) {
        var key = tempPlotKeys[i];
        if (!!this.chart.ready) {
          this.chart.data.addColumn('number', key);
        }
      }
      this.chart.uCounter++;
    },
    resetPlots: function () {
      this.$set(this, "plots", {
        t: [],
        signals: {},
        allSelected: true
      });
      var tempPlots = this.cLang.algorithms[this.sAlgorithm].plots;
      var tempPlotKeys = Object.keys(tempPlots);
      for (var i = 0; i < tempPlotKeys.length; i++) {
        var key = tempPlotKeys[i];
        this.$set(this.plots.signals, key, {
          name: tempPlots[key],
          log: [],
          selected: true,
          color: getColorAt(i)
        });
      }
      this.resetChart();
    },
    gPaletteClick: function (idx) {
      document.getElementById('gColor_' + idx).click()
    },
    addToFGens: function (a, b) {
      this.fGens[a].splice(b, 0, JSON.parse(JSON.stringify(this.dFGen)));
      this.$nextTick(this.validateInputs);
    },
    delAFGen: function (a, b) {
      this.fGens[a].splice(b, 1);
      this.$nextTick(this.validateInputs);
    },
    clearErrors: function () {
      this.$set(this, "errors", []);
    },
    validateInputs: function () {
      this.clearErrors();
      var inpItems = document.getElementsByClassName('errValidation');
      for (var i = 0; i < inpItems.length; i++) {
        var inpItem = inpItems[i];
        var fndError = false;
        var inpValue, minValue, maxValue;
        inpValue = getNumFromText(inpItem.value);
        minValue = math.evaluate(inpItem.getAttribute("min"));
        maxValue = math.evaluate(inpItem.getAttribute("max"));
        if (isNaN(inpValue)) fndError = true;
        else if (math.abs(inpValue) === Infinity) fndError = true;
        else if ((minValue !== NaN) && (inpValue < minValue)) fndError = true;
        else if ((maxValue !== NaN) && (inpValue > maxValue)) fndError = true;
        if (fndError) {
          this.errors.push({
            mTab: inpItem.getAttribute("mTab"),
            sID: inpItem.getAttribute("sID"),
            val: inpItem.value,
            min: '$' + math.parse(minValue).toTex().replaceAll_1('Infinity', '\\infty') + "$",
            max: '$' + math.parse(maxValue).toTex().replaceAll_1('Infinity', '\\infty') + "$"
          });
          inpItem.classList.add('w3-red');
        } else {
          inpItem.classList.remove('w3-red');
        }
      }
    },
    resetInvalidsToDefault: function () {
      //sAlgorithm
      /*if (!this.sAlgorithm) {
        this.$set(this, "sAlgorithm", "sResponse");
        return;
      }*/

      //Input and outputs
      var TempKeys = Object.keys(this.cLang.algorithms[this.sAlgorithm].inputs);
      var tempItems = ['sInputs', 'iOffsets', 'iGains'];
      for (var i = 0; i < TempKeys.length; i++) {
        var key = TempKeys[i];
        for (var j = 0; j < tempItems.length; j++) {
          var dKey = tempItems[j];
          if (this[dKey][key] === undefined) {
            this.$set(this[dKey], key, this.defaults[dKey]);
            this.$nextTick(this.validateInputs);
            return;
          }
        }
      }
      TempKeys = Object.keys(this.cLang.algorithms[this.sAlgorithm].outputs);
      tempItems = ['sOutputs', 'oOffsets', 'oGains'];
      for (var i = 0; i < TempKeys.length; i++) {
        var key = TempKeys[i];
        for (var j = 0; j < tempItems.length; j++) {
          var dKey = tempItems[j];
          if (this[dKey][key] === undefined) {
            this.$set(this[dKey], key, this.defaults[dKey]);
            this.$nextTick(this.validateInputs);
            return;
          }
        }
      }

      //Params
      if (!!this.cLang.algorithms[this.sAlgorithm].params) {
        TempKeys = Object.keys(this.cLang.algorithms[this.sAlgorithm].params);
        for (var i = 0; i < TempKeys.length; i++) {
          var key = TempKeys[i];
          if (this.params[key] === undefined) {
            this.$set(this.params, key, this.cLang.algorithms[this.sAlgorithm].params[key].dValue);
            this.$nextTick(this.validateInputs);
            return;
          }
        }
      }
      //fGens
      if (!!this.cLang.algorithms[this.sAlgorithm].fGens) {
        TempKeys = Object.keys(this.cLang.algorithms[this.sAlgorithm].fGens);
        for (var i = 0; i < TempKeys.length; i++) {
          var key = TempKeys[i];
          if (this.fGens[key] === undefined || this.fGens[key].length === 0) {
            this.$set(this.fGens, key, [Object.assign({}, this.dFGen)]);
            this.$nextTick(this.validateInputs);
            return;
          }
          for (var ii = 0; ii < this.fGens[key].length; ii++) {
            var tempSType = this.fGens[key][ii].sType;
            for (var j = 0; j < this.cLang.fGens.sTypes[tempSType].params.length; j++) {
              var tempParam = this.cLang.fGens.sTypes[tempSType].params[j];
              if (this.fGens[key][ii].params[tempParam] === undefined) {
                this.$set(this.fGens[key][ii].params, tempParam, Object.assign({}, this.cLang.fGens.params[tempParam]));
                this.$nextTick(this.validateInputs);
                return;
              }
            }
            var tempPresParams = Object.keys(this.fGens[key][ii].params);
            for (var j = 0; j < tempPresParams.length; j++) {
              if (this.cLang.fGens.sTypes[tempSType].params.indexOf(tempPresParams[j]) < 0) {
                this.$delete(this.fGens[key][ii].params, tempPresParams[j]);
                this.$nextTick(this.validateInputs);
                return;
              }
            }

          }
        }
      }
    }
  }
});