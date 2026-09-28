def log = new File(basedir, 'build.log').text
// Invoker combines all three invocations in one log; the first two must succeed.
assert log.count('Validating file ') == 3
assert log.count('BUILD SUCCESS') == 2
// Each constraint must reject its deliberately invalid value.
assert log.contains('invalid.json')
assert log.contains('/any: does not match')
assert log.contains('/whitespace: does not match')
assert log.contains('/repeated: does not match')
assert log.contains('/keys/') && log.contains('string found, integer expected')
assert log.contains('Some files are not valid')
