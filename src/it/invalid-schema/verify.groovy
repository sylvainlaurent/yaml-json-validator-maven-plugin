def log = new File(basedir, 'build.log').text
// The schema must be checked even though the input glob matches no files.
assert log.contains('Invalid schema [') && log.contains('schema.json')
assert log.contains('required') && log.contains('array')
assert !log.contains('Validating file ')
