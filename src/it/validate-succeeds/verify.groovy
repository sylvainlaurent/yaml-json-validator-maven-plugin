return !(new File('target/it/validate-succeeds/build.log')
        .text.contains('does not exist or is not a directory. Skipping.'))