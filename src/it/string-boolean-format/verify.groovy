def log = new File(basedir, 'build.log').text
assert log.contains('Validating file ') && log.contains('value.json')
// Issue #28: check the warning in the actual Maven output, not just the exit code.
assert !log.contains('format attribute "boolean" not supported')
assert !log.contains('Unknown format boolean')
