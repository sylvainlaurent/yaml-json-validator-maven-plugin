import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Keep the installed plugin and its ordinary dependencies, but start without any Graal runtime.
def repository = new File(basedir, 'repository')
repository.deleteDir()
localRepositoryPath.eachFileRecurse { file ->
    if (file.isFile()) {
        def relative = localRepositoryPath.toPath().relativize(file.toPath())
        def name = relative.toString().replace(File.separatorChar, '/' as char)
        if (!name.startsWith('org/graalvm/') && !name.startsWith('com/ibm/icu/')
                && file.name != '_remote.repositories' && !file.name.endsWith('.lastUpdated')) {
            def destination = repository.toPath().resolve(relative)
            Files.createDirectories(destination.parent)
            Files.copy(file.toPath(), destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
return true
