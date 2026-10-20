package com.fossscout.app.data

/**
 * Bundled sample catalog. Replace this with a network-backed repository later
 * without changing the UI layer.
 */
val SampleProjects: List<Project> = listOf(
    Project(
        id = "blender",
        name = "Blender",
        tagline = "The free 3D creation suite for modeling, animation, and rendering.",
        description = "Blender covers the entire 3D pipeline, from modeling and rigging to " +
            "simulation, compositing, and video editing.",
        category = ProjectCategory.DESIGN,
        license = "GPL-3.0",
        website = "https://www.blender.org",
        tags = listOf("3d", "animation", "vfx"),
        stars = 14_200,
    ),
    Project(
        id = "godot",
        name = "Godot Engine",
        tagline = "A feature-packed 2D and 3D game engine built by its community.",
        description = "Godot is a cross-platform game engine with a unified editor, " +
            "a friendly scripting language, and no royalties or strings attached.",
        category = ProjectCategory.DEVELOPER_TOOLS,
        license = "MIT",
        website = "https://godotengine.org",
        tags = listOf("game-dev", "engine", "2d"),
        stars = 95_000,
    ),
    Project(
        id = "krita",
        name = "Krita",
        tagline = "A professional digital painting studio for illustrators and concept artists.",
        description = "Krita ships with a huge brush engine, animation tools, and a " +
            "non-destructive workflow designed for artists.",
        category = ProjectCategory.DESIGN,
        license = "GPL-3.0",
        website = "https://krita.org",
        tags = listOf("painting", "illustration", "art"),
        stars = 13_500,
    ),
    Project(
        id = "home-assistant",
        name = "Home Assistant",
        tagline = "Local-first home automation that puts you in control.",
        description = "Home Assistant connects thousands of smart home devices while " +
            "keeping your data on your own hardware.",
        category = ProjectCategory.PRODUCTIVITY,
        license = "Apache-2.0",
        website = "https://www.home-assistant.io",
        tags = listOf("smart-home", "automation", "local-first"),
        stars = 76_000,
    ),
    Project(
        id = "signal",
        name = "Signal",
        tagline = "Private messaging with end-to-end encryption by default.",
        description = "Signal is a nonprofit messaging app built around privacy, " +
            "with open source clients and protocol.",
        category = ProjectCategory.PRIVACY,
        license = "AGPL-3.0",
        website = "https://signal.org",
        tags = listOf("messaging", "encryption", "privacy"),
        stars = 26_000,
    ),
    Project(
        id = "vlc",
        name = "VLC media player",
        tagline = "Plays almost every media format, on almost every platform.",
        description = "VLC is a portable, cross-platform media player and streaming " +
            "server maintained by the VideoLAN nonprofit.",
        category = ProjectCategory.MEDIA,
        license = "GPL-2.0",
        website = "https://www.videolan.org/vlc/",
        tags = listOf("video", "player", "codecs"),
        stars = 13_000,
    ),
    Project(
        id = "joplin",
        name = "Joplin",
        tagline = "Encrypted note-taking and to-dos that sync on your terms.",
        description = "Joplin stores notes as Markdown, supports end-to-end encryption, " +
            "and can sync through providers you choose.",
        category = ProjectCategory.PRODUCTIVITY,
        license = "AGPL-3.0",
        website = "https://joplinapp.org",
        tags = listOf("notes", "markdown", "sync"),
        stars = 48_000,
    ),
    Project(
        id = "organic-maps",
        name = "Organic Maps",
        tagline = "Offline maps and navigation powered by OpenStreetMap.",
        description = "Organic Maps is a privacy-focused maps app with offline routing, " +
            "no ads, and no tracking.",
        category = ProjectCategory.PRODUCTIVITY,
        license = "Apache-2.0",
        website = "https://organicmaps.app",
        tags = listOf("maps", "offline", "navigation"),
        stars = 13_000,
    ),
    Project(
        id = "kde-connect",
        name = "KDE Connect",
        tagline = "Connect your phone and desktop without sending data to the cloud.",
        description = "KDE Connect links devices on your local network for notifications, " +
            "file transfers, clipboard sync, and remote input.",
        category = ProjectCategory.PRODUCTIVITY,
        license = "GPL-2.0",
        website = "https://kdeconnect.kde.org",
        tags = listOf("devices", "clipboard", "notifications"),
        stars = 4_000,
    ),
    Project(
        id = "kiwix",
        name = "Kiwix",
        tagline = "Carry Wikipedia and other knowledge offline, anywhere.",
        description = "Kiwix lets you read offline copies of websites and educational " +
            "content, even without an internet connection.",
        category = ProjectCategory.EDUCATION,
        license = "GPL-3.0",
        website = "https://www.kiwix.org",
        tags = listOf("offline", "wikipedia", "knowledge"),
        stars = 1_200,
    ),
    Project(
        id = "anki",
        name = "Anki",
        tagline = "Spaced-repetition flashcards that make remembering easier.",
        description = "Anki schedules reviews at the right moment so you can learn " +
            "languages, medicine, or anything else efficiently.",
        category = ProjectCategory.EDUCATION,
        license = "AGPL-3.0",
        website = "https://apps.ankiweb.net",
        tags = listOf("flashcards", "spaced-repetition", "memory"),
        stars = 23_000,
    ),
    Project(
        id = "f-droid",
        name = "F-Droid",
        tagline = "An app store for free and open source Android software.",
        description = "F-Droid is a catalog of reproducible, FOSS Android apps with " +
            "its own client and a community-run build pipeline.",
        category = ProjectCategory.DEVELOPER_TOOLS,
        license = "GPL-3.0",
        website = "https://f-droid.org",
        tags = listOf("android", "apps", "foss"),
        stars = 10_000,
    ),
)
