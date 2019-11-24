String.prototype.replaceAll_1 = function(search, replacement) {
    var target = this;
    return target.replace(new RegExp(search, 'g'), replacement);
};

String.prototype.replaceAll_2 = function(search, replacement) {
    var target = this;
    return target.split(search).join(replacement);
};


function getNumFromText(a) {
    var b;
    try {
        b = math.evaluate(a);
        return math.isNumeric(b) ? b : NaN;
    } catch (e) {
        return NaN
    }
}