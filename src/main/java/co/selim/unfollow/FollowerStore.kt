package co.selim.unfollow

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

interface FollowerStore {
    var followers: List<String>
}

class FileBackedFollowerStore(storeFile: String) : FollowerStore {
    private val storePath = Paths.get(storeFile)

    override var followers: List<String>
        get() {
            return if (Files.exists(storePath))
                Files.readAllLines(storePath)
            else
                emptyList()
        }
        set(value) {
            Files.write(storePath, value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }

}