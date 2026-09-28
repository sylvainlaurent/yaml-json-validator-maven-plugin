def log = new File(basedir, 'build.log').text
// Both validation sets must use the shared mappings, also in the offline invocation.
assert log.count('Validating file ') == 4
assert log.contains('first.json') && log.contains('second.json')
// Expected failures must come from our network policy, not dependency resolution or connection failures.
assert log.contains('Maven is offline')
assert log.contains('allowRemoteSchemas is false')
