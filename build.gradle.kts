plugins {
    id("com.android.application") version "8.2.2" apply false
}

task("clean") {
    delete(rootProject.buildDir)
}
