return new File('target/it/skip-nonexisting-basedir/build.log')
        .text.contains('does not exist or is not a directory. Skipping.')