def log = new File(basedir, 'build.log').text
// Invoker combines all three invocations in one log; the first two must succeed.
// Both successful builds run two executions with separate contexts in the same plugin realm.
assert log.count('Validating file ') == 5
assert log.count('BUILD SUCCESS') == 2
assert !log.contains('Unable to load the TruffleAttach library')
assert !log.contains('already loaded in another classloader')
// Each constraint must reject its deliberately invalid value.
assert log.contains('invalid.json')
assert log.contains('/any: does not match')
assert log.contains('/whitespace: does not match')
assert log.contains('/repeated: does not match')
assert log.contains('/keys/') && log.contains('string found, integer expected')
assert log.contains('Some files are not valid')
