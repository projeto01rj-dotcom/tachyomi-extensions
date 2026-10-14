import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MEGA compartilhamento (PT-BR)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://g.api.mega.co.nz"
    }
}
