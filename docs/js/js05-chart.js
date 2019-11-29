google.charts.load('current', {
    'packages': ['corechart']
});
google.charts.setOnLoadCallback(function () {
    var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
    var data = new google.visualization.DataTable();
    app.$set(app.$data.chart, "view", chart);
    app.$set(app.$data.chart, "data", data);
    app.$set(app.$data.chart, "options", {
        fontName: "'Computer Modern Serif',sans-serif",
        legend: "none",
        chartArea: {
            right: 25,
            top: 25,
            left: 75,
            bottom: 25,
        },
        explorer: {
            actions: ['dragToZoom', 'rightClickToReset'],
        },
        backgroundColor: {
            fill: 'transparent'
        }
    });
    app.$set(app.$data.chart, "ready", true);
    app.resetChart();
});

new ResizeSensor(document.getElementById('chart_parent'), function() {
    app.$data.chart.uCounter++;
});