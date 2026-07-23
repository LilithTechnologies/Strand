plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod_version")}\""
    swaps["minecraft_version"] = "\"${node.metadata.version}\""

    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")
}