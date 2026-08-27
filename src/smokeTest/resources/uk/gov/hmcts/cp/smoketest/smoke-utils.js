function fn() {
  var Files = Java.type('java.nio.file.Files');
  var Paths = Java.type('java.nio.file.Paths');
  var StandardCharsets = Java.type('java.nio.charset.StandardCharsets');

  // Setup writes here at pipeline runtime - build-output-relative, gitignored, never touches the
  // source tree. Run reads the same file straight back (no PRP/PRD tier exists for PCR yet, so
  // there is no checked-in fallback config - design doc §5).
  var CASE_CONFIG_DIR = Paths.get('build', 'smoke-test-config');

  function todayIso() {
    var d = new Date();
    var yyyy = d.getFullYear();
    var mm = ('0' + (d.getMonth() + 1)).slice(-2);
    var dd = ('0' + d.getDate()).slice(-2);
    return yyyy + '-' + mm + '-' + dd;
  }

  return {
    today: function() {
      return todayIso();
    },
    generateUrn: function() {
      var unit = Math.floor(Math.random() * (99 - 10 + 1)) + 10;
      var number = Math.floor(Math.random() * (99999 - 10000 + 1)) + 10000;
      var yy = ('' + new Date().getFullYear()).slice(-2);
      return unit + 'GD' + number + yy;
    },
    hearingDatePlusDays: function(days) {
      var d = new Date();
      d.setDate(d.getDate() + days);
      var yyyy = d.getFullYear();
      var mm = ('0' + (d.getMonth() + 1)).slice(-2);
      var dd = ('0' + d.getDate()).slice(-2);
      return yyyy + '-' + mm + '-' + dd;
    },
    writeCaseConfig: function(environment, caseUrn, hearingId, defendantId) {
      Files.createDirectories(CASE_CONFIG_DIR);
      var config = {
        environment: environment,
        caseUrn: caseUrn,
        hearingId: hearingId,
        defendantId: defendantId,
        source: 'automated-setup',
        lastConfirmedDate: todayIso(),
        notes: ''
      };
      var path = CASE_CONFIG_DIR.resolve(environment + '.json');
      Files.write(path, JSON.stringify(config, null, 2).getBytes(StandardCharsets.UTF_8));
    },
    readCaseConfig: function(environment) {
      var path = CASE_CONFIG_DIR.resolve(environment + '.json');
      var text = new java.lang.String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      return JSON.parse(text);
    }
  };
}
