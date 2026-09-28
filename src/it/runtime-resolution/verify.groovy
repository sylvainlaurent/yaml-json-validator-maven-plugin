def log = new File(basedir, 'build.log').text
// Parsing-only validation must work without Graal; the next offline schema validation must fail.
assert log.count('BUILD SUCCESS') == 3
assert log.count('BUILD FAILURE') == 1
assert log.contains('Could not resolve GraalJS runtime') && log.contains('while Maven is offline')
assert log.count('Validating file ') == 3
// An online run through the configured mirror must populate only the selected runtime.
def feature = System.getProperty('java.specification.version').replaceFirst('^1\\.', '').toInteger()
def graal = new File(basedir, 'repository/org/graalvm')
assert new File(graal, feature < 17 ? 'js/js/21.3.19/js-21.3.19.jar' : 'js/js-language/25.0.4/js-language-25.0.4.jar').isFile()
assert !new File(graal, feature < 17 ? 'js/js-language/25.0.4' : 'js/js/21.3.19').exists()
assert !new File(graal, 'truffle/truffle-runtime').exists()
def artifact = feature < 17 ? 'js/js/21.3.19' : 'js/js-language/25.0.4'
// Maven 4 appends a repository-configuration hash to the repository ID in this cache file.
assert (new File(graal, artifact + '/_remote.repositories').text =~ /\.jar>runtime-mirror(?:-[0-9a-f]+)?=/).find()
