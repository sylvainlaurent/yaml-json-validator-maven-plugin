def log = new File(basedir, 'build.log').text
// Malformed JSON must fail schema loading even when the input glob matches no files.
assert log.contains('Could not load schema [schema.json]')
assert !log.contains('Validating file ')
