plugins {
    `java-library`
}

sourceSets {
    main {
        resources {
            srcDir(".")
            include("fonts/Monaspace Neon/MonaspaceNeon-Regular.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Bold.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Light.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Italic.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Regular.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Bold.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Light.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Italic.otf")
        }
    }
}
