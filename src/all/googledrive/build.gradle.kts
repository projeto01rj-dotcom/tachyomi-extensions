import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Google Drive"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    source {
        lang = "all"
        baseUrl = "https://drive.google.com"
    }
}

dependencies {
    implementation(project(":lib:archive"))
}
