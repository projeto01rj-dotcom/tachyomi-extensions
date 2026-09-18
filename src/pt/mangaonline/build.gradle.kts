import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Online TV"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangaonline.tv"
    }
}